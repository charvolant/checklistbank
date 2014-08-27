package org.gbif.checklistbank.cli.normalizer;

import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.vocabulary.Rank;
import org.gbif.checklistbank.cli.common.NeoConfiguration;
import org.gbif.checklistbank.neo.Labels;
import org.gbif.checklistbank.neo.NeoMapper;
import org.gbif.checklistbank.neo.TaxonProperties;
import org.gbif.utils.file.FileUtils;

import java.io.File;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.tooling.GlobalGraphOperations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * base class to create neo integration tests.
 */
public abstract class NeoTest {

  protected NeoConfiguration cfg;
  protected GraphDatabaseService db;
  private NeoMapper mapper = NeoMapper.instance();

  @Before
  public void initNeoCfg() throws Exception {
    cfg = new NeoConfiguration();
    File tmp = FileUtils.createTempDir();
    cfg.neoRepository = tmp;
  }

  @After
  public void cleanup() throws Exception {
    if (db != null) {
      db.shutdown();
    }
    org.apache.commons.io.FileUtils.cleanDirectory(cfg.neoRepository);
    cfg.neoRepository.delete();
  }

  public void initDb(UUID datasetKey) {
    db = cfg.newEmbeddedDb(datasetKey);
  }

  public Transaction beginTx() {
    return db.beginTx();
  }

  public NameUsage getUsageByKey(int key) {
    Node n = db.getNodeById(key);
    return getUsageByNode(n);
  }

  public NameUsage getUsageByTaxonId(String id) {
    Node n = IteratorUtil.firstOrNull(db.findNodesByLabelAndProperty(Labels.TAXON, TaxonProperties.TAXON_ID, id));
    return getUsageByNode(n);
  }

  public NameUsage getUsageByName(String name) {
    Node n =
      IteratorUtil.firstOrNull(db.findNodesByLabelAndProperty(Labels.TAXON, TaxonProperties.CANONICAL_NAME, name));
    return getUsageByNode(n);
  }

  private NameUsage getUsageByNode(Node n) {
    return mapper.read(n);
  }

  public void showAll() {
    show(GlobalGraphOperations.at(db).getAllNodesWithLabel(Labels.TAXON));
  }

  public void showRoot() {
    show(GlobalGraphOperations.at(db).getAllNodesWithLabel(Labels.ROOT));
  }

  public void showSynonyms() {
    show(GlobalGraphOperations.at(db).getAllNodesWithLabel(Labels.SYNONYM));
  }

  private void show(Iterable<Node> iterable) {
    for (Node n : iterable) {
      NameUsage u = getUsageByNode(n);
      System.out.println("### " + n.getId());
      System.out.println(u);
    }
  }

  public void assertUsage(
    String sourceID,
    boolean synonym,
    String name,
    String basionym,
    Rank rank,
    String... parents
  ) {
    NameUsage u = getUsageByTaxonId(sourceID);
    assertEquals(synonym, u.isSynonym());
    assertEquals(rank, u.getRank());
    assertEquals(name, u.getScientificName());
    NameUsage nu = getUsageByKey(u.getKey());
    if (basionym != null) {
      assertNotNull(nu.getBasionymKey());
      NameUsage bas = getUsageByKey(nu.getBasionymKey());
      assertEquals(basionym, bas.getScientificName());
    } else {
      assertNull(nu.getBasionymKey());
    }
    Integer pid = u.getParentKey();
    for (String pn : parents) {
      NameUsage p = getUsageByKey(pid);
      assertEquals(pn, p.getScientificName());
      pid = p.getParentKey();
    }
  }
}