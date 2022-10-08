/*
 * Copyright © 2022 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

import org.jooq.DSLContext;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static org.jooq.SQLDialect.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class JQWithDatabaseContract
{
  private static final Logger LOG =
    LoggerFactory.getLogger(JQWithDatabaseContract.class);

  @Container
  private static final PostgreSQLContainer<?> POSTGRESQL =
    new PostgreSQLContainer<>("postgres")
      .withDatabaseName("jqpage")
      .withUsername("postgres")
      .withPassword("12345678");

  private static PGSimpleDataSource SOURCE;

  @BeforeAll
  public static void serverSetup()
    throws Exception
  {
    LOG.debug("serverSetup");
    waitForDatabaseToStart();
    SOURCE = createPool();

    copyToContainer("/postgres-sakila-schema.sql");
    copyToContainer("/postgres-sakila-insert-data.sql");
    POSTGRESQL.addEnv("PGPASSWORD", "12345678");

    {
      final var r =
        POSTGRESQL.execInContainer(
          "psql",
          "-U",
          "postgres",
          "-w",
          "-d",
          "jqpage",
          "-f",
          "/postgres-sakila-schema.sql"
        );
      assertEquals(0, r.getExitCode());
    }

    {
      final var r =
        POSTGRESQL.execInContainer(
          "psql",
          "-U",
          "postgres",
          "-w",
          "-d",
          "jqpage",
          "-f",
          "/postgres-sakila-insert-data.sql"
        );
      assertEquals(0, r.getExitCode());
    }
  }

  private static void copyToContainer(
    final String name)
    throws IOException
  {
    final var c = JQWithDatabaseContract.class;
    try (var s = c.getResourceAsStream(name)) {
      final var data = s.readAllBytes();
      POSTGRESQL.copyFileToContainer(Transferable.of(data), name);
    }
  }

  private static PGSimpleDataSource createPool()
  {
    final var source = new PGSimpleDataSource();
    source.setServerNames(new String[]{
      POSTGRESQL.getHost()
    });
    source.setPortNumbers(new int[]{
      POSTGRESQL.getFirstMappedPort().intValue()
    });
    source.setDatabaseName("jqpage");
    source.setUser("postgres");
    source.setPassword("12345678");
    return source;
  }

  protected static Connection openConnection()
    throws SQLException
  {
    final var conn = SOURCE.getConnection();
    conn.setAutoCommit(false);
    return conn;
  }

  @AfterAll
  public static void serverTearDown()
    throws Exception
  {
    LOG.debug("serverTearDown");
  }

  private static void waitForDatabaseToStart()
    throws InterruptedException, TimeoutException
  {
    LOG.debug("starting database");
    POSTGRESQL.start();
    LOG.debug("waiting for database to start");
    final var timeWait = Duration.ofSeconds(60L);
    final var timeThen = Instant.now();
    while (!POSTGRESQL.isRunning()) {
      Thread.sleep(1L);
      final var timeNow = Instant.now();
      if (Duration.between(timeThen, timeNow).compareTo(timeWait) > 0) {
        LOG.error("timed out waiting for database to start");
        throw new TimeoutException("Timed out waiting for database to start");
      }
    }
    LOG.debug("database started");
  }

  protected static DSLContext createContext(
    final Connection connection)
  {
    final var settings =
      new Settings().withRenderNameCase(RenderNameCase.LOWER);
    return DSL.using(connection, POSTGRES, settings);
  }
}
