// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A module's tables as jOOQ classes (the macro jooq_tables): its migrations run by Flyway on
 * Postgres itself, in a container, and the schema they make read by jOOQ's generator. Postgres, not
 * an imitation of it, so that what is generated is what the app runs on. Its pending migrations
 * (NEXT_001__description.sql) run too, after the released ones: {@link PendingMigrations}.
 *
 * <p>Each package that it generates gets a {@code package-info.java} that puts it in the
 * infrastructure ring of the sliced onion. jMolecules does not look for a ring in the parent
 * packages, so without it the generated classes would be in no ring, and the module's rules would
 * not check them.
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
      PendingMigrations.configure(Flyway.configure())
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
    inInfrastructureRing(directory);
  }

  /** Writes a {@code package-info.java} into each package under {@code directory}. */
  private static void inInfrastructureRing(Path directory) throws IOException {
    List<Path> packages;
    try (var files = Files.walk(directory)) {
      packages =
          files
              .filter(file -> file.toString().endsWith(".java"))
              .map(Path::getParent)
              .distinct()
              .sorted()
              .toList();
    }
    for (var folder : packages) {
      var name =
          directory
              .relativize(folder)
              .toString()
              .replace(folder.getFileSystem().getSeparator(), ".");
      Files.writeString(
          folder.resolve("package-info.java"),
          """
          @org.jmolecules.architecture.onion.simplified.InfrastructureRing
          package %s;
          """
              .formatted(name));
    }
  }
}
