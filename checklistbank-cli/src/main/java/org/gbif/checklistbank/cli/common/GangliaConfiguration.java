package org.gbif.checklistbank.cli.common;

import java.util.concurrent.TimeUnit;

import com.beust.jcommander.Parameter;
import com.google.common.base.MoreObjects;
import com.yammer.metrics.MetricRegistry;
import com.yammer.metrics.ganglia.GangliaReporter;
import info.ganglia.gmetric4j.gmetric.GMetric;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A configuration class which holds the host and port to connect yammer metrics to a ganglia server.
 */
@SuppressWarnings("PublicField")
public class GangliaConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(GangliaConfiguration.class);

  @Parameter(names = "--ganglia-host")
  public String host;

  @Parameter(names = "--ganglia-port")
  public int port = 8649;

  @Parameter(names = "--ganglia-name")
  public String name;

  /**
   * Starts the GangliaReporter, pointing to the configured host and port.
   */
  @JsonIgnore
  public void start(MetricRegistry registry) {
    if (host != null && port > 0) {
      LOG.info("Reporting to ganglia at {}:{}", host, port);
      final GMetric ganglia = new GMetric(host, port, GMetric.UDPAddressingMode.MULTICAST, 1);
      final GangliaReporter reporter = GangliaReporter.forRegistry(registry)
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .build(ganglia);
      reporter.start(1, TimeUnit.MINUTES);
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("host", host)
      .add("port", port)
      .add("name", name)
      .toString();
  }
}
