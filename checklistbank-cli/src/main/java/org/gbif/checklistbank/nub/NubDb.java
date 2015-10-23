package org.gbif.checklistbank.nub;

import org.gbif.api.vocabulary.Kingdom;
import org.gbif.api.vocabulary.NameUsageIssue;
import org.gbif.api.vocabulary.Origin;
import org.gbif.api.vocabulary.Rank;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.checklistbank.authorship.AuthorComparator;
import org.gbif.checklistbank.model.Equality;
import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NeoProperties;
import org.gbif.checklistbank.neo.RelType;
import org.gbif.checklistbank.neo.UsageDao;
import org.gbif.checklistbank.neo.traverse.Traversals;
import org.gbif.checklistbank.nub.model.NubUsage;
import org.gbif.checklistbank.nub.model.NubUsageMatch;
import org.gbif.checklistbank.nub.model.SrcUsage;
import org.gbif.common.parsers.KingdomParser;
import org.gbif.common.parsers.core.ParseResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import com.beust.jcommander.internal.Maps;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.IteratorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around the dao that etends the dao with nub build specific common operations.
 */
public class NubDb {

    private static final Logger LOG = LoggerFactory.getLogger(NubDb.class);
    private final AuthorComparator authComp = AuthorComparator.createWithAuthormap();
    protected final UsageDao dao;
    private final KingdomParser kingdomParser = KingdomParser.getInstance();
    private final Map<Kingdom, NubUsage> kingdoms = Maps.newHashMap();

    private NubDb(UsageDao dao, boolean initialize) {
        this.dao = dao;
        // create indices?
        if (initialize) {
            try (Transaction tx = dao.beginTx()) {
                Schema schema = dao.getNeo().schema();
                schema.indexFor(Labels.TAXON).on(NeoProperties.CANONICAL_NAME).create();
                tx.success();
            }
        }
    }

    public static NubDb create(UsageDao dao) {
        return new NubDb(dao, true);
    }

    /**
     * Opens an existing db without creating a new db schema.
     */
    public static NubDb open(UsageDao dao) {
        return new NubDb(dao, false);
    }

    public Transaction beginTx() {
        return dao.beginTx();
    }

    public List<NubUsage> findNubUsages(String canonical) {
        List<NubUsage> usages = Lists.newArrayList();
        for (Node n : IteratorUtil.loop(dao.getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical))) {
            usages.add(dao.readNub(n));
        }
        return usages;
    }

    /**
     * @return the parent (or accepted) nub usage for a given node. Will be null for kingdom root nodes.
     */
    public NubUsage getParent(NubUsage child) {
        return child == null ? null : dao.readNub( getParent(child.node) );
    }

    public NubUsage getKingdom(Kingdom kingdom) {
        return kingdoms.get(kingdom);
    }

    /**
     * @return the parent (or accepted) nub usage for a given node. Will be null for kingdom root nodes.
     */
    public Node getParent(Node child) {
        if (child != null) {
            Relationship rel;
            if (child.hasLabel(Labels.SYNONYM)) {
                rel = child.getSingleRelationship(RelType.SYNONYM_OF, Direction.OUTGOING);
            } else {
                rel = child.getSingleRelationship(RelType.PARENT_OF, Direction.INCOMING);
            }
            if (rel != null) {
                return rel.getOtherNode(child);
            }
        }
        return null;
    }

    /**
     * @return the neo node with the given node id or throw NotFoundException if it cannot be found!
     */
    public Node getNode(long id) throws NotFoundException {
        return dao.getNeo().getNodeById(id);
    }

    /**
     * Returns the matching accepted nub usage for that canonical name at the given rank.
     */
    public NubUsageMatch findAcceptedNubUsage(Kingdom kingdom, String canonical, Rank rank) {
        List<NubUsage> usages = Lists.newArrayList();
        for (Node n : IteratorUtil.loop(dao.getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, canonical))) {
            NubUsage rn = dao.readNub(n);
            if (kingdom == rn.kingdom && rank == rn.rank && rn.status.isAccepted()) {
                usages.add(rn);
            }
        }
        // remove doubtful ones
        if (usages.size() > 1) {
            Iterator<NubUsage> iter = usages.iterator();
            while (iter.hasNext()) {
                if (iter.next().status == TaxonomicStatus.DOUBTFUL) {
                    iter.remove();
                }
            }
        }

        if (usages.isEmpty()) {
            return NubUsageMatch.empty();
        } else if (usages.size() == 1) {
            return NubUsageMatch.match(usages.get(0));
        } else {
            LOG.error("{} homonyms encountered for {} {}", usages.size(), rank, canonical);
            throw new IllegalStateException("accepted homonym encountered for "+kingdom+" kingdom: " + rank + " " + canonical);
        }
    }

    /**
     * Tries to find an already existing nub usage for the given source usage.
     *
     * @return the existing usage or null if it does not exist yet.
     */
    public NubUsageMatch findNubUsage(UUID currSource, SrcUsage u, Kingdom uKingdom, NubUsage currNubParent) throws IgnoreSourceUsageException {
        List<NubUsage> checked = Lists.newArrayList();
        int canonMatches = 0;
        final String name = dao.canonicalOrScientificName(u.parsedName, false);
        for (Node n : IteratorUtil.loop(dao.getNeo().findNodes(Labels.TAXON, NeoProperties.CANONICAL_NAME, name))) {
            NubUsage rn = dao.readNub(n);
            if (matchesNub(u, uKingdom, rn, currNubParent)) {
                checked.add(rn);
                if (!rn.parsedName.hasAuthorship()) {
                    canonMatches++;
                }
            }
        }

        if (checked.size() == 0) {
            // try harder to match to kingdoms by name alone
            if (u.rank != null && u.rank.higherThan(Rank.PHYLUM)) {
                ParseResult<Kingdom> kResult = kingdomParser.parse(u.scientificName);
                if (kResult.isSuccessful()) {
                    return NubUsageMatch.snap(kingdoms.get(kResult.getPayload()), false);
                }
            }
            return NubUsageMatch.empty();

        } else if (checked.size() == 1) {
            return NubUsageMatch.match(checked.get(0));

        } else if (checked.size() - canonMatches == 1){
            // prefer the single match with authorship!
            for (NubUsage nu : checked) {
                if (nu.parsedName.hasAuthorship()) {
                    return NubUsageMatch.match(nu);
                }
            }

        } else {
            if (u.status.isAccepted()) {
                // avoid the case when an accepted name without author is being matched against synonym names with authors from the same source
                Set<UUID> sources = new HashSet<UUID>();
                for (NubUsage nu : checked) {
                    sources.add(nu.datasetKey);
                }
                if (sources.contains(currSource) && sources.size() == 1) {
                    LOG.debug("{} homonyms encountered for {}, but only synonyms from the same source", checked.size(), u.scientificName);
                    return NubUsageMatch.empty();
                }
            }

            // all synonyms pointing to the same accepted? then it wont matter much
            Iterator<NubUsage> iter = checked.iterator();
            Node parent = getParent(iter.next().node);
            if (parent != null) {
                while (iter.hasNext()) {
                    Node p2 = getParent(iter.next().node);
                    if (!parent.equals(p2)) {
                        parent = null;
                        break;
                    }
                }
                if (parent != null) {
                    return NubUsageMatch.snap(checked.get(0), false);
                }
            }

            // finally pick the first accepted randomly
            // TODO: is this is a good idea???
            for (NubUsage nu : checked) {
                if (nu.status.isAccepted()) {
                    nu.issues.add(NameUsageIssue.HOMONYM);
                    nu.addRemark("Homonym known in other sources: " + u.scientificName);
                    LOG.warn("{} ambigous homonyms encountered for {} in source {}", checked.size(), u.scientificName, currSource);
                    return NubUsageMatch.snap(nu, true);
                }
            }
            throw new IgnoreSourceUsageException("homonym " + u.scientificName, u.scientificName);
        }
        return NubUsageMatch.empty();
    }

    private boolean matchesNub(SrcUsage u, Kingdom uKingdom, NubUsage match, NubUsage currNubParent) {
        if (u.rank != match.rank) {
            return false;
        }
        // no homonyms above genus level
        if (u.rank.isSuprageneric()) {
            return true;
        }
        Equality author = authComp.compare(u.parsedName, match.parsedName);
        Equality kingdom = compareKingdom(uKingdom, match);
        if (author == Equality.DIFFERENT || kingdom == Equality.DIFFERENT) return false;
        switch (author) {
            case EQUAL:
                // really force a no-match in case authors match but the name is classified under a different (normalised) kingdom?
                return true;
            case UNKNOWN:
                return u.rank.isSpeciesOrBelow() || compareClassification(currNubParent, match) != Equality.DIFFERENT;
        }
        return false;
    }

    // if authors are missing require the classification to not contradict!
    private Equality compareKingdom(Kingdom uKingdom, NubUsage match) {
        if (uKingdom == null || match.kingdom == null) {
            return Equality.UNKNOWN;
        }
        return norm(uKingdom) == norm(match.kingdom) ? Equality.EQUAL : Equality.DIFFERENT;
    }

    // if authors are missing require the classification to not contradict!
    private Equality compareClassification(NubUsage currNubParent, NubUsage match) {
        if (currNubParent != null && existsInClassification(match.node, currNubParent.node)) {
            return Equality.EQUAL;
        }
        return Equality.DIFFERENT;
    }

    public long countTaxa() {
        Result res = dao.getNeo().execute("start node=node(*) match node return count(node) as cnt");
        return (long) res.columnAs("cnt").next();
    }

    /**
     * distinct only 3 larger groups of kingdoms as others are often conflated.
     */
    private Kingdom norm(Kingdom k) {
        switch (k) {
            case ANIMALIA:
            case PROTOZOA:
                return Kingdom.ANIMALIA;
            case PLANTAE:
            case FUNGI:
            case CHROMISTA:
                return Kingdom.PLANTAE;
            default:
                return Kingdom.INCERTAE_SEDIS;
        }
    }

    public NubUsage addUsage(NubUsage parent, SrcUsage src, Origin origin, UUID sourceDatasetKey, NameUsageIssue ... issues) {
        LOG.debug("create {} {} {} {} with parent {}", origin.name().toLowerCase(), src.status.name().toLowerCase(), src.parsedName.getScientificName(), src.rank, parent == null ? "none" : parent.parsedName.getScientificName());

        NubUsage nub = new NubUsage(src);
        nub.datasetKey = sourceDatasetKey;
        nub.origin = origin;
        if (src.key != null) {
            nub.sourceIds.add(src.key);
        }
        Collections.addAll(nub.issues, issues);
        return addUsage(parent, nub);
    }

    /**
     * @param parent classification parent or accepted name in case the nub usage has a synonym status
     */
    public NubUsage addUsage(NubUsage parent, NubUsage nub) {
        Preconditions.checkNotNull(parent);
        return add(parent, nub);
    }

    public NubUsage addRoot(NubUsage nub) {
        return add(null, nub);
    }

    /**
     * @param parent classification parent or accepted name in case the nub usage has a synonym status
     */
    private NubUsage add(@Nullable NubUsage parent, NubUsage nub) {
        if (nub.node == null) {
            // create new neo node if none exists yet (should be the regular case)
            nub.node = dao.createTaxon();
        }
        if (parent == null) {
            nub.node.addLabel(Labels.ROOT);
        } else {
            nub.kingdom = parent.kingdom;
            if (nub.status != null && nub.status.isSynonym()) {
                nub.node.addLabel(Labels.SYNONYM);
                nub.node.createRelationshipTo(parent.node, RelType.SYNONYM_OF);
            } else {
                parent.node.createRelationshipTo(nub.node, RelType.PARENT_OF);
            }
        }
        // add rank specific labels so we can easily find them later
        switch (nub.rank) {
            case FAMILY:
                nub.node.addLabel(Labels.FAMILY);
                break;
            case GENUS:
                nub.node.addLabel(Labels.GENUS);
                break;
            case SPECIES:
                nub.node.addLabel(Labels.SPECIES);
                break;
            case SUBSPECIES:
            case VARIETY:
            case SUBVARIETY:
            case FORM:
            case SUBFORM:
                nub.node.addLabel(Labels.INFRASPECIES);
                break;
        }
        return store(nub);
    }

    /**
     * Creates a new parent relation from parent to child node and removes any previously existing parent relations of
     * the child node
     */
    public void updateParentRel(Node n, Node parent) {
        setSingleToRelationship(parent, n, RelType.PARENT_OF);
    }

    /**
     * Create a new relationship of given type from to nodes and make sure only a single relation of that kind exists at the "to" node.
     * If more exist delete them silently.
     */
    public void setSingleToRelationship(Node from, Node to, RelType reltype) {
        deleteRelations(to, reltype, Direction.INCOMING);
        from.createRelationshipTo(to, reltype);
    }

    public void setSingleFromRelationship(Node from, Node to, RelType reltype) {
        deleteRelations(from, reltype, Direction.OUTGOING);
        from.createRelationshipTo(to, reltype);
    }

    public void createSynonymRelation(Node synonym, Node accepted) {
        deleteRelations(synonym, RelType.PARENT_OF, Direction.INCOMING);
        deleteRelations(synonym, RelType.SYNONYM_OF, Direction.OUTGOING);
        synonym.addLabel(Labels.SYNONYM);
        synonym.removeLabel(Labels.ROOT);
        synonym.createRelationshipTo(accepted, RelType.SYNONYM_OF);
    }

    public boolean deleteRelations(Node n, RelType type, Direction direction) {
        boolean deleted = false;
        for (Relationship rel : n.getRelationships(type, direction)) {
            rel.delete();
            deleted = true;
        }
        return deleted;
    }

    /**
     * Iterates over all direct children of a node, deletes that parentOf relation and creates a new parentOf relation to the given new parent node instead.
     * @param n the node with child nodes
     * @param parent the new parent to be linked
     */
    public void assignParentToChildren(Node n, NubUsage parent, NameUsageIssue ... issues) {
        for (Relationship rel : Traversals.CHILDREN.traverse(n).relationships()) {
            Node child = rel.getOtherNode(n);
            rel.delete();
            parent.node.createRelationshipTo(child, RelType.PARENT_OF);
            // read nub usage to add an issue to the child
            NubUsage cu = dao.readNub(child);
            Collections.addAll(cu.issues, issues);
            if (!cu.parsedName.getGenusOrAbove().equals(parent.parsedName.getGenusOrAbove())) {
                cu.issues.add(NameUsageIssue.NAME_PARENT_MISMATCH);
            }
            store(cu);
        }
    }

    /**
     * Iterates over all direct synonyms of a node, deletes that synonymOf relationship and creates a new synonymOf relation to the given new accepted node instead.
     * @param n the node with synonym nodes
     * @param accepted the new accepted to be linked
     */
    public void assignAcceptedToSynonyms(Node n, Node accepted) {
        Set<Node> synonyms = Sets.newHashSet();
        for (Relationship rel : Traversals.SYNONYMS.traverse(n).relationships()) {
            Node syn = rel.getOtherNode(n);
            rel.delete();
            synonyms.add(syn);
        }
        for (Node syn : synonyms) {
            syn.createRelationshipTo(accepted, RelType.SYNONYM_OF);
        }
    }

    public NubUsage store(NubUsage nub) {
        dao.store(nub);
        if (nub.rank == Rank.KINGDOM) {
            kingdoms.put(nub.kingdom, nub);
        }
        return nub;
    }

    /**
     * @param n      the node to start the parental hierarchy search from
     * @param search the node to find in the hierarchy
     */
    public boolean existsInClassification(Node n, Node search) {
        if (n.equals(search)) {
            return true;
        }
        if (n.hasLabel(Labels.SYNONYM)) {
            n = getParent(n);
            if (n.equals(search)) {
                return true;
            }
        }
        for (Node p : Traversals.PARENTS.traverse(n).nodes()) {
            if (p.equals(search)) {
                return true;
            }
        }
        return false;
    }

    public List<NubUsage> listBasionymGroup(Node bas) {
        List<NubUsage> group = Lists.newArrayList();
        for (Node n : IteratorUtil.loop(Traversals.BASIONYM_GROUP.traverse(bas).nodes().iterator())) {
            NubUsage nub = dao.readNub(n);
            // nub can be null in case of placeholder neo nodes that do no have a real NubUsage in the kvp store
            // ignore those!
            if (nub != null) {
                group.add(nub);
            }
        }
        return group;
    }
}
