package org.gbif.checklistbank.kryo;

import org.gbif.api.model.checklistbank.DatasetMetrics;
import org.gbif.api.model.checklistbank.Description;
import org.gbif.api.model.checklistbank.Distribution;
import org.gbif.api.model.checklistbank.NameUsage;
import org.gbif.api.model.checklistbank.NameUsageMediaObject;
import org.gbif.api.model.checklistbank.NameUsageMetrics;
import org.gbif.api.model.checklistbank.ParsedName;
import org.gbif.api.model.checklistbank.Reference;
import org.gbif.api.model.checklistbank.SpeciesProfile;
import org.gbif.api.model.checklistbank.TypeSpecimen;
import org.gbif.api.model.checklistbank.VerbatimNameUsage;
import org.gbif.api.model.checklistbank.VernacularName;
import org.gbif.api.model.common.Identifier;
import org.gbif.checklistbank.model.UsageExtensions;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.EolReferenceTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.IucnTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.dwc.terms.UnknownTerm;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClbKryoFactoryTest {
    Kryo kryo = new ClbKryoFactory().create();

    @Test
    public void testTerms() throws Exception {
        List<Term> terms = Lists.newArrayList(
                DwcTerm.scientificName, DwcTerm.associatedOrganisms, DwcTerm.taxonID,
                DcTerm.title,
                GbifTerm.canonicalName,
                IucnTerm.threatStatus, EolReferenceTerm.primaryTitle, new UnknownTerm(URI.create("http://gbif.org/abcdefg"))
        );
        assertSerde(terms);
    }

    @Test
    public void testEmptyModels() throws Exception {
        assertSerde(new NameUsage());
        assertSerde(new VerbatimNameUsage());
        assertSerde(new NameUsageMetrics());
        assertSerde(new UsageExtensions());
        assertSerde(new ParsedName());
        assertSerde(new Description());
        assertSerde(new Distribution());
        assertSerde(new Identifier());
        assertSerde(new NameUsageMediaObject());
        assertSerde(new Reference());
        assertSerde(new SpeciesProfile());
        assertSerde(new NameUsage());
        assertSerde(new TypeSpecimen());
        assertSerde(new VernacularName());
        assertSerde(new DatasetMetrics());
    }

    private void assertSerde(Object obj) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
        Output output = new Output(buffer);
        kryo.writeObject(output, obj);
        output.close();
        byte[] bytes = buffer.toByteArray();

        final Input input = new Input(bytes);
        Object obj2 = kryo.readObject(input, obj.getClass());

        assertEquals(obj, obj2);
    }
}