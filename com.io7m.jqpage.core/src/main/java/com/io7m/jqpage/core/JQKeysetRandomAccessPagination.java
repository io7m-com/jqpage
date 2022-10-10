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
import org.jooq.Statement;
import org.jooq.TableLike;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Pagination functions using keyset pagination.
 */

public final class JQKeysetRandomAccessPagination
{
  private JQKeysetRandomAccessPagination()
  {

  }

  /**
   * Create a set of pages for the given query.
   *
   * @param context    The SQL context
   * @param table      The table-like query
   * @param sortFields The ORDER BY fields
   * @param pageSize   The page size
   *
   * @return A set of pages
   */

  public static List<JQKeysetRandomAccessPageDefinition> createPageDefinitions(
    final DSLContext context,
    final TableLike<?> table,
    final List<Field<?>> sortFields,
    final long pageSize)
  {
    return createPageDefinitions(
      context,
      table,
      sortFields,
      pageSize,
      statement -> {

      }
    );
  }

  /**
   * Create a set of pages for the given query.
   *
   * @param context           The SQL context
   * @param table             The table-like query
   * @param sortFields        The ORDER BY fields
   * @param pageSize          The page size
   * @param statementListener A listener that will receive the statement to be
   *                          executed
   *
   * @return A set of pages
   */

  public static List<JQKeysetRandomAccessPageDefinition> createPageDefinitions(
    final DSLContext context,
    final TableLike<?> table,
    final List<Field<?>> sortFields,
    final long pageSize,
    final Consumer<Statement> statementListener)
  {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(table, "table");
    Objects.requireNonNull(sortFields, "fields");
    Objects.requireNonNull(statementListener, "statementListener");

    /*
     * An object is on a page boundary if the row number is exactly
     * divisible by the page size. This can be calculated on the
     * database side with a window function.
     */

    final var pageBoundaryExpression =
      DSL.rowNumber()
        .over(DSL.orderBy(sortFields))
        .modulo(DSL.inline(pageSize));

    final var casePageBoundary =
      DSL.case_(pageBoundaryExpression)
        .when(DSL.inline(0), DSL.inline(true))
        .else_(DSL.inline(false))
        .as("jq_is_page_boundary");

    final var innerSelects = new ArrayList<>(sortFields);
    innerSelects.add(casePageBoundary);

    final var innerPageBoundaries =
      context.select(innerSelects)
        .from(table)
        .orderBy(sortFields)
        .asTable("jq_inner");

    final ArrayList<Field<?>> innerFields = new ArrayList<>();
    for (final var field : sortFields) {
      innerFields.add(innerPageBoundaries.field(field));
    }

    /*
     * Use a window function to calculate page numbers. Select records
     * but only return those rows where is_page_boundary is true.
     */

    final var pageNumberExpression =
      DSL.rowNumber()
        .over(DSL.orderBy(innerFields))
        .plus(DSL.inline(1))
        .as("jq_page_number");

    final ArrayList<Field<?>> outerSelects = new ArrayList<>(innerFields);
    outerSelects.add(pageNumberExpression);

    final var outerPageBoundaries =
      context.select(outerSelects)
        .from(innerPageBoundaries)
        .where(DSL.condition(innerPageBoundaries.field("jq_is_page_boundary").isTrue()));

    final var pages =
      new ArrayList<JQKeysetRandomAccessPageDefinition>();

    statementListener.accept(outerPageBoundaries);

    final var result =
      outerPageBoundaries.fetch();

    final var orderBy = new Field<?>[sortFields.size()];
    sortFields.toArray(orderBy);

    var firstOffset = 0L;
    pages.add(
      new JQKeysetRandomAccessPageDefinition(
        new Object[0],
        orderBy,
        1L,
        pageSize,
        firstOffset)
    );

    for (final var record : result) {
      firstOffset += pageSize;
      final var values = new Object[sortFields.size()];
      for (int index = 0; index < values.length; ++index) {
        values[index] = record.get(sortFields.get(index));
      }
      pages.add(
        new JQKeysetRandomAccessPageDefinition(
          values,
          orderBy,
          record.<Long>getValue("jq_page_number", Long.class).longValue(),
          pageSize,
          firstOffset
        )
      );
    }
    return pages;
  }
}
