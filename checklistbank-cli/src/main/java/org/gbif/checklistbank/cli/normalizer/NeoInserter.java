package org.gbif.checklistbank.cli.normalizer;

import org.gbif.api.model.checklistbank.NameUsageContainer;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.api.model.checklistbank.VerbatimNameUsage;
import org.gbif.api.vocabulary.Extension;
import org.gbif.api.vocabulary.NameType;
import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.api.vocabulary.NomenclaturalStatus;
import org.gbif.api.vocabulary.Origin;
import org.gbif.api.vocabulary.Rank;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NeoMapper;
import org.gbif.checklistbank.neo.TaxonProperties;
import org.gbif.common.parsers.NomStatusParser;
import org.gbif.common.parsers.RankParser;
import org.gbif.common.parsers.TaxStatusParser;
import org.gbif.common.parsers.core.EnumParser;
import org.gbif.common.parsers.core.ParseResult;
import org.gbif.dwc.record.Record;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.text.Archive;
import org.gbif.dwc.text.ArchiveFactory;
import org.gbif.dwc.text.StarRecord;
import org.gbif.nameparser.NameParser;
import org.gbif.nameparser.UnparsableException;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.beust.jcommander.internal.Lists;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.yammer.metrics.Meter;
import org.apache.commons.io.FileUtils;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.index.lucene.unsafe.batchinsert.LuceneBatchInserterIndexProvider;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserterIndex;
import org.neo4j.unsafe.batchinsert.BatchInserterIndexProvider;
import org.neo4j.unsafe.batchinsert.BatchInserters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(NeoInserter.class);
  private static final Pattern NULL_PATTERN = Pattern.compile("^\\s*(\\\\N|\\\\?NULL)\\s*$");

  private Archive arch;
  private Map<String, UUID> constituents;
  private NeoMapper mapper = NeoMapper.instance();
  private NameParser nameParser = new NameParser();
  private RankParser rankParser = RankParser.getInstance();
  private EnumParser<NomenclaturalStatus> nomStatusParser = NomStatusParser.getInstance();
  private EnumParser<TaxonomicStatus> taxStatusParser = TaxStatusParser.getInstance();
  private InsertMetadata meta;
  private ExtensionInterpreter extensionInterpreter = new ExtensionInterpreter();

  public InsertMetadata insert(File storeDir, File dwca, int batchSize, Meter insertMeter,
                               Map<String, UUID> constituents) throws NormalizationFailedException {
    this.constituents = constituents;

    openArchive(dwca);

    LOG.info("Creating new neo db at {}", storeDir.getAbsolutePath());
    initNeoDir(storeDir);
    final BatchInserter inserter = BatchInserters.inserter(storeDir.getAbsolutePath());

    final BatchInserterIndexProvider indexProvider = new LuceneBatchInserterIndexProvider(inserter);
    final BatchInserterIndex taxonIdx =
      indexProvider.nodeIndex(TaxonProperties.TAXON_ID, MapUtil.stringMap("type", "exact"));
    taxonIdx.setCacheCapacity(TaxonProperties.TAXON_ID, 10000);
    final BatchInserterIndex sciNameIdx =
      indexProvider.nodeIndex(TaxonProperties.SCIENTIFIC_NAME, MapUtil.stringMap("type", "exact"));
    sciNameIdx.setCacheCapacity(TaxonProperties.SCIENTIFIC_NAME, 10000);
    final BatchInserterIndex canonNameIdx =
      indexProvider.nodeIndex(TaxonProperties.CANONICAL_NAME, MapUtil.stringMap("type", "exact"));
    canonNameIdx.setCacheCapacity(TaxonProperties.CANONICAL_NAME, 10000);

    final long startSort = System.currentTimeMillis();
    LOG.debug("Sorted archive in {} seconds", (System.currentTimeMillis() - startSort) / 1000);

    for (StarRecord star : arch) {
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
        // read extensions data
        for (Extension ext : Extension.values()) {
          if (star.hasExtension(ext.getRowType())) {
            v.getExtensions().put(ext, Lists.<Map<Term, String>>newArrayList());
            for (Record eRec : star.extension(ext.getRowType())) {
              Map<Term, String> data = Maps.newHashMap();
              for (Term t : eRec.terms()) {
                String val = clean(eRec.value(t));
                if (val != null) {
                  data.put(t, val);
                }
              }
              v.getExtensions().get(ext).add(data);
            }
          }
        }
        // convert into a NameUsage interpreting all enums and other needed types
        NameUsageContainer u = buildUsage(v);

        // ... and into neo
        Map<String, Object> props = mapper.propertyMap(core.id(), u, v);
        // add more neo properties to be used in resolving the relations later:
        putProp(props, DwcTerm.parentNameUsageID, v);
        putProp(props, DwcTerm.parentNameUsage, v);
        putProp(props, DwcTerm.acceptedNameUsageID, v);
        putProp(props, DwcTerm.acceptedNameUsage, v);
        putProp(props, DwcTerm.originalNameUsageID, v);
        putProp(props, DwcTerm.originalNameUsage, v);

        long node = inserter.createNode(props, Labels.TAXON);
        taxonIdx.add(node, props);

        meta.incRecords();
        meta.incRank(u.getRank());
        insertMeter.mark();
        if (meta.getRecords() % (batchSize * 10) == 0) {
          LOG.info("Inserts done into neo4j: {}", meta.getRecords());
        }

      } catch (IgnoreNameUsageException e) {
        meta.incIgnored();
        LOG.info("Ignoring record {}: {}", star.core().id(), e.getMessage());
      }
    }
    LOG.info("Data insert completed, {} nodes created", meta.getRecords());
    LOG.info("Insert rate: {}", insertMeter.getMeanRate());

    indexProvider.shutdown();
    inserter.shutdown();
    LOG.info("Neo shutdown, data flushed to disk", meta.getRecords());

    return meta;
  }

  private void openArchive(File dwca) throws NormalizationFailedException {
    meta = new InsertMetadata();
    try {
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

  private void putProp(Map<String, Object> props, Term t, VerbatimNameUsage v) {
    String val = clean(v.getCoreField(t));
    if (val != null) {
      props.put(NeoMapper.propertyName(t), val);
    }
  }

  private NameUsageContainer buildUsage(VerbatimNameUsage v) throws IgnoreNameUsageException {
    NameUsageContainer u = new NameUsageContainer();
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
    ParsedName pn = setScientificName(u,v,rank);

    // tax status
    String tstatus = v.getCoreField(DwcTerm.taxonomicStatus);
    if (!Strings.isNullOrEmpty(tstatus)) {
      ParseResult<TaxonomicStatus> taxParse = taxStatusParser.parse(tstatus);
      if (taxParse.isSuccessful()) {
        u.setTaxonomicStatus(taxParse.getPayload());
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
      ParseResult<NomenclaturalStatus>  nsParse = nomStatusParser.parse(pn.getNomStatus());
      if (nsParse.isSuccessful()) {
        u.getNomenclaturalStatus().add(nsParse.getPayload());
      }
    }

    // INTERPRET EXTENSIONS
    extensionInterpreter.interpret(u, v);

    return u;
  }

  @VisibleForTesting
  protected ParsedName setScientificName(NameUsageContainer u, VerbatimNameUsage v, Rank rank) throws IgnoreNameUsageException {
    ParsedName pn = new ParsedName();
    final String sciname = clean(v.getCoreField(DwcTerm.scientificName));
    try {
      if (sciname != null) {
        pn = nameParser.parse(sciname);
        // append author if its not part of the name yet
        String author = v.getCoreField(DwcTerm.scientificNameAuthorship);
        if (!Strings.isNullOrEmpty(author) && !sciname.contains(author)
                && (!pn.isAuthorsParsed() || Strings.isNullOrEmpty(pn.getAuthorship()))) {
          u.addIssue(NameUsageIssue.SCIENTIFIC_NAME_ASSEMBLED);
          pn.setAuthorship(author);
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
          pn.setAuthorship(v.getCoreField(DwcTerm.scientificNameAuthorship));
          pn.setRank(rank);
          pn.setType(NameType.WELLFORMED);
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

  private static String firstClean(VerbatimNameUsage v, Term ... terms) {
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

  static String clean(String x) {
    if (Strings.isNullOrEmpty(x) || NULL_PATTERN.matcher(x).find()) {
      return null;
    }
    return Strings.emptyToNull(x.trim());
  }

}
