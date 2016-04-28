package org.gbif.checklistbank.utils;

import org.gbif.io.CSVReader;
import org.gbif.io.TabWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;

/**
 * Manual utility to add an occurrence count column to a tab delimited file with taxonKeys in the first column.
 */
@Ignore
public class OccMetricsColAppender {
  private final File reports;

  public OccMetricsColAppender(File reports) {
    this.reports = reports;
  }

  private void addMetrics(String fn) throws IOException {
    CSVReader reader = new CSVReader(new File(reports, fn), "utf8", "\t", null, 0);
    try (Writer writer = new FileWriter(new File(reports, "2-"+fn))) {
      TabWriter tab = new TabWriter(writer);
      for (String[] row : reader) {
        String[] row2 = Arrays.copyOf(row, row.length+1);
        row2[row.length] = count(row[0]);
        tab.write(row2);
      }
    }
  }

  private String count(String taxonKey) {
    try {
      URL url = new URL("http://api.gbif.org/v1/occurrence/count?taxonKey="+taxonKey);
      FilterInputStream stream = (FilterInputStream) url.getContent();
      String count = IOUtils.toString(stream, "UTF8");
      stream.close();
      return count;

    } catch (Exception e) {
      e.printStackTrace();
    }
    return "-1";
  }

  public static void main(String[] args) throws Exception {
    OccMetricsColAppender reporter = new OccMetricsColAppender(new File("/Users/markus/Dropbox/nub-ng/nub/reports"));
    reporter.addMetrics("deleted.txt");
  }

}