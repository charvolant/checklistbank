package org.gbif.checklistbank.nub.source;

import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.vocabulary.TaxonomicStatus;
import org.gbif.checklistbank.cli.normalizer.Normalizer;
import org.gbif.checklistbank.cli.normalizer.NormalizerConfiguration;
import org.gbif.checklistbank.neo.UsageDao;
import org.gbif.dwca.io.ArchiveFactory;
import org.gbif.io.DownloadUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import javax.annotation.Nullable;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A nub source which is backed by a dwca checklist file which gets normalized into neo4j first
 * and then drives the nub source
 */
public class DwcaSource extends NubSource {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaSource.class);

  private NormalizerConfiguration cfg = new NormalizerConfiguration();

  public DwcaSource(String name, File dwca) throws IOException {
    super(UUID.randomUUID(), name.replaceAll("\\s", " "), false);
    initRepos();
    File archiveDir = cfg.archiveDir(key);
    LOG.info("Open dwc archive {}", dwca);
    ArchiveFactory.openArchive(dwca, archiveDir);
  }

  public DwcaSource(String name, URL dwca) throws IOException {
    this(name, download(dwca));
  }

  private void initRepos() {
    cfg.archiveRepository = Files.createTempDir();
    cfg.neo.neoRepository = Files.createTempDir();
  }

  private static File download(URL dwca) throws IOException {
    final File tmp = File.createTempFile("dwca-download", "dwca");
    LOG.info("Download dwca from {} into {}", dwca, tmp);
    DownloadUtil.download(dwca, tmp);
    return tmp;
  }

  private static String nullsafeString(@Nullable Object obj) {
    return obj == null ? null : obj.toString();
  }

  @Override
  void initNeo(NeoUsageWriter writer) throws Exception {
    UsageDao dao = normalize();
    LOG.info("Import source usages");
    try (Transaction tx = dao.beginTx()) {
      for (Node n : dao.allNodes()) {
        NameUsage u = dao.readUsage(n, true);
        TaxonomicStatus status = u.getTaxonomicStatus();
        if (status == null) {
          status = u.isSynonym() ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED;
        }
        // u.id, u.parent_fk, u.basionym_fk, u.rank, u.status, u.nom_status, n.scientific_name
        String[] row = new String[7];
        row[0] = String.valueOf(n.getId());
        row[1] = nullsafeString(status.isSynonym() ? u.getAcceptedKey() : u.getParentKey());
        row[2] = nullsafeString(u.getBasionymKey());
        row[3] = nullsafeString(u.getRank());
        row[4] = status.name();
        //TODO: nom status
        row[5] = null;
        row[6] = u.getScientificName();
        writer.addRow(row);
      }
    }
    dao.closeAndDelete();
  }

  /**
   * read dwca stream and normalize it
   */
  private UsageDao normalize() {
    LOG.info("Normalize dwca");
    Normalizer normalizer = Normalizer.create(cfg, key);
    normalizer.run();
    return UsageDao.open(cfg.neo, key);
  }

  @Override
  public void close() {
    super.close();
    FileUtils.deleteQuietly(cfg.archiveRepository);
    FileUtils.deleteQuietly(cfg.neo.neoRepository);
  }
}
