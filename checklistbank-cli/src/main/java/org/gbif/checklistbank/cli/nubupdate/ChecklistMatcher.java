package org.gbif.checklistbank.cli.nubupdate;

import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.model.checklistbank.NameUsageMatch;
import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.service.checklistbank.NameUsageMatchingService;
import org.gbif.api.service.checklistbank.NameUsageService;
import org.gbif.checklistbank.cli.importer.DatasetImportServiceCombined;
import org.gbif.checklistbank.index.NameUsageIndexService;
import org.gbif.checklistbank.index.guice.RealTimeModule;
import org.gbif.checklistbank.service.DatasetImportService;
import org.gbif.checklistbank.service.mybatis.guice.InternalChecklistBankServiceMyBatisModule;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.beust.jcommander.internal.Maps;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.yammer.metrics.Meter;
import com.yammer.metrics.MetricRegistry;
import com.yammer.metrics.jvm.MemoryUsageGaugeSet;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ChecklistMatcher implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ChecklistMatcher.class);

  private final MatchConfiguration cfg;
  private final HikariDataSource hds;
  private Map<NameUsageMatch.MatchType, Integer> metrics = Maps.newHashMap();
  private final Meter matchMeter;
  private final DatasetImportServiceCombined importService;
  private final NameUsageService usageService;
  private final NameUsageMatchingService matchingService;
  private final UUID datasetKey;

  public ChecklistMatcher(MatchConfiguration cfg, UUID datasetKey, MetricRegistry registry) {
    this.datasetKey = datasetKey;
    this.cfg = cfg;
    this.matchMeter = registry.getMeters().get(MatchService.MATCH_METER);
    // init mybatis layer and solr from cfg instance
    Injector inj = Guice.createInjector(cfg.clb.createServiceModule(), new RealTimeModule(cfg.solr));
    importService = new DatasetImportServiceCombined(inj.getInstance(DatasetImportService.class),
                                                     inj.getInstance(NameUsageIndexService.class));
    usageService = inj.getInstance(NameUsageService.class);
    // use ws clients for nub matching
    matchingService = cfg.matching.createMatchingService();
    Key<DataSource> dsKey = Key.get(DataSource.class, Names.named(InternalChecklistBankServiceMyBatisModule.DATASOURCE_BINDING_NAME));
    hds = (HikariDataSource) inj.getInstance(dsKey);
  }

  /**
   * Uses an internal metrics registry to setup the normalizer
   */
  public static ChecklistMatcher build(MatchConfiguration cfg, UUID datasetKey) {
    MetricRegistry registry = new MetricRegistry("matcher");
    MemoryUsageGaugeSet mgs = new MemoryUsageGaugeSet();
    registry.registerAll(mgs);

    registry.meter(MatchService.MATCH_METER);

    return new ChecklistMatcher(cfg, datasetKey, registry);
  }

  public void run() {
    LOG.info("Start matching checklist {}", datasetKey);
    PagingRequest req = new PagingRequest(0, 1000);
    PagingResponse<NameUsage> resp = usageService.list(null, datasetKey, null, req);
    match(resp);
    while (!resp.isEndOfRecords()) {
      req.nextPage();
      resp = usageService.list(null, datasetKey, null, req);
      match(resp);
    }
    LOG.info("Matching of {} finished. Metrics={}", datasetKey, metrics);
    hds.close();
  }

  private void match(PagingResponse<NameUsage> resp) {
    for (NameUsage u : resp.getResults()) {
      NameUsageMatch match = matchingService.match(u.getScientificName(), u.getRank(), u, true, false);
      incCounter(match.getMatchType());
      matchMeter.mark();
    }
  }

  private void incCounter(NameUsageMatch.MatchType matchType) {
    if (metrics.containsKey(matchType)) {
      metrics.put(matchType, metrics.get(matchType)+1);
    } else {
      metrics.put(matchType, 1);
    }
  }

  public Map<NameUsageMatch.MatchType, Integer> getMetrics() {
    return metrics;
  }
}
