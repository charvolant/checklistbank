package org.gbif.checklistbank.service.mybatis.mapper;

import org.gbif.api.model.Constants;
import org.gbif.api.vocabulary.Rank;
import org.gbif.checklistbank.model.UsageCount;
import org.gbif.checklistbank.service.mybatis.postgres.ClbDbTestRule;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

/**
 *
 */
public class UsageCountMapperTest extends MapperITBase<UsageCountMapper> {

  public UsageCountMapperTest() {
    super(UsageCountMapper.class, ClbDbTestRule.squirrels());
  }

  @Test
  public void testRoot() {
    List<UsageCount> root = mapper.root(Constants.NUB_DATASET_KEY);
    assertEquals(1, root.size());
    assertEquals(1, root.get(0).getKey());

    root = mapper.root(UUID.fromString("109aea14-c252-4a85-96e2-f5f4d5d088f4"));
    assertEquals(1, root.size());
    assertEquals(100000001, root.get(0).getKey());
    assertEquals(27, root.get(0).getSize());
    assertEquals("Animalia", root.get(0).getName());
    assertEquals(Rank.KINGDOM, root.get(0).getRank());
  }

  @Test
  public void testChildren() {
    List<UsageCount> children = mapper.children(1);
    assertEquals(1, children.size());
    assertEquals(10, children.get(0).getKey());

    children = mapper.children(100000001);
    assertEquals(1, children.size());
    assertEquals(100000002, children.get(0).getKey());

    children = mapper.children(100000005);
    assertEquals(2, children.size());
    assertEquals(100000042, children.get(0).getKey());
    assertEquals(100000043, children.get(1).getKey());
    assertEquals(10, children.get(1).getSize());

    children = mapper.children(100000025);
    assertEquals(9, children.size());
  }
}