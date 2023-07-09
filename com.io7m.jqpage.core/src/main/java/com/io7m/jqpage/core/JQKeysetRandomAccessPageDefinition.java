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

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.GroupField;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectSeekStepN;
import org.jooq.SelectSelectStep;
import org.jooq.SortField;
import org.jooq.TableLike;

import java.util.List;
import java.util.Objects;

/**
 * A page produced by keyset pagination. The {@link #seek} field specifies a
 * record that can be used to seek to the start of the given page. For the first
 * page, seek is the empty array (and so no seeking should be performed).
 *
 * @param table       The base table-like object
 * @param seek        The record to which to seek to reach the start of this
 *                    page
 * @param orderBy     The fields by which to order records
 * @param groupBy     The fields by which to group results
 * @param conditions  The conditions by which to filter rows
 * @param index       The page number (starting at 1)
 * @param limit       The maximum possible number of items in the page
 * @param firstOffset The offset of the first item
 * @param distinct    Whether SELECT DISTINCT needs to be used
 */

public record JQKeysetRandomAccessPageDefinition(
  Object[] seek,
  TableLike<?> table,
  SortField<?>[] orderBy,
  List<Condition> conditions,
  List<GroupField> groupBy,
  long index,
  long limit,
  long firstOffset,
  JQSelectDistinct distinct)
{
  /**
   * A page produced by keyset pagination. The {@link #seek} field specifies a
   * record that can be used to seek to the start of the given page. For the
   * first page, seek is the empty array (and so no seeking should be
   * performed).
   *
   * @param table       The base table-like object
   * @param seek        The record to which to seek to reach the start of this
   *                    page
   * @param orderBy     The fields by which to order records
   * @param groupBy     The fields by which to group results
   * @param conditions  The conditions by which to filter rows
   * @param index       The page number (starting at 1)
   * @param limit       The maximum possible number of items in the page
   * @param firstOffset The offset of the first item
   * @param distinct    Whether SELECT DISTINCT needs to be used
   */

  public JQKeysetRandomAccessPageDefinition
  {
    Objects.requireNonNull(seek, "seek");
    Objects.requireNonNull(orderBy, "orderBy");
    Objects.requireNonNull(table, "table");
    Objects.requireNonNull(orderBy, "orderBy");
    Objects.requireNonNull(conditions, "conditions");
    Objects.requireNonNull(groupBy, "groupBy");
    Objects.requireNonNull(distinct, "distinct");
  }

  /**
   * @param context The DSL context
   *
   * @return A query that selects all fields
   */

  public Select<?> query(
    final DSLContext context)
  {
    return this.queryFields(context, List.of());
  }

  /**
   * @param fields  The fields to use in the SELECT clause
   * @param context The DSL context
   *
   * @return A query that selects the given fields
   */

  public Select<?> queryFields(
    final DSLContext context,
    final List<Field<?>> fields)
  {
    final SelectSelectStep<Record> baseStart;
    if (fields.isEmpty()) {
      baseStart = doSelect(context, List.of(), this.distinct);
    } else {
      baseStart = doSelect(context, fields, this.distinct);
    }

    final SelectConditionStep<?> baseSelect =
      baseStart.from(this.table)
        .where(this.conditions);

    final SelectSeekStepN<?> baseOrderedSelect;
    if (!this.groupBy.isEmpty()) {
      baseOrderedSelect =
        baseSelect.groupBy(this.groupBy)
          .orderBy(this.orderBy);
    } else {
      baseOrderedSelect =
        baseSelect.orderBy(this.orderBy);
    }

    if (this.seek.length > 0) {
      return baseOrderedSelect
        .seek(this.seek)
        .limit(Long.valueOf(this.limit));
    } else {
      return baseOrderedSelect
        .limit(Long.valueOf(this.limit));
    }
  }

  private static SelectSelectStep<Record> doSelect(
    final DSLContext context,
    final List<Field<?>> fields,
    final JQSelectDistinct distinct)
  {
    return switch (distinct) {
      case SELECT -> context.select(fields);
      case SELECT_DISTINCT -> context.selectDistinct(fields);
    };
  }
}
