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
 * Saves a run of the rules as Cucumber's messages, in {@code rules.ndjson}, and the methods that
 * apply the rules ({@link RulesApplied}) beside it, in {@value RulesApplied#FILE}. Under {@code
 * bazel test}, the files are with the outputs of the test ({@code TEST_UNDECLARED_OUTPUTS_DIR}).
 * Else they are in the working directory.
 */
public final class RulesMessages implements ConcurrentEventListener {
  private final MessageFormatter messages;

  public RulesMessages() {
    var outputs = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    var folder = outputs == null ? Path.of("") : Path.of(outputs);
    try {
      messages = new MessageFormatter(Files.newOutputStream(folder.resolve("rules.ndjson")));
      RulesApplied.write(
          folder.resolve(RulesApplied.FILE),
          RulesApplied.scan(RulesMessages.class.getClassLoader()).applied());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void setEventPublisher(EventPublisher publisher) {
    messages.setEventPublisher(publisher);
  }
}
