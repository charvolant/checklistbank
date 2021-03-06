package org.gbif.checklistbank.nub.source;

import org.gbif.checklistbank.iterable.CloseableIterable;
import org.gbif.checklistbank.iterable.FutureIterator;
import org.gbif.checklistbank.utils.ExecutorUtils;
import org.gbif.utils.concurrent.NamedThreadFactory;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for nub source lists that deals with the iteratore and async loading of sources.
 */
public class NubSourceList implements CloseableIterable<NubSource> {
  private static final Logger LOG = LoggerFactory.getLogger(ClbSourceList.class);
  protected final ExecutorService exec;
  protected List<Future<NubSource>> futures = Lists.newArrayList();
  private final boolean parseNames;

  public NubSourceList(boolean parseNames) {
    this.parseNames = parseNames;
    //exec = new DirectExecutor();
    exec = Executors.newSingleThreadExecutor(new NamedThreadFactory("source-loader"));
  }

  public NubSourceList(List<? extends NubSource> sources, boolean parseNames) {
    this(parseNames);
    submitSources(sources);
  }

  /**
   * Call this method from subclasses once to submit all nub resources to this list.
   * The list will be submitted to a background loaders that calls init() on each NubSource asynchroneously.
   */
  protected void submitSources(List<? extends NubSource> sources) {
    LOG.info("Found {} backbone sources", sources.size());
    // submit loader jobs
    ExecutorCompletionService ecs = new ExecutorCompletionService(exec);
    for (NubSource src : sources) {
      futures.add(ecs.submit(new LoadSource(src, parseNames)));
    }
  }

  @Override
  public Iterator<NubSource> iterator() {
    return new FutureIterator<NubSource>(futures);
  }

  @Override
  public void close() {
    ExecutorUtils.stop(exec, "nub source loader", 10, TimeUnit.SECONDS);
  }
}
