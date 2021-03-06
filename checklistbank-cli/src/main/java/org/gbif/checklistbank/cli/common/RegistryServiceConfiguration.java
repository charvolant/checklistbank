package org.gbif.checklistbank.cli.common;

import org.gbif.registry.ws.client.guice.RegistryWsClientModule;
import org.gbif.ws.client.guice.AnonymousAuthModule;
import org.gbif.ws.client.guice.SingleUserAuthModule;

import java.util.Properties;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.beust.jcommander.ParametersDelegate;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.neo4j.helpers.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration needed to anonymously connect to the registry webservices.
 */
@SuppressWarnings("PublicField")
public class RegistryServiceConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(RegistryServiceConfiguration.class);

  @ParametersDelegate
  @Valid
  @NotNull
  public String wsUrl = "http://api.gbif.org/v1";

  public String user;

  public String password;

  public Injector createRegistryInjector() {
    Properties props = new Properties();
    props.put("registry.ws.url", wsUrl);

    Module auth;
    if (!Strings.isBlank(user) && !Strings.isBlank(password)) {
      auth = new SingleUserAuthModule(user, password);
    } else {
      auth = new AnonymousAuthModule();
    }
    Injector injClient = Guice.createInjector(auth, new RegistryWsClientModule(props));
    LOG.info("Connecting to registry services at {}", wsUrl);
    return injClient;
  }

}
