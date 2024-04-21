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

import com.io7m.ervilla.api.EContainerSupervisorType;
import com.io7m.ervilla.test_extension.ErvillaCloseAfterSuite;
import com.io7m.ervilla.test_extension.ErvillaConfiguration;
import com.io7m.ervilla.test_extension.ErvillaExtension;
import com.io7m.jqpage.core.JQField;
import com.io7m.jqpage.core.JQKeysetRandomAccessPageDefinition;
import com.io7m.jqpage.core.JQKeysetRandomAccessPagination;
import com.io7m.jqpage.core.JQKeysetRandomAccessPaginationParameters;
import com.io7m.jqpage.core.JQOffsetPagination;
import com.io7m.jqpage.core.JQSelectDistinct;
import com.io7m.jqpage.tests.JQTestContainers.JQDatabaseFixture;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.io7m.jqpage.core.JQOrder.ASCENDING;
import static com.io7m.jqpage.core.JQOrder.DESCENDING;
import static com.io7m.jqpage.tests.Tables.FILM;
import static com.io7m.jqpage.tests.Tables.FILM_ACTOR;
import static com.io7m.jqpage.tests.tables.Actor.ACTOR;
import static com.io7m.jqpage.tests.tables.Customer.CUSTOMER;
import static org.jooq.SQLDialect.POSTGRES;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith({ErvillaExtension.class})
@ErvillaConfiguration(disabledIfUnsupported = true, projectName = "com.io7m.jqpage")
public final class JQPaginationTest
{
  private static JQDatabaseFixture DATABASE_FIXTURE;

  @BeforeAll
  public static void setupOnce(
    final @ErvillaCloseAfterSuite EContainerSupervisorType containers)
    throws Exception
  {
    DATABASE_FIXTURE =
      JQTestContainers.createDatabase(containers, 35432);
  }

  @BeforeEach
  public void setup()
    throws Exception
  {
    DATABASE_FIXTURE.reset();

    this.statements = new ArrayList<String>();
  }

  private static Connection openConnection()
    throws SQLException
  {
    return DATABASE_FIXTURE.openConnection();
  }

  private static DSLContext createContext(
    final Connection connection)
  {
    final var settings =
      new Settings().withRenderNameCase(RenderNameCase.LOWER);
    return DSL.using(connection, POSTGRES, settings);
  }

  private ArrayList<String> statements;

  @AfterEach
  public void tearDown()
  {
    for (final var st : this.statements) {
      System.out.println(st);
      System.out.println();
    }
  }

  /**
   * Select all actors.
   *
   * @throws Exception On errors
   */

  @Test
  public void testBasicActors()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context = createContext(connection);
      context.selectFrom(ACTOR)
        .fetch()
        .forEach(record -> {
          System.out.printf(
            "%s %s %s%n",
            record.get(ACTOR.ACTOR_ID),
            record.get(ACTOR.FIRST_NAME),
            record.get(ACTOR.LAST_NAME)
          );
        });
      assertEquals(
        Integer.valueOf(200),
        context.select(DSL.count()).from(ACTOR).fetchInto(int.class).get(0)
      );
    }
  }

  /**
   * Select all customers.
   *
   * @throws Exception On errors
   */

  @Test
  public void testBasicCustomers()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context = createContext(connection);
      context.selectFrom(CUSTOMER)
        .fetch()
        .forEach(record -> {
          System.out.printf(
            "%s %s %s%n",
            record.get(CUSTOMER.CUSTOMER_ID),
            record.get(CUSTOMER.FIRST_NAME),
            record.get(CUSTOMER.LAST_NAME)
          );
        });
      assertEquals(
        Integer.valueOf(599),
        context.select(DSL.count()).from(CUSTOMER).fetchInto(int.class).get(0)
      );
    }
  }

  private record Person(
    int id,
    String nameFirst,
    String nameLast)
  {

  }

  /**
   * Select actors using offset pagination.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorOffsetPagination()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);
      final var baseQuery =
        context.selectFrom(ACTOR)
          .orderBy(ACTOR.ACTOR_ID);

      {
        final var page =
          JQOffsetPagination.paginate(
            context,
            baseQuery,
            List.of(new JQField(ACTOR.ACTOR_ID, ASCENDING)),
            75L,
            0L,
            JQPaginationTest::toActor
          );

        assertEquals(1L, page.pageFirstOffset());
        assertEquals(3L, page.pageCount());
        assertEquals(1L, page.pageIndex());
        assertEquals(75, page.items().size());
      }

      {
        final var page =
          JQOffsetPagination.paginate(
            context,
            baseQuery,
            List.of(new JQField(ACTOR.ACTOR_ID, ASCENDING)),
            75L,
            75L,
            JQPaginationTest::toActor,
            statement -> this.statements.add(statement.toString())
          );

        assertEquals(76L, page.pageFirstOffset());
        assertEquals(3L, page.pageCount());
        assertEquals(2L, page.pageIndex());
        assertEquals(75, page.items().size());
      }

      {
        final var page =
          JQOffsetPagination.paginate(
            context,
            baseQuery,
            List.of(new JQField(ACTOR.ACTOR_ID, ASCENDING)),
            75L,
            150L,
            JQPaginationTest::toActor
          );

        assertEquals(151L, page.pageFirstOffset());
        assertEquals(3L, page.pageCount());
        assertEquals(3L, page.pageIndex());
        assertEquals(50, page.items().size());
      }
    }
  }

  /**
   * Select actors using offset pagination with a query that returns nothing.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorOffsetPaginationEmpty()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);
      final var baseQuery =
        context.selectFrom(ACTOR)
          .where(ACTOR.FIRST_NAME.eq("NONEXISTENT"))
          .orderBy(ACTOR.ACTOR_ID);

      {
        final var page =
          JQOffsetPagination.paginate(
            context,
            baseQuery,
            List.of(new JQField(ACTOR.ACTOR_ID, ASCENDING)),
            75L,
            0L,
            JQPaginationTest::toActor
          );

        assertEquals(0L, page.pageFirstOffset());
        assertEquals(0L, page.pageCount());
        assertEquals(0L, page.pageIndex());
        assertEquals(0, page.items().size());
      }
    }
  }

  /**
   * Select actors using keyset pagination.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorKeysetPagination()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);

      final var parameters =
        JQKeysetRandomAccessPaginationParameters.forTable(CUSTOMER)
          .addSortField(new JQField(CUSTOMER.FIRST_NAME, ASCENDING))
          .addSortField(new JQField(CUSTOMER.LAST_NAME, ASCENDING))
          .addWhereCondition(CUSTOMER.FIRST_NAME.like("%%I%%"))
          .setPageSize(75L)
          .setStatementListener(s -> this.statements.add(s.toString()))
          .build();

      final List<JQKeysetRandomAccessPageDefinition> pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(context, parameters);

      assertEquals(4, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());
      }

      {
        final var page = pages.get(1);
        final List<Person> records =
          context.selectFrom(CUSTOMER)
            .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
            .orderBy(page.orderBy())
            .seek(page.seek())
            .limit(page.limit())
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(2L, page.index());
        assertEquals(75, records.size());
        assertEquals("FREDDIE", records.get(0).nameFirst);
        assertEquals("DUGGAN", records.get(0).nameLast);
        assertEquals("FREDERICK", records.get(1).nameFirst);
        assertEquals("ISBELL", records.get(1).nameLast);
      }

      {
        final var page = pages.get(2);
        final var statement =
          context.selectFrom(CUSTOMER)
            .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
            .orderBy(page.orderBy())
            .seek(page.seek())
            .limit(page.limit());

        this.statements.add(statement.toString());

        final var records =
          statement.fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(3L, page.index());
        assertEquals(75, records.size());
        assertEquals("MARIO", records.get(0).nameFirst);
        assertEquals("CHEATHAM", records.get(0).nameLast);
        assertEquals("MARION", records.get(1).nameFirst);
        assertEquals("OCAMPO", records.get(1).nameLast);
      }

      {
        final var page = pages.get(3);
        final var records =
          context.selectFrom(CUSTOMER)
            .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
            .orderBy(page.orderBy())
            .seek(page.seek())
            .limit(page.limit())
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(4L, page.index());
        assertEquals(8, records.size());
        assertEquals("VIRGIL", records.get(0).nameFirst);
        assertEquals("WOFFORD", records.get(0).nameLast);
        assertEquals("VIRGINIA", records.get(1).nameFirst);
        assertEquals("GREEN", records.get(1).nameLast);
      }
    }
  }

  /**
   * Select actors using keyset pagination.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorKeysetPaginationDescending()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);

      final var parameters =
        JQKeysetRandomAccessPaginationParameters.forTable(CUSTOMER)
          .addSortField(new JQField(CUSTOMER.FIRST_NAME, DESCENDING))
          .addSortField(new JQField(CUSTOMER.LAST_NAME, DESCENDING))
          .addWhereCondition(CUSTOMER.FIRST_NAME.like("%%I%%"))
          .setPageSize(75L)
          .setStatementListener(s -> this.statements.add(s.toString()))
          .build();

      final List<JQKeysetRandomAccessPageDefinition> pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(
          context, parameters);

      assertEquals(4, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());

        final List<Person> records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);

        assertEquals("WILMA", records.get(0).nameFirst);
        assertEquals("RICHARDS", records.get(0).nameLast);
        assertEquals("WILLIE", records.get(1).nameFirst);
        assertEquals("MARKHAM", records.get(1).nameLast);
      }

      {
        final var page = pages.get(1);
        final List<Person> records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(2L, page.index());
        assertEquals(75, records.size());

        assertEquals("MAURICE", records.get(0).nameFirst);
        assertEquals("CRAWLEY", records.get(0).nameLast);
        assertEquals("MATTIE", records.get(1).nameFirst);
        assertEquals("HOFFMAN", records.get(1).nameLast);
      }

      {
        final var page = pages.get(2);
        final var statement = page.query(context);

        this.statements.add(statement.toString());

        final var records =
          statement.fetch()
            .map(JQPaginationTest::toCustomer);

        assertEquals(3L, page.index());
        assertEquals(75, records.size());

        assertEquals("GINA", records.get(0).nameFirst);
        assertEquals("WILLIAMSON", records.get(0).nameLast);
        assertEquals("GILBERT", records.get(1).nameFirst);
        assertEquals("SLEDGE", records.get(1).nameLast);
      }

      {
        final var page = pages.get(3);
        final List<Person> records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);

        assertEquals(4L, page.index());
        assertEquals(8, records.size());

        assertEquals("ANTONIO", records.get(0).nameFirst);
        assertEquals("MEEK", records.get(0).nameLast);
        assertEquals("ANNIE", records.get(1).nameFirst);
        assertEquals("RUSSELL", records.get(1).nameLast);
      }
    }
  }

  /**
   * Select actors using keyset pagination.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorKeysetPaginationDescendingID()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);

      final var parameters =
        JQKeysetRandomAccessPaginationParameters.forTable(CUSTOMER)
          .addSortField(new JQField(CUSTOMER.CUSTOMER_ID, DESCENDING))
          .setPageSize(75L)
          .setStatementListener(s -> this.statements.add(s.toString()))
          .build();

      final List<JQKeysetRandomAccessPageDefinition> pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(context, parameters);

      assertEquals(8, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());

        final List<Person> records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);

        assertEquals(599, records.get(0).id);
        assertEquals(598, records.get(1).id);
        assertEquals(525, records.get(74).id);
      }

      {
        final var page = pages.get(1);
        final List<Person> records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(2L, page.index());
        assertEquals(75, records.size());
        assertEquals(524, records.get(0).id);
        assertEquals(523, records.get(1).id);
        assertEquals(450, records.get(74).id);
      }

      {
        final var page = pages.get(2);
        final var statement =
          page.query(context);

        this.statements.add(statement.toString());

        final var records =
          statement.fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(3L, page.index());
        assertEquals(75, records.size());
        assertEquals(449, records.get(0).id);
        assertEquals(448, records.get(1).id);
        assertEquals(375, records.get(74).id);
      }

      {
        final var page = pages.get(3);
        final var records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(4L, page.index());
        assertEquals(75, records.size());
        assertEquals(374, records.get(0).id);
        assertEquals(373, records.get(1).id);
        assertEquals(300, records.get(74).id);
      }

      {
        final var page = pages.get(4);
        final var records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(5L, page.index());
        assertEquals(75, records.size());
        assertEquals(299, records.get(0).id);
        assertEquals(298, records.get(1).id);
        assertEquals(225, records.get(74).id);
      }

      {
        final var page = pages.get(5);
        final var records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(6L, page.index());
        assertEquals(75, records.size());
        assertEquals(224, records.get(0).id);
        assertEquals(223, records.get(1).id);
        assertEquals(150, records.get(74).id);
      }

      {
        final var page = pages.get(6);
        final var records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(7L, page.index());
        assertEquals(75, records.size());
        assertEquals(149, records.get(0).id);
        assertEquals(148, records.get(1).id);
        assertEquals(75, records.get(74).id);
      }

      {
        final var page = pages.get(7);
        final var records =
          page.query(context)
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(8L, page.index());
        assertEquals(74, records.size());
        assertEquals(74, records.get(0).id);
        assertEquals(73, records.get(1).id);
        assertEquals(1, records.get(73).id);
      }
    }
  }

  /**
   * Select actors using keyset pagination with grouping.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorKeysetPaginationGrouping()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);

      final var baseTable =
        ACTOR
          .join(FILM_ACTOR).on(FILM_ACTOR.ACTOR_ID.eq(ACTOR.ACTOR_ID))
          .join(FILM).on(FILM.FILM_ID.eq(FILM_ACTOR.FILM_ID));

      final var parameters =
        JQKeysetRandomAccessPaginationParameters.forTable(baseTable)
          .addSortField(new JQField(ACTOR.ACTOR_ID, ASCENDING))
          .addWhereCondition(FILM.TITLE.equalIgnoreCase("MIRACLE VIRTUAL"))
          .addGroupByField(ACTOR.ACTOR_ID)
          .addGroupByField(FILM.FILM_ID)
          .setPageSize(75L)
          .setStatementListener(s -> this.statements.add(s.toString()))
          .build();

      final List<JQKeysetRandomAccessPageDefinition> pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(context, parameters);

      assertEquals(1, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());

        final List<Person> records =
          page.queryFields(context, List.of(ACTOR.ACTOR_ID, ACTOR.FIRST_NAME, ACTOR.LAST_NAME, FILM.TITLE))
            .fetch()
            .map(JQPaginationTest::toActor);

        assertEquals(1, records.size());
        assertEquals("FRED", records.get(0).nameFirst);
        assertEquals("COSTNER", records.get(0).nameLast);
      }
    }
  }

  /**
   * Select actors using keyset pagination with DISTINCT.
   *
   * @throws Exception On errors
   */

  @Test
  public void testActorKeysetPaginationDistinct()
    throws Exception
  {
    try (var connection = openConnection()) {
      final var context =
        createContext(connection);

      final var baseTable =
        ACTOR.leftJoin(FILM_ACTOR).using(ACTOR.ACTOR_ID);

      final var parameters =
        JQKeysetRandomAccessPaginationParameters.forTable(baseTable)
          .addSortField(new JQField(ACTOR.FIRST_NAME, ASCENDING))
          .addSortField(new JQField(ACTOR.LAST_NAME, ASCENDING))
          .setDistinct(JQSelectDistinct.SELECT_DISTINCT)
          .setPageSize(100L)
          .setStatementListener(s -> this.statements.add(s.toString()))
          .build();

      final List<JQKeysetRandomAccessPageDefinition> pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(context, parameters);

      assertEquals(55L, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());

        final List<Person> records =
          page.queryFields(context, List.of(ACTOR.ACTOR_ID, ACTOR.FIRST_NAME, ACTOR.LAST_NAME))
            .fetch()
            .map(JQPaginationTest::toActor);

        assertEquals("ADAM", records.get(0).nameFirst);
        assertEquals("GRANT", records.get(0).nameLast);

        assertEquals("ADAM", records.get(1).nameFirst);
        assertEquals("HOPPER", records.get(1).nameLast);
      }

      checkEveryActorIsPresent(context, pages);
    }
  }

  private static void checkEveryActorIsPresent(
    final DSLContext context,
    final List<JQKeysetRandomAccessPageDefinition> pages)
  {
    final var allPeople =
      context.select(ACTOR.ACTOR_ID, ACTOR.FIRST_NAME, ACTOR.LAST_NAME)
        .from(ACTOR)
        .fetch()
        .stream()
        .map(JQPaginationTest::toActor)
        .collect(Collectors.toUnmodifiableSet());

    final var pagesPeople =
      new HashSet<Person>();

    final List<Field<?>> fields =
      List.of(ACTOR.ACTOR_ID, ACTOR.FIRST_NAME, ACTOR.LAST_NAME);

    for (final var page : pages) {
      final List<Person> records =
        page.queryFields(context, fields)
          .fetch()
          .map(JQPaginationTest::toActor);

      pagesPeople.addAll(records);
    }

    assertEquals(allPeople, pagesPeople);
  }

  private static Person toCustomer(
    final Record record)
  {
    return new Person(
      record.getValue(CUSTOMER.CUSTOMER_ID, Integer.class).intValue(),
      record.getValue(CUSTOMER.FIRST_NAME, String.class),
      record.getValue(CUSTOMER.LAST_NAME, String.class)
    );
  }

  private static Person toActor(
    final Record record)
  {
    return new Person(
      record.getValue(ACTOR.ACTOR_ID, Integer.class).intValue(),
      record.getValue(ACTOR.FIRST_NAME, String.class),
      record.getValue(ACTOR.LAST_NAME, String.class)
    );
  }
}
