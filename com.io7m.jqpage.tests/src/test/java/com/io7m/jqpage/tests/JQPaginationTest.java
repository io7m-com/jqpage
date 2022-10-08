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

import com.io7m.jqpage.core.JQKeysetRandomAccessPagination;
import com.io7m.jqpage.core.JQOffsetPagination;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.io7m.jqpage.tests.tables.Actor.ACTOR;
import static com.io7m.jqpage.tests.tables.Customer.CUSTOMER;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class JQPaginationTest
  extends JQWithDatabaseContract
{
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
            List.of(ACTOR.ACTOR_ID),
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
            List.of(ACTOR.ACTOR_ID),
            75L,
            75L,
            JQPaginationTest::toActor
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
            List.of(ACTOR.ACTOR_ID),
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
            List.of(ACTOR.ACTOR_ID),
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

      final var fields = new Field[]{
        CUSTOMER.FIRST_NAME,
        CUSTOMER.LAST_NAME
      };

      final var pages =
        JQKeysetRandomAccessPagination.createPageDefinitions(
          context,
          CUSTOMER.where(CUSTOMER.FIRST_NAME.like("%%I%%")),
          List.of(CUSTOMER.FIRST_NAME, CUSTOMER.LAST_NAME),
          75L
        );

      assertEquals(4, pages.size());

      {
        final var page = pages.get(0);
        assertEquals(1L, page.index());
      }

      {
        final var page = pages.get(1);
        final var records =
          context.selectFrom(CUSTOMER)
            .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
            .orderBy(page.orderBy())
            .seek(page.seek())
            .limit(page.limit())
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(2L, page.index());
        assertEquals(75, records.size());
      }

      {
        final var page = pages.get(2);
        final var records =
          context.selectFrom(CUSTOMER)
            .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
            .orderBy(page.orderBy())
            .seek(page.seek())
            .limit(page.limit())
            .fetch()
            .map(JQPaginationTest::toCustomer);
        assertEquals(3L, page.index());
        assertEquals(75, records.size());
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
      }
    }
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
