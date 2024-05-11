/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.jqpage.tests;

import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.api.EContainerType;
import com.io7m.ervilla.api.EPortAddressType;
import com.io7m.ervilla.postgres.EPgSpecs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class JQTestContainers
{
  private JQTestContainers()
  {

  }

  /**
   * The basic database fixture.
   *
   * @param container        The database container
   * @param connectionString
   */

  public record JQDatabaseFixture(
    EContainerType container,
    String connectionString)
  {
    /**
     * Reset the container by dropping and recreating the database. This
     * is significantly faster than destroying and recreating the container.
     *
     * @throws IOException          On errors
     * @throws InterruptedException On interruption
     */

    public void reset()
      throws IOException, InterruptedException
    {
      this.container.executeAndWaitIndefinitely(
        List.of(
          "dropdb",
          "-w",
          "-U",
          "postgres",
          "postgres"
        )
      );

      this.container.executeAndWaitIndefinitely(
        List.of(
          "createdb",
          "-w",
          "-U",
          "postgres",
          "postgres"
        )
      );

      final Path tempdir =
        Files.createTempDirectory("jqpage-");

      final var schemaFile =
      JQTestDirectories.resourceOf(
        JQTestContainers.class,
        tempdir,
        "postgres-sakila-schema.sql"
      );

      final var dataFile =
      JQTestDirectories.resourceOf(
        JQTestContainers.class,
        tempdir,
        "postgres-sakila-insert-data.sql"
      );

      this.container.copyInto(
        schemaFile, "/postgres-sakila-schema.sql");
      this.container.copyInto(
        dataFile, "/postgres-sakila-insert-data.sql");

      this.container.executeAndWaitIndefinitely(
        List.of(
        "psql",
        "-U",
        "postgres",
        "-v",
        "ON_ERROR_STOP=1",
        "-w",
        "-d",
        "postgres",
        "-f",
        "/postgres-sakila-schema.sql"
        )
      );

      this.container.executeAndWaitIndefinitely(
        List.of(
          "psql",
          "-U",
          "postgres",
          "-v",
          "ON_ERROR_STOP=1",
          "-w",
          "-d",
          "postgres",
          "-f",
          "/postgres-sakila-insert-data.sql"
        )
      );
    }

    public Connection openConnection()
      throws SQLException
    {
      final var properties = new Properties();
      properties.put("user", "postgres");
      properties.put("password", "12345678");
      return DriverManager.getConnection(this.connectionString, properties);
    }
  }

  public static JQDatabaseFixture createDatabase(
    final EContainerSupervisorType supervisor,
    final int port)
    throws IOException, InterruptedException
  {
    final var container =
      supervisor.start(
        EPgSpecs.builderFromDockerIO(
          JQTestProperties.POSTGRESQL_VERSION,
          new EPortAddressType.All(),
          port,
          "postgres",
          "postgres",
          "12345678"
        ).build()
      );

    final var connectionString =
      "jdbc:postgresql://localhost:%d/postgres"
        .formatted(Integer.valueOf(port));

    final var fixture = new JQDatabaseFixture(container, connectionString);
    fixture.reset();
    return fixture;
  }
}
