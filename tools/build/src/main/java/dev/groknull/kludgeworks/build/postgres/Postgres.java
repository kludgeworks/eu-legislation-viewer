// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build.postgres;

import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The Postgres that the build and the tests run on: the image production runs, in Docker, by
 * Testcontainers. jOOQ's code generation, the expand/contract check and the schema's lint run the
 * migrations on it, and so do the viewer's tests.
 *
 * <p>Postgres itself, not an imitation of it, so that what is generated and checked is what the app
 * runs on. Embedded Postgres (zonky's binaries) was given up: its Linux builds have no lz4, which a
 * released migration uses.
 */
public final class Postgres {
  /** The image: the version production runs. */
  public static final DockerImageName IMAGE = DockerImageName.parse("postgres:18.6");

  private Postgres() {}

  /** Starts a new Postgres container. Closing it stops and removes it. */
  public static PostgreSQLContainer start() {
    var postgres = new PostgreSQLContainer(IMAGE);
    postgres.start();
    return postgres;
  }

  /** Returns the database {@code database} of the running container {@code postgres}. */
  public static DataSource dataSource(PostgreSQLContainer postgres, String database) {
    var source = new PGSimpleDataSource();
    source.setServerNames(new String[] {postgres.getHost()});
    source.setPortNumbers(new int[] {postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)});
    source.setDatabaseName(database);
    source.setUser(postgres.getUsername());
    source.setPassword(postgres.getPassword());
    return source;
  }

  /** Returns the container's own database. */
  public static DataSource dataSource(PostgreSQLContainer postgres) {
    return dataSource(postgres, postgres.getDatabaseName());
  }
}
