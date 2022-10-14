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

package com.io7m.jqpage.core;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SortField;
import org.jooq.Statement;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pagination functions using offset pagination.
 */

public final class JQOffsetPagination
{
  private JQOffsetPagination()
  {

  }

  /**
   * Paginate a query using offset pagination.
   *
   * @param context    The DSL context
   * @param query      The base query
   * @param sort       The fields by which the query is to be sorted
   * @param limit      The limit (page size)
   * @param offset     The starting offset
   * @param fromRecord A function that converts records to values
   * @param <T>        The type of returned values
   *
   * @return A page of results
   */

  public static <T> JQPage<T> paginate(
    final DSLContext context,
    final Select<?> query,
    final List<JQField> sort,
    final long limit,
    final long offset,
    final Function<Record, T> fromRecord)
  {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(sort, "sort");
    Objects.requireNonNull(fromRecord, "fromRecord");

    return paginate(
      context,
      query,
      sort,
      limit,
      offset,
      fromRecord,
      statement -> {

      }
    );
  }

  /**
   * Paginate a query using offset pagination.
   *
   * @param context           The DSL context
   * @param query             The base query
   * @param sort              The fields by which the query is to be sorted
   * @param limit             The limit (page size)
   * @param offset            The starting offset
   * @param fromRecord        A function that converts records to values
   * @param <T>               The type of returned values
   * @param statementListener A listener that will receive the statement to be
   *                          executed
   *
   * @return A page of results
   */

  public static <T> JQPage<T> paginate(
    final DSLContext context,
    final Select<?> query,
    final List<JQField> sort,
    final long limit,
    final long offset,
    final Function<Record, T> fromRecord,
    final Consumer<Statement> statementListener)
  {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(sort, "sort");
    Objects.requireNonNull(fromRecord, "fromRecord");

    final var sortArray = new JQField[sort.size()];
    sort.toArray(sortArray);

    return paginate(
      context,
      query,
      sortArray,
      limit,
      offset,
      fromRecord,
      statementListener
    );
  }

  /**
   * Paginate a query using offset pagination.
   *
   * @param context           The DSL context
   * @param query             The base query
   * @param sort              The fields by which the query is to be sorted
   * @param limit             The limit (page size)
   * @param offset            The starting offset
   * @param fromRecord        A function that converts records to values
   * @param <T>               The type of returned values
   * @param statementListener A listener that will receive the statement to be
   *                          executed
   *
   * @return A page of results
   */

  public static <T> JQPage<T> paginate(
    final DSLContext context,
    final Select<?> query,
    final JQField[] sort,
    final long limit,
    final long offset,
    final Function<Record, T> fromRecord,
    final Consumer<Statement> statementListener)
  {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(sort, "sort");
    Objects.requireNonNull(fromRecord, "fromRecord");
    Objects.requireNonNull(statementListener, "statementListener");

    final var pageQuery =
      paginateInner(context, query, sort, limit, offset);

    statementListener.accept(pageQuery);

    final var results =
      pageQuery.fetch();
    final var items =
      new ArrayList<T>();

    long pageIndex = 0L;
    long pageCount = 0L;
    Long pageFirstOffset = null;

    for (final var record : results) {
      if (pageFirstOffset == null) {
        pageFirstOffset =
          record.get("jq_page_item_index", Long.class);
        final var pageItemsTotal =
          record.get("jq_page_items_total", Double.class)
            .doubleValue();
        pageCount = (long) Math.ceil(pageItemsTotal / (double) limit);
      }
      pageIndex =
        record.get("jq_page_index_current", Long.class)
          .longValue();
      items.add(fromRecord.apply(record));
    }

    if (pageFirstOffset == null) {
      pageFirstOffset = Long.valueOf(0L);
    }

    return new JQPage<>(
      items,
      pageIndex,
      pageCount,
      pageFirstOffset.longValue()
    );
  }

  private static Select<?> paginateInner(
    final DSLContext context,
    final Select<?> query,
    final JQField[] sort,
    final long limit,
    final long offset)
  {
    final var u =
      query.asTable("jq_inner");

    final var uOrderByFields = new SortField<?>[sort.length];
    for (int index = 0; index < sort.length; ++index) {
      uOrderByFields[index] = sort[index].fieldQualifiedSort(u);
    }

    final var pageItemsTotal =
      DSL.count().over()
        .as("jq_page_items_total");

    final var pageItemIndex =
      DSL.rowNumber()
        .over()
        .orderBy(uOrderByFields)
        .as("jq_page_item_index");

    final var t =
      context.select(u.asterisk())
        .select(pageItemsTotal, pageItemIndex)
        .from(u)
        .orderBy(uOrderByFields)
        .limit(Long.valueOf(limit))
        .offset(Long.valueOf(offset));

    final var pageSize =
      DSL.count().over()
        .as("jq_page_size");

    final var pageIndexLast =
      DSL.field(DSL.max(t.field(pageItemIndex)).over().eq(t.field(pageItemsTotal)))
        .as("jq_page_index_last");

    final var pageIndexCurrent =
      t.field(pageItemIndex)
        .minus(DSL.inline(1))
        .div(Long.valueOf(limit))
        .plus(DSL.inline(1))
        .as("jq_page_index_current");

    final var tOrderByFields = new SortField<?>[sort.length];
    for (int index = 0; index < sort.length; ++index) {
      tOrderByFields[index] = sort[index].fieldQualifiedSort(t.asTable());
    }

    return context.select(t.fields(query.getSelect().toArray(Field[]::new)))
      .select(
        pageSize,
        pageIndexLast,
        t.field(pageItemsTotal),
        t.field(pageItemIndex),
        pageIndexCurrent)
      .from(t)
      .orderBy(tOrderByFields);
  }
}
