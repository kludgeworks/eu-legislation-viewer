// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.rules;

import io.cucumber.core.plugin.MessageFormatter;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves a run of the rules as Cucumber's messages, in {@code rules.ndjson}. Under {@code bazel
 * test}, the file is with the outputs of the test ({@code TEST_UNDECLARED_OUTPUTS_DIR}). Else it is
 * in the working directory.
 */
public final class RulesMessages implements ConcurrentEventListener {
  private final MessageFormatter messages;

  public RulesMessages() {
    var outputs = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    var file = outputs == null ? Path.of("rules.ndjson") : Path.of(outputs, "rules.ndjson");
    try {
      messages = new MessageFormatter(Files.newOutputStream(file));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    messages.setEventPublisher(publisher);
  }
}
