package org.gbif.checklistbank.neo.traverse;

import org.gbif.checklistbank.neo.RelType;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Uniqueness;
import org.neo4j.kernel.impl.traversal.MonoDirectionalTraversalDescription;

/**
 * Various reusable traversal descriptions for taxonomic neo dbs.
 */
public class Traversals {
  public static final TraversalDescription PARENT = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition());

  public static final TraversalDescription PARENTS = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(Evaluators.excludeStartPosition());

  public static final TraversalDescription CHILDREN = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.OUTGOING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates depth first over all descendants including synonyms.
   * The node of pro parte synonyms will be visited multiple times, once for each synonym/pro_parte relationship!
   * There is no particular order for the direct children.
   * See SORTED_TREE if a taxonomic order is required!
   */
  public static final TraversalDescription DESCENDANTS = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.OUTGOING)
      .relationships(RelType.SYNONYM_OF, Direction.INCOMING)
      .relationships(RelType.PROPARTE_SYNONYM_OF, Direction.INCOMING)
      .depthFirst()
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  public static final TraversalDescription SYNONYMS = new MonoDirectionalTraversalDescription()
      .relationships(RelType.SYNONYM_OF, Direction.INCOMING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  public static final TraversalDescription ACCEPTED = new MonoDirectionalTraversalDescription()
      .relationships(RelType.SYNONYM_OF, Direction.OUTGOING)
      .relationships(RelType.PROPARTE_SYNONYM_OF, Direction.OUTGOING)
      .breadthFirst()
      .evaluator(Evaluators.toDepth(1))
      .evaluator(Evaluators.excludeStartPosition())
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Finds all nodes connected via a basionym_of relation regardless of the direction.
   */
  public static final TraversalDescription BASIONYM_GROUP = new MonoDirectionalTraversalDescription()
      .relationships(RelType.BASIONYM_OF)
      .breadthFirst()
      .uniqueness(Uniqueness.NODE_PATH);


  /**
   * Traversal that iterates depth first over all descendants including synonyms and the starting node.
   * The node of pro parte synonyms will be visited only once as pro_parte relationships are ignored.
   * There is no particular order for the direct children.
   * See SORTED_TREE traversals if a taxonomic order is required!
   */
  public static final TraversalDescription TREE_WITHOUT_PRO_PARTE = new MonoDirectionalTraversalDescription()
      .relationships(RelType.PARENT_OF, Direction.OUTGOING)
      .relationships(RelType.SYNONYM_OF, Direction.INCOMING)
      .depthFirst()
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates over all child taxa and their synonyms in a taxonomic order, i.e. by rank and secondary ordered by the name.
   * The traversal includes the initial starting node.
   * The node of pro parte synonyms will be visited multiple times, once for each synonym/pro_parte relationship!
   *
   * This traversal differes from DESCENDANTS that it includes the starting node and yields the nodes in a taxonomic order.
   * The order is a bit expensive to calculate and requires more memory. So use DESCENDANTS whenever possible.
   */
  public static final TraversalDescription SORTED_TREE = new MonoDirectionalTraversalDescription()
      .depthFirst()
      .expand(TaxonomicOrderExpander.TREE_WITH_PPSYNONYMS_EXPANDER)
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates over all child taxa and their synonyms in a taxonomic order, but excludes pro parte relations, the node of pro parte synonyms will
   * therefore be visited only once via the synonym_of relationship.
   * The traversal includes the initial starting node.
   *
   * This traversal differes from DESCENDANTS that it includes the starting node and yields the nodes in a taxonomic order.
   * The order is a bit expensive to calculate and requires more memory. So use DESCENDANTS whenever possible.
   */
  public static final TraversalDescription SORTED_TREE_WITHOUT_PRO_PARTE = new MonoDirectionalTraversalDescription()
      .depthFirst()
      .expand(TaxonomicOrderExpander.TREE_WITH_SYNONYMS_EXPANDER)
      .uniqueness(Uniqueness.NODE_PATH);

  /**
   * Traversal that iterates over all accepted child taxa in taxonomic order, i.e. by rank and secondary ordered by the name.
   * The traversal includes the initial starting node!
   */
  public static final TraversalDescription SORTED_ACCEPTED_TREE = new MonoDirectionalTraversalDescription()
      .depthFirst()
      .expand(TaxonomicOrderExpander.TREE_EXPANDER)
      .evaluator(new AcceptedOnlyEvaluator())
      .uniqueness(Uniqueness.NODE_PATH);

}
