package org.gbif.checklistbank.neo;

import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.api.model.checklistbank.VerbatimNameUsage;
import org.gbif.api.vocabulary.Extension;
import org.gbif.api.vocabulary.NameType;
import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.api.vocabulary.NomenclaturalStatus;
import org.gbif.api.vocabulary.Origin;
import org.gbif.api.vocabulary.Rank;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.checklistbank.cli.common.Metrics;
import org.gbif.checklistbank.cli.normalizer.ExtensionInterpreter;
import org.gbif.checklistbank.cli.normalizer.IgnoreNameUsageException;
import org.gbif.checklistbank.cli.normalizer.InsertMetadata;
import org.gbif.checklistbank.cli.normalizer.NormalizationFailedException;
import org.gbif.checklistbank.model.UsageExtensions;
import org.gbif.common.parsers.NomStatusParser;
import org.gbif.common.parsers.RankParser;
import org.gbif.common.parsers.TaxStatusParser;
import org.gbif.common.parsers.UrlParser;
import org.gbif.common.parsers.core.EnumParser;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.terms.AcTerm;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.TermFactory;
import org.gbif.dwca.io.Archive;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.dwca.record.Record;
import org.gbif.dwca.record.StarRecord;
import org.gbif.nameparser.NameParser;
import org.gbif.nameparser.UnparsableException;
import org.gbif.utils.ObjectUtils;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.yammer.metrics.Meter;
import com.yammer.metrics.MetricRegistry;
import org.apache.commons.io.FileUtils;
import org.neo4j.kernel.api.index.PreexistingIndexEntryConflictException;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NeoInserter implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);
    private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");
    private static final TermFactory TF = TermFactory.instance();

    private Archive arch;
    private Map<String, UUID> constituents;
    private NameParser nameParser = new NameParser();
    private RankParser rankParser = RankParser.getInstance();
    private EnumParser<NomenclaturalStatus> nomStatusParser = NomStatusParser.getInstance();
    private EnumParser<TaxonomicStatus> taxStatusParser = TaxStatusParser.getInstance();
    private InsertMetadata meta = new InsertMetadata();
    private ExtensionInterpreter extensionInterpreter = new ExtensionInterpreter();
    private final BatchInserter inserter;
    private final int batchSize;
    private final Meter insertMeter;
    private final Map<Term, Extension> extensions;
    private final UsageDao dao;

    private NeoInserter(UsageDao dao, File storeDir, int batchSize, @Nullable Meter insertMeter) {
        Preconditions.checkNotNull(dao, "DAO required");
        LOG.info("Creating new neo db at {}", storeDir.getAbsolutePath());
        this.dao = dao;
        initNeoDir(storeDir);
        inserter = BatchInserters.inserter(storeDir.getAbsolutePath());
        this.batchSize = batchSize;
        this.insertMeter = insertMeter;
        extensions = Maps.newHashMap();
        for (Extension e : Extension.values()) {
            extensions.put(TF.findTerm(e.getRowType()), e);
        }
    }

    public static NeoInserter create(UsageDao dao, File storeDir, int batchSize, @Nullable MetricRegistry registry) {
        return new NeoInserter(dao, storeDir, batchSize, registry == null ? null : registry.meter(Metrics.INSERT_METER));
    }

    public InsertMetadata insert(File dwca, Map<String, UUID> constituents) throws NormalizationFailedException {
        this.constituents = constituents;
        openArchive(dwca);
        for (StarRecord star : arch) {
            insertStarRecord(star);
        }
        LOG.info("Data insert completed, {} nodes created", meta.getRecords());
        if (insertMeter != null) {
            LOG.info("Insert rate: {}", insertMeter.getMeanRate());
        }
        return meta;
    }

    @VisibleForTesting
    protected void insertStarRecord(StarRecord star) {

        try {
            VerbatimNameUsage v = new VerbatimNameUsage();

            // set core props
            Record core = star.core();
            for (Term t : core.terms()) {
                String val = clean(core.value(t));
                if (val != null) {
                    v.setCoreField(t, val);
                }
            }
            // make sure this is last to override already put taxonID keys
            v.setCoreField(DwcTerm.taxonID, taxonID(core));
            // readUsage extensions data
            for (Map.Entry<Term, Extension> ext : extensions.entrySet()) {
                if (star.hasExtension(ext.getKey())) {
                    v.getExtensions().put(ext.getValue(), Lists.<Map<Term, String>>newArrayList());
                    for (Record eRec : star.extension(ext.getKey())) {
                        Map<Term, String> data = Maps.newHashMap();
                        for (Term t : eRec.terms()) {
                            String val = clean(eRec.value(t));
                            if (val != null) {
                                data.put(t, val);
                            }
                        }
                        v.getExtensions().get(ext.getValue()).add(data);
                    }
                }
            }
            // convert into a NameUsage interpreting all enums and other needed types
            NameUsage u = buildUsage(v);
            UsageExtensions ext = extensionInterpreter.interpret(u, v);

            // and batch insert key neo properties used during normalization
            Map<String, Object> props = dao.neoProperties(core.id(), u, v);
            long nodeId = inserter.createNode(props, Labels.TAXON, u.isSynonym() ? Labels.SYNONYM : Labels.TAXON);
            // store verbatim instance
            dao.store(nodeId, v);
            dao.store(nodeId, u, false);
            dao.store(nodeId, ext);


            meta.incRecords();
            meta.incRank(u.getRank());
            if (insertMeter != null) {
                insertMeter.mark();
            }
            if (meta.getRecords() % (batchSize * 10) == 0) {
                LOG.info("Inserts done into neo4j: {}", meta.getRecords());
            }

        } catch (IgnoreNameUsageException e) {
            meta.incIgnored();
            LOG.info("Ignoring record {}: {}", star.core().id(), e.getMessage());
        }
    }

    private void openArchive(File dwca) throws NormalizationFailedException {
        meta = new InsertMetadata();
        try {
            LOG.info("Reading dwc archive from {}", dwca);
            arch = ArchiveFactory.openArchive(dwca);
            if (!arch.getCore().hasTerm(DwcTerm.taxonID)) {
                LOG.warn("Using core ID for taxonID");
                meta.setCoreIdUsed(true);
            }
            // multi values in use for acceptedID?
            for (Term t : arch.getCore().getTerms()) {
                String delim = arch.getCore().getField(t).getDelimitedBy();
                if (!Strings.isNullOrEmpty(delim)) {
                    meta.getMultiValueDelimiters().put(t, Splitter.on(delim).omitEmptyStrings());
                }
            }
            for (Term t : DwcTerm.HIGHER_RANKS) {
                if (arch.getCore().hasTerm(t)) {
                    meta.setDenormedClassificationMapped(true);
                    break;
                }
            }
            if (arch.getCore().hasTerm(DwcTerm.parentNameUsageID) || arch.getCore().hasTerm(DwcTerm.parentNameUsage)) {
                meta.setParentNameMapped(true);
            }
            if (arch.getCore().hasTerm(DwcTerm.acceptedNameUsageID) || arch.getCore().hasTerm(DwcTerm.acceptedNameUsage)) {
                meta.setAcceptedNameMapped(true);
            }
            if (arch.getCore().hasTerm(DwcTerm.originalNameUsageID) || arch.getCore().hasTerm(DwcTerm.originalNameUsage)) {
                meta.setOriginalNameMapped(true);
            }
        } catch (IOException e) {
            throw new NormalizationFailedException("IOException opening archive " + dwca.getAbsolutePath(), e);
        }
    }

    private void initNeoDir(File storeDir) {
        try {
            if (storeDir.exists()) {
                FileUtils.forceDelete(storeDir);
            }
            FileUtils.forceMkdir(storeDir);

        } catch (IOException e) {
            throw new NormalizationFailedException("Cannot prepare neo db directory " + storeDir.getAbsolutePath(), e);
        }
    }

    private NameUsage buildUsage(VerbatimNameUsage v) throws IgnoreNameUsageException {
        NameUsage u = new NameUsage();
        u.setTaxonID(v.getCoreField(DwcTerm.taxonID));
        u.setOrigin(Origin.SOURCE);
        if (constituents != null && v.hasCoreField(DwcTerm.datasetID)) {
            UUID cKey = constituents.get(v.getCoreField(DwcTerm.datasetID));
            u.setConstituentKey(cKey);
        }

        // classification
        //TODO: interpret classification string if others are not given
        // DwcTerm.higherClassification;
        u.setKingdom(v.getCoreField(DwcTerm.kingdom));
        u.setPhylum(v.getCoreField(DwcTerm.phylum));
        u.setClazz(v.getCoreField(DwcTerm.class_));
        u.setOrder(v.getCoreField(DwcTerm.order));
        u.setFamily(v.getCoreField(DwcTerm.family));
        u.setGenus(v.getCoreField(DwcTerm.genus));
        u.setSubgenus(v.getCoreField(DwcTerm.subgenus));

        // rank
        String vRank = firstClean(v, DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank);
        if (!Strings.isNullOrEmpty(vRank)) {
            ParseResult<Rank> rankParse = rankParser.parse(vRank);
            if (rankParse.isSuccessful()) {
                u.setRank(rankParse.getPayload());
            } else {
                u.addIssue(NameUsageIssue.RANK_INVALID);
            }
        }
        final Rank rank = u.getRank();

        // build best name
        ParsedName pn = setScientificName(u, v, rank);

        // tax status
        String tstatus = v.getCoreField(DwcTerm.taxonomicStatus);
        if (!Strings.isNullOrEmpty(tstatus)) {
            ParseResult<TaxonomicStatus> taxParse = taxStatusParser.parse(tstatus);
            if (taxParse.isSuccessful()) {
                u.setTaxonomicStatus(taxParse.getPayload());
                u.setSynonym(u.getTaxonomicStatus().isSynonym());
            } else {
                u.addIssue(NameUsageIssue.TAXONOMIC_STATUS_INVALID);
            }
        }

        // nom status
        String nstatus = v.getCoreField(DwcTerm.nomenclaturalStatus);
        if (!Strings.isNullOrEmpty(nstatus)) {
            ParseResult<NomenclaturalStatus> nsParse = nomStatusParser.parse(nstatus);
            if (nsParse.isSuccessful()) {
                u.getNomenclaturalStatus().add(nsParse.getPayload());
            } else {
                u.addIssue(NameUsageIssue.NOMENCLATURAL_STATUS_INVALID);
            }
        }

        if (!Strings.isNullOrEmpty(pn.getNomStatus())) {
            ParseResult<NomenclaturalStatus> nsParse = nomStatusParser.parse(pn.getNomStatus());
            if (nsParse.isSuccessful()) {
                u.getNomenclaturalStatus().add(nsParse.getPayload());
            }
        }

        // other properties
        u.setPublishedIn(v.getCoreField(DwcTerm.namePublishedIn));
        u.setAccordingTo(v.getCoreField(DwcTerm.nameAccordingTo));
        u.setRemarks(v.getCoreField(DwcTerm.taxonRemarks));
        u.setAuthorship(v.getCoreField(DwcTerm.scientificNameAuthorship));

        u.setReferences(ObjectUtils.coalesce(
                UrlParser.parse(v.getCoreField(DcTerm.references)),
                UrlParser.parse(v.getCoreField(AcTerm.furtherInformationURL)),
                UrlParser.parse(v.getCoreField(DcTerm.source))
        ));

        return u;
    }

    @VisibleForTesting
    protected ParsedName setScientificName(NameUsage u, VerbatimNameUsage v, Rank rank) throws IgnoreNameUsageException {
        ParsedName pn = new ParsedName();
        final String sciname = clean(v.getCoreField(DwcTerm.scientificName));
        try {
            if (sciname != null) {
                pn = nameParser.parse(sciname, rank);
                // append author if its not part of the name yet
                String author = v.getCoreField(DwcTerm.scientificNameAuthorship);
                if (!Strings.isNullOrEmpty(author) && !sciname.contains(author)
                        && (!pn.isAuthorsParsed() || Strings.isNullOrEmpty(pn.getAuthorship()))) {
                    u.addIssue(NameUsageIssue.SCIENTIFIC_NAME_ASSEMBLED);
                    pn.setAuthorship(buildAuthorship(v));
                }
            } else {
                String genus = firstClean(v, GbifTerm.genericName, DwcTerm.genus);
                if (genus == null) {
                    // bad atomized name, we can't assemble anything. Ignore this record completely!!!
                    throw new IgnoreNameUsageException("No name found");

                } else {
                    pn.setGenusOrAbove(genus);
                    pn.setSpecificEpithet(v.getCoreField(DwcTerm.specificEpithet));
                    pn.setInfraSpecificEpithet(v.getCoreField(DwcTerm.infraspecificEpithet));
                    pn.setAuthorship(buildAuthorship(v));
                    pn.setRank(rank);
                    pn.setType(NameType.SCIENTIFIC);
                    u.addIssue(NameUsageIssue.SCIENTIFIC_NAME_ASSEMBLED);
                }
            }
        } catch (UnparsableException e) {
            LOG.debug("Unparsable {} name {}", e.type, e.name);
            pn = new ParsedName();
            pn.setType(e.type);
            pn.setScientificName(sciname);
        }

        if (u.getIssues().contains(NameUsageIssue.SCIENTIFIC_NAME_ASSEMBLED)) {
            u.setScientificName(pn.fullName());
        } else {
            u.setScientificName(sciname);
        }
        u.setCanonicalName(Strings.emptyToNull(pn.canonicalName()));
        //TODO: verify name parts and rank
        u.setNameType(pn.getType());
        return pn;
    }

    private static String buildAuthorship(VerbatimNameUsage v) {
        StringBuilder sb = new StringBuilder();
        if (v.hasCoreField(DwcTerm.scientificNameAuthorship)) {
            sb.append(v.getCoreField(DwcTerm.scientificNameAuthorship));
        }
        if (v.hasCoreField(DwcTerm.namePublishedInYear) && !sb.toString().contains(v.getCoreField(DwcTerm.namePublishedInYear))) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v.getCoreField(DwcTerm.namePublishedInYear));
        }
        return sb.toString();
    }

    private static String firstClean(VerbatimNameUsage v, Term... terms) {
        for (Term t : terms) {
            String x = clean(v.getCoreField(t));
            if (x != null) {
                return x;
            }
        }
        return null;
    }

    private String taxonID(Record core) {
        if (meta.isCoreIdUsed()) {
            return clean(core.id());
        } else {
            return clean(core.value(DwcTerm.taxonID));
        }
    }

    public static String clean(String x) {
        if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
            return null;
        }
        return Strings.emptyToNull(x.trim());
    }

    @Override
    public void close() throws NotUniqueRuntimeException {
        try {
            try {
                // define indices
                LOG.info("Building lucene index taxonID ...");
                //TODO: neo4j batchinserter does not seem to evaluate the unique constraint. Duplicates pass thru (see tests) !!!
                inserter.createDeferredConstraint(Labels.TAXON).assertPropertyIsUnique(NeoProperties.TAXON_ID).create();
                LOG.info("Building lucene index scientific name ...");
                inserter.createDeferredSchemaIndex(Labels.TAXON).on(NeoProperties.SCIENTIFIC_NAME).create();
                LOG.info("Building lucene index canonical name ...");
                inserter.createDeferredSchemaIndex(Labels.TAXON).on(NeoProperties.CANONICAL_NAME).create();
            } finally {
                // this is when lucene indices are build and thus throws RuntimeExceptions when unique constraints are broken
                // we catch these exceptions below
                inserter.shutdown();
            }
        } catch (RuntimeException e) {
            Throwable t = e.getCause();
            // check if the cause was a broken unique constraint which can only be taxonID in our case
            if (t != null && t instanceof PreexistingIndexEntryConflictException) {
                PreexistingIndexEntryConflictException pe = (PreexistingIndexEntryConflictException) t;
                LOG.error("TaxonID not unique. Value {} used for both node {} and {}", pe.getPropertyValue(), pe.getExistingNodeId(), pe.getAddedNodeId());
                throw new NotUniqueRuntimeException("TaxonID", pe.getPropertyValue());
            } else {
                throw e;
            }
        }
        LOG.info("Neo batch inserter closed, data flushed to disk. Opening regular neo db again ...", meta.getRecords());
        dao.openNeo();
    }

}
