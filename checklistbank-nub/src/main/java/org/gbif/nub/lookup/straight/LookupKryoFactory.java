package org.gbif.nub.lookup.straight;

import org.gbif.api.vocabulary.Kingdom;
import org.gbif.api.vocabulary.Rank;
import org.gbif.nub.mapdb.ImmutableListSerializer;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;

/**
 * A kryo factory that knows how to serde the LookupUsage class
 */
public class LookupKryoFactory implements KryoFactory {

  @Override
  public Kryo create() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(true);

    // model class(es)
    kryo.register(LookupUsage.class);

    // java & commons
    kryo.register(Date.class);
    kryo.register(HashMap.class);
    kryo.register(HashSet.class);
    kryo.register(ArrayList.class);
    ImmutableListSerializer.registerSerializers(kryo);

    // enums
    kryo.register(Rank.class);
    kryo.register(Kingdom.class);

    return kryo;
  }
}
