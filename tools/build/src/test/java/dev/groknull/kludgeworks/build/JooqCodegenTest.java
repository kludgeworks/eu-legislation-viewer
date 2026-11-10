// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** jOOQ's code generation leaves out what the schema deprecates, so the code cannot use it. */
class JooqCodegenTest {
  @TempDir Path directory;

  @Test
  void leavesOutDeprecatedTablesAndColumns() throws Exception {
    var migrations = Files.createDirectories(directory.resolve("act"));
    Files.writeString(
        migrations.resolve("V2026_11_0_001__act.sql"),
        """
        create table act (celex text not null, title text);
        create table draft (id int);
        comment on column act.title is 'deprecated: use act_title';
        comment on table draft is 'deprecated: nothing reads it';
        """);
    var config = directory.resolve("jooq.xml");
    Files.writeString(
        config,
        """
        <configuration xmlns="http://www.jooq.org/xsd/jooq-codegen-3.21.0.xsd">
          <generator><database/></generator>
        </configuration>
        """);
    var jar = directory.resolve("act.srcjar");

    JooqCodegen.main(
        new String[] {config.toString(), migrations.toString(), "act.jooq", jar.toString()});

    try (var sources = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), Map.of())) {
      var tables = sources.getPath("act/jooq/tables");
      assertThat(tables.resolve("Draft.java")).doesNotExist();
      assertThat(Files.readString(tables.resolve("Act.java")))
          .contains("DSL.name(\"celex\")")
          .doesNotContain("DSL.name(\"title\")");
    }
  }
}
