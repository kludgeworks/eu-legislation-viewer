// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.DirectorySource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.Node;

/**
 * The corpus's manifestations as tests found when the tests are discovered, not made while they run
 * (a TestFactory's): asked for CorpusTests, one test per complete manifestation of the mirror,
 * named by its Cellar ID. Bazel's runner filters what is discovered, so it shards them
 * (shard_count) and picks one by name (--test_filter); a TestFactory's tests it sees only as their
 * factory, all in one shard.
 */
public final class CorpusEngine extends HierarchicalTestEngine<CorpusEngine.Context> {
  static final String ID = "formex-corpus";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
    var engine = new EngineDescriptor(uniqueId, "Formex corpus");
    var asked =
        request.getSelectorsByType(ClassSelector.class).stream()
            .anyMatch(s -> s.getClassName().equals(CorpusTests.class.getName()));
    if (asked) {
      var corpus = CorpusTests.corpus();
      try {
        CorpusTests.manifest(corpus)
            .forEach(
                (id, celex) ->
                    engine.addChild(
                        new ManifestationTest(
                            uniqueId.append("manifestation", id),
                            new Manifestation(id, corpus.resolve("files").resolve(id)),
                            celex)));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return engine;
  }

  @Override
  protected Context createExecutionContext(ExecutionRequest request) {
    return new Context();
  }

  static final class Context implements EngineExecutionContext {}

  /** One manifestation, checked by CorpusTests.check. */
  private static final class ManifestationTest extends AbstractTestDescriptor
      implements Node<Context> {
    private final Manifestation manifestation;
    private final Optional<String> celex;

    ManifestationTest(UniqueId id, Manifestation manifestation, Optional<String> celex) {
      super(id, manifestation.name(), DirectorySource.from(manifestation.folder().toFile()));
      this.manifestation = manifestation;
      this.celex = celex;
    }

    @Override
    public Type getType() {
      return Type.TEST;
    }

    @Override
    public Context execute(Context context, DynamicTestExecutor dynamicTestExecutor)
        throws Exception {
      CorpusTests.check(manifestation, celex);
      return context;
    }
  }
}
