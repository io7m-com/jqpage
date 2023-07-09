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


package com.io7m.jqpage.core;

import org.jooq.Condition;
import org.jooq.GroupField;
import org.jooq.Statement;
import org.jooq.TableLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The (immutable) parameters required to achieve pagination.
 */

public final class JQKeysetRandomAccessPaginationParameters
{
  private final TableLike<?> table;
  private final List<JQField> sortFields;
  private final List<Condition> whereConditions;
  private final List<GroupField> groupBy;
  private final long pageSize;
  private final Consumer<Statement> statementListener;
  private final JQSelectDistinct distinct;

  /**
   * @return The table source
   */

  public TableLike<?> table()
  {
    return this.table;
  }

  /**
   * @return The ORDER BY fields
   */

  public List<JQField> sortFields()
  {
    return this.sortFields;
  }

  /**
   * @return The WHERE conditions
   */

  public List<Condition> whereConditions()
  {
    return this.whereConditions;
  }

  /**
   * @return The GROUP BY fields
   */

  public List<GroupField> groupBy()
  {
    return this.groupBy;
  }

  /**
   * @return The maximum size of a page
   */

  public long pageSize()
  {
    return this.pageSize;
  }

  /**
   * @return A listener that will receive statements
   */

  public Consumer<Statement> statementListener()
  {
    return this.statementListener;
  }

  /**
   * @return A specification of SELECT or SELECT DISTINCT
   */

  public JQSelectDistinct distinct()
  {
    return this.distinct;
  }

  private JQKeysetRandomAccessPaginationParameters(
    final TableLike<?> inTable,
    final List<JQField> inSortFields,
    final List<Condition> inWhereConditions,
    final List<GroupField> inGroupBy,
    final long inPageSize,
    final Consumer<Statement> inStatementListener,
    final JQSelectDistinct inDistinct)
  {
    this.table =
      Objects.requireNonNull(inTable, "table");
    this.sortFields =
      Objects.requireNonNull(inSortFields, "sortFields");
    this.whereConditions =
      Objects.requireNonNull(inWhereConditions, "whereConditions");
    this.groupBy =
      Objects.requireNonNull(inGroupBy, "groupBy");
    this.pageSize =
      inPageSize;
    this.statementListener =
      Objects.requireNonNull(inStatementListener, "statementListener");
    this.distinct =
      Objects.requireNonNull(inDistinct, "distinct");
  }

  /**
   * Create a new mutable parameter builder for the given table expression.
   *
   * @param inTable The table source
   *
   * @return A builder
   */

  public static Builder forTable(
    final TableLike<?> inTable)
  {
    return new Builder(
      Objects.requireNonNull(inTable, "inTable")
    );
  }

  /**
   * A mutable parameter forTable.
   */

  public static final class Builder
  {
    private TableLike<?> table;
    private List<JQField> sortFields = new ArrayList<>();
    private List<Condition> whereConditions = new ArrayList<>();
    private List<GroupField> groupBy = new ArrayList<>();
    private long pageSize;
    private Consumer<Statement> statementListener = s -> {
    };
    private JQSelectDistinct distinct = JQSelectDistinct.SELECT;

    private Builder(
      final TableLike<?> inTable)
    {
      this.table = Objects.requireNonNull(inTable, "table");
    }

    /**
     * Add a sort field. The order that this method is called is significant;
     * {@code addSortField(F); addSortField(G);} will sort the resulting query
     * first by {@code F} and then by {@code G}.
     *
     * @param newField The sort field
     *
     * @return this
     */

    public Builder addSortField(
      final JQField newField)
    {
      this.sortFields.add(Objects.requireNonNull(newField, "field"));
      return this;
    }

    /**
     * Add a group field. The order that this method is called is significant;
     * {@code addGroupByField(F); addGroupByField(G);} will group the resulting
     * query first by {@code F} and then by {@code G}.
     *
     * @param newField The group field
     *
     * @return this
     */

    public Builder addGroupByField(
      final GroupField newField)
    {
      this.groupBy.add(Objects.requireNonNull(newField, "field"));
      return this;
    }

    /**
     * Add a WHERE condition. All the conditions will be combined with
     * logical AND.
     *
     * @param newCondition The condition
     *
     * @return this
     */

    public Builder addWhereCondition(
      final Condition newCondition)
    {
      this.whereConditions.add(
        Objects.requireNonNull(newCondition, "condition")
      );
      return this;
    }

    /**
     * Set the maximum page size.
     *
     * @param newPageSize The size
     *
     * @return this
     */

    public Builder setPageSize(
      final long newPageSize)
    {
      this.pageSize = newPageSize;
      return this;
    }

    /**
     * Specify whether SELECT expressions should be DISTINCT.
     *
     * @param newDistinct The distinct specification
     *
     * @return this
     */

    public Builder setDistinct(
      final JQSelectDistinct newDistinct)
    {
      this.distinct = Objects.requireNonNull(newDistinct, "distinct");
      return this;
    }

    /**
     * Set a listener that will receive statements.
     *
     * @param newStatementListener The receiver
     *
     * @return this
     */

    public Builder setStatementListener(
      final Consumer<Statement> newStatementListener)
    {
      this.statementListener =
        Objects.requireNonNull(newStatementListener, "statementListener");
      return this;
    }

    /**
     * Build a set of parameters based on all the values given so far.
     *
     * @return An immutable set of parameters
     */

    public JQKeysetRandomAccessPaginationParameters build()
    {
      return new JQKeysetRandomAccessPaginationParameters(
        this.table,
        List.copyOf(this.sortFields),
        List.copyOf(this.whereConditions),
        List.copyOf(this.groupBy),
        this.pageSize,
        this.statementListener,
        this.distinct
      );
    }
  }
}
