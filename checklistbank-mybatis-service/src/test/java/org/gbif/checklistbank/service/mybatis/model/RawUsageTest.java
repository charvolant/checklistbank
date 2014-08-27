package org.gbif.checklistbank.service.mybatis.model;

import org.gbif.checklistbank.model.RawUsage;

import java.util.Date;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;

public class RawUsageTest {

  @Test
  public void testEquals() throws Exception {
    final int key = 100000001;
    final byte[] data = "xeghwax542tgld@".getBytes();
    final UUID dkey = UUID.randomUUID();

    RawUsage raw = new RawUsage();
    raw.setUsageKey(key);
    raw.setDatasetKey(dkey);
    raw.setLastCrawled(new Date());
    raw.setData(data);

    RawUsage raw2 = new RawUsage();
    raw2.setUsageKey(key);
    raw2.setDatasetKey(dkey);
    raw2.setLastCrawled(new Date());
    raw2.setData(data);

    Assert.assertEquals(raw, raw2);

    raw.setLastCrawled(null);
    Assert.assertNotEquals(raw, raw2);

    raw2.setLastCrawled(null);
    Assert.assertEquals(raw, raw2);
  }
}