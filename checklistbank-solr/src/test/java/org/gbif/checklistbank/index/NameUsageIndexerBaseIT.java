package org.gbif.checklistbank.index;

import org.gbif.checklistbank.index.guice.EmbeddedSolrReference;
import org.gbif.checklistbank.service.mybatis.postgres.ClbDbTestRule;
import org.gbif.utils.file.properties.PropertiesUtil;

import java.io.IOException;
import java.util.Properties;
import javax.xml.parsers.ParserConfigurationException;

import com.google.common.base.Throwables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.junit.BeforeClass;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Base class for integration tests using the squirrels test solr index.
 * The class builds a fresh index before all tests run.
 */
public abstract class NameUsageIndexerBaseIT {

    private static final Logger LOG = LoggerFactory.getLogger(NameUsageIndexerBaseIT.class);
    private static EmbeddedSolrReference solrRef;
    private static NameUsageIndexer nameUsageIndexer;

    @BeforeClass
    public static void setup() throws IOException, SAXException, ParserConfigurationException {

        // run liquibase & dbSetup
        LOG.info("Run liquibase & dbSetup once");
        try {
            ClbDbTestRule rule = ClbDbTestRule.squirrels();
            rule.apply(new Statement() {
                public void evaluate() throws Throwable {
                    // do nothing
                }
            }, null).evaluate();
        } catch (Throwable throwable) {
            Throwables.propagate(throwable);
        }

        // Creates the injector, merging properties taken from default test indexing and checklistbank
        Properties props = PropertiesUtil.loadProperties(NameUsageIndexingConfig.CLB_PROPERTY_FILE);
        Properties props2 = PropertiesUtil.loadProperties(NameUsageIndexingConfig.CLB_INDEXING_PROPERTY_TEST_FILE);
        props.putAll(props2);
        Injector injector = Guice.createInjector(new SolrIndexingTestModule(props));
        // Gets the indexer instance
        solrRef = injector.getInstance(EmbeddedSolrReference.class);
        nameUsageIndexer = injector.getInstance(NameUsageIndexer.class);
        nameUsageIndexer.run();
    }

    protected static EmbeddedSolrServer solr() {
        return solrRef.getSolr();
    }

    protected static NameUsageIndexer indexer() {
        return nameUsageIndexer;
    }

}
