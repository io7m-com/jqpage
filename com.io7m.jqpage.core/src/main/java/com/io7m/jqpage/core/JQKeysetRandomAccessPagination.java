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
import org.jooq.GroupField;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.SelectSelectStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
   * @param parameters The parameters
   *
   * @return A set of pages
   */

  public static List<JQKeysetRandomAccessPageDefinition> createPageDefinitions(
    final DSLContext context,
    final JQKeysetRandomAccessPaginationParameters parameters)
  {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(parameters, "parameters");

    /*
     * An object is on a page boundary if the row number is exactly
     * divisible by the page size. This can be calculated on the
     * database side with a window function.
     */

    final var sortFields =
      parameters.sortFields();
    final SortField<?>[] fieldsForOrderBy =
      buildFieldsForInnerOrderBy(sortFields);
    final Field<?>[] fieldsForSelect =
      buildFieldsForInnerSelect(sortFields);

    final var pageSize =
      parameters.pageSize();
    final var pageBoundaryExpression =
      DSL.rowNumber()
        .over(DSL.orderBy(fieldsForOrderBy))
        .modulo(DSL.inline(pageSize));

    final var casePageBoundary =
      DSL.case_(pageBoundaryExpression)
        .when(DSL.inline(0), DSL.inline(true))
        .else_(DSL.inline(false))
        .as("jq_is_page_boundary");

    final var innerSelects =
      new ArrayList<>(Arrays.asList(fieldsForSelect));
    innerSelects.add(casePageBoundary);

    final var table =
      parameters.table();
    final var whereConditions =
      parameters.whereConditions();
    final var groupBy =
      parameters.groupBy();

    final var innerPageBaseQuery =
      doSelect(innerSelects, parameters.distinct())
        .from(table)
        .where(whereConditions);

    final Table<?> innerPageBoundaries =
      applyGroupByIfNecessary(groupBy, fieldsForOrderBy, innerPageBaseQuery);
    final SortField<?>[] fieldsForOrderByInner =
      buildQualifiedFieldsForInnerOrderBy(sortFields, innerPageBoundaries);
    final Field<?>[] fieldsForSelectInner =
      buildQualifiedFieldsForInnerSelect(sortFields, innerPageBoundaries);

    /*
     * Use a window function to calculate page numbers. Select records
     * but only return those rows where is_page_boundary is true.
     */

    final var pageNumberExpression =
      DSL.rowNumber()
        .over(DSL.orderBy(fieldsForOrderByInner))
        .plus(DSL.inline(1))
        .as("jq_page_number");

    final ArrayList<Field<?>> outerSelects =
      new ArrayList<>(Arrays.asList(fieldsForSelectInner));
    outerSelects.add(pageNumberExpression);

    final var isPageBoundary =
      DSL.condition(innerPageBoundaries.field("jq_is_page_boundary").isTrue());

    final var outerPageBoundaries =
      context.select(outerSelects)
        .from(innerPageBoundaries)
        .where(isPageBoundary);

    final var pages =
      new ArrayList<JQKeysetRandomAccessPageDefinition>();

    parameters.statementListener()
      .accept(outerPageBoundaries);

    final var result =
      outerPageBoundaries.fetch();

    var firstOffset = 0L;
    pages.add(
      new JQKeysetRandomAccessPageDefinition(
        new Object[0],
        table,
        fieldsForOrderBy,
        whereConditions,
        groupBy,
        1L,
        pageSize,
        firstOffset,
        parameters.distinct()
      )
    );

    for (final var record : result) {
      firstOffset += pageSize;
      final var values = new Object[fieldsForOrderBy.length];
      for (int index = 0; index < values.length; ++index) {
        values[index] = record.get(fieldsForSelect[index]);
      }
      pages.add(
        new JQKeysetRandomAccessPageDefinition(
          values,
          table,
          fieldsForOrderBy,
          whereConditions,
          groupBy,
          record.<Long>getValue("jq_page_number", Long.class).longValue(),
          pageSize,
          firstOffset,
          parameters.distinct()
        )
      );
    }
    return pages;
  }

  private static SelectSelectStep<Record> doSelect(
    final ArrayList<Field<?>> innerSelects,
    final JQSelectDistinct distinct)
  {
    return switch (distinct) {
      case SELECT -> DSL.select(innerSelects);
      case SELECT_DISTINCT -> DSL.selectDistinct(innerSelects);
    };
  }

  private static Field<?>[] buildQualifiedFieldsForInnerSelect(
    final List<JQField> sortFields,
    final Table<?> innerPageBoundaries)
  {
    final var fieldsForSelectInner = new Field[sortFields.size()];
    for (int index = 0; index < sortFields.size(); ++index) {
      fieldsForSelectInner[index] =
        sortFields.get(index).fieldQualified(innerPageBoundaries);
    }
    return fieldsForSelectInner;
  }

  private static SortField<?>[] buildQualifiedFieldsForInnerOrderBy(
    final List<JQField> sortFields,
    final Table<?> innerPageBoundaries)
  {
    final var fieldsForOrderByInner = new SortField[sortFields.size()];
    for (int index = 0; index < sortFields.size(); ++index) {
      fieldsForOrderByInner[index] =
        sortFields.get(index).fieldQualifiedSort(innerPageBoundaries);
    }
    return fieldsForOrderByInner;
  }

  private static Table<?> applyGroupByIfNecessary(
    final List<GroupField> groupBy,
    final SortField<?>[] fieldsForOrderBy,
    final SelectConditionStep<Record> innerPageBaseQuery)
  {
    final Table<?> innerPageBoundaries;
    if (!groupBy.isEmpty()) {
      innerPageBoundaries =
        innerPageBaseQuery.groupBy(groupBy)
          .orderBy(fieldsForOrderBy)
          .asTable("jq_inner");
    } else {
      innerPageBoundaries =
        innerPageBaseQuery.orderBy(fieldsForOrderBy)
          .asTable("jq_inner");
    }
    return innerPageBoundaries;
  }

  private static Field<?>[] buildFieldsForInnerSelect(
    final List<JQField> sortFields)
  {
    final var fieldsForSelect = new Field[sortFields.size()];
    for (int index = 0; index < sortFields.size(); ++index) {
      fieldsForSelect[index] = sortFields.get(index).field();
    }
    return fieldsForSelect;
  }

  private static SortField<?>[] buildFieldsForInnerOrderBy(
    final List<JQField> sortFields)
  {
    final var fieldsForOrderBy = new SortField[sortFields.size()];
    for (int index = 0; index < sortFields.size(); ++index) {
      fieldsForOrderBy[index] = sortFields.get(index).fieldOrdered();
    }
    return fieldsForOrderBy;
  }
}
