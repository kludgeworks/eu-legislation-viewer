// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import dev.groknull.kludgeworks.build.flyway.PendingMigrations;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Target;

/**
 * A module's tables as jOOQ classes (the macro jooq_tables): its migrations run by Flyway on
 * Postgres itself, embedded (zonky's binaries, the major version production runs), and the schema
 * they make read by jOOQ's generator. Postgres, not an imitation of it, so that what is generated
 * is what the app runs on. Its pending migrations (NEXT_001__description.sql) run too, after the
 * released ones: {@link PendingMigrations}.
 *
 * <p>Each package that it generates gets a {@code package-info.java} that puts it in the
 * infrastructure ring of the sliced onion. jMolecules does not look for a ring in the parent
 * packages, so without it the generated classes would be in no ring, and the module's rules would
 * not check them.
 *
 * <p>Arguments: the generator's configuration (what to generate: forced types and the like), the
 * module's migrations, the package to generate into, and the source jar to write.
 */
public final class JooqCodegen {
  /**
   * The tables and columns whose comment starts with {@code deprecated:}, as patterns of their
   * names: left out, so that the code can no longer use what a later release drops.
   */
  static final String DEPRECATED =
      """
      select c.relname || case when d.objsubid = 0 then '' else '\\.' || a.attname end
      from pg_description as d
      join pg_class as c on c.oid = d.objoid and d.classoid = 'pg_class'::regclass
      join pg_namespace as n on n.oid = c.relnamespace
      left join pg_attribute as a on a.attrelid = c.oid and a.attnum = d.objsubid
      where n.nspname = 'public' and d.description like 'deprecated:%'
      """;

  private JooqCodegen() {}

  public static void main(String[] args) throws Exception {
    var config = Path.of(args[0]);
    var migrations = Path.of(args[1]);
    var packageName = args[2];
    var jar = Path.of(args[3]);
    var directory = SourceJar.directoryFor(jar);
    try (var postgres = EmbeddedPostgres.start()) {
      var url = postgres.getJdbcUrl("postgres", "postgres");
      PendingMigrations.configure(Flyway.configure())
          .dataSource(postgres.getPostgresDatabase())
          .locations("filesystem:" + migrations.toAbsolutePath())
          .load()
          .migrate();
      try (var in = Files.newInputStream(config)) {
        var configuration =
            GenerationTool.load(in)
                .withJdbc(
                    new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(url)
                        .withUser("postgres"));
        var generator = configuration.getGenerator();
        generator
            .getDatabase()
            .withName("org.jooq.meta.postgres.PostgresDatabase")
            .withInputSchema("public")
            .withOutputSchemaToDefault(true)
            .withExcludes("flyway_schema_history")
            .withIncludeExcludeColumns(true)
            .withExcludeSql(DEPRECATED);
        generator.withTarget(
            new Target()
                .withPackageName(packageName)
                .withDirectory(directory.toAbsolutePath().toString()));
        GenerationTool.generate(configuration);
      }
    }
    inInfrastructureRing(directory);
    SourceJar.write(directory, jar);
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
          "@org.jmolecules.architecture.onion.simplified.InfrastructureRing\npackage "
              + name
              + ";\n");
    }
  }
}
