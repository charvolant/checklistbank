package org.gbif.checklistbank.neo.traverse;

import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NeoProperties;
import org.gbif.checklistbank.neo.RelType;

import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.PathExpander;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.traversal.BranchState;

/**
 * depth first, rank then scientific name order based branching.
 */
public class TaxonomicOrderExpander implements PathExpander {
  public final static TaxonomicOrderExpander TREE_EXPANDER = new TaxonomicOrderExpander(false);
  public final static TaxonomicOrderExpander TREE_WITH_SYNONYMS_EXPANDER = new TaxonomicOrderExpander(true);

  public static final String NULL_NAME = "???";

  private final boolean inclSynonyms;

  private static final Ordering<Relationship> PARENT_ORDER = Ordering.natural().onResultOf(
      new Function<Relationship, Integer>() {
        @Nullable
        @Override
        public Integer apply(Relationship rel) {
          return (Integer) rel.getEndNode().getProperty(NeoProperties.RANK, Integer.MAX_VALUE);

        }
      }
  ).compound(Ordering.natural().onResultOf(
      new Function<Relationship, String>() {
        @Nullable
        @Override
        public String apply(Relationship rel) {
          return (String) rel.getEndNode().getProperty(NeoProperties.SCIENTIFIC_NAME, NULL_NAME);
        }
      }
  ));

  private static final Ordering<Relationship> SYNONYM_ORDER = Ordering.natural().reverse().onResultOf(
      new Function<Relationship, Boolean>() {
        @Nullable
        @Override
        public Boolean apply(Relationship rel) {
          return rel.getStartNode().hasLabel(Labels.BASIONYM);
        }
      }
  ).compound(Ordering.natural().onResultOf(
      new Function<Relationship, String>() {
        @Nullable
        @Override
        public String apply(Relationship rel) {
          return (String) rel.getStartNode().getProperty(NeoProperties.SCIENTIFIC_NAME, NULL_NAME);
        }
      }
  ));

  private TaxonomicOrderExpander(boolean inclSynonyms) {
    this.inclSynonyms = inclSynonyms;
  }

  @Override
  public Iterable<Relationship> expand(Path path, BranchState state) {
    List<Relationship> children = PARENT_ORDER.sortedCopy(path.endNode().getRelationships(RelType.PARENT_OF, Direction.OUTGOING));
    if (inclSynonyms) {
      return Iterables.concat(
          SYNONYM_ORDER.sortedCopy(
              Iterables.concat(
                  path.endNode().getRelationships(RelType.SYNONYM_OF, Direction.INCOMING),
                  path.endNode().getRelationships(RelType.PROPARTE_SYNONYM_OF, Direction.INCOMING)
              )
          ),
          children
      );
    } else {
      return children;
    }
  }

  @Override
  public PathExpander reverse() {
    throw new UnsupportedOperationException();
  }

}
