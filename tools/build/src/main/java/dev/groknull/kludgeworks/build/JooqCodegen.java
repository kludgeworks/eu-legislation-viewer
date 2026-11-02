// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A module's tables as jOOQ classes (the macro jooq_tables): its migrations run by Flyway on
 * Postgres itself, in a container, and the schema they make read by jOOQ's generator. Postgres, not
 * an imitation of it, so that what is generated is what the app runs on.
 *
 * <p>Arguments: the generator's configuration (what to generate: forced types and the like), the
 * module's migrations, the package to generate into, the directory to write it in, and the image.
 */
public final class JooqCodegen {
  private JooqCodegen() {}

  public static void main(String[] args) throws Exception {
    var config = Path.of(args[0]);
    var migrations = Path.of(args[1]);
    var packageName = args[2];
    var directory = Path.of(args[3]);
    try (var postgres = new PostgreSQLContainer(DockerImageName.parse(args[4]))) {
      postgres.start();
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("filesystem:" + migrations.toAbsolutePath())
          .load()
          .migrate();
      try (var in = Files.newInputStream(config)) {
        var configuration =
            GenerationTool.load(in)
                .withJdbc(
                    new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(postgres.getJdbcUrl())
                        .withUser(postgres.getUsername())
                        .withPassword(postgres.getPassword()));
        var generator = configuration.getGenerator();
        generator
            .getDatabase()
            .withName("org.jooq.meta.postgres.PostgresDatabase")
            .withInputSchema("public")
            .withOutputSchemaToDefault(true)
            .withExcludes("flyway_schema_history");
        generator.withTarget(
            new Target()
                .withPackageName(packageName)
                .withDirectory(directory.toAbsolutePath().toString()));
        GenerationTool.generate(configuration);
      }
    }
  }
}
