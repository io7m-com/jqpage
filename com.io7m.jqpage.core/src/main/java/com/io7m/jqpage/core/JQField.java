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

import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;

/**
 * A field and the order the field will be sorted.
 *
 * @param field The field
 * @param order The order
 */

public record JQField(
  Field<?> field,
  JQOrder order)
{
  /**
   * @param u The table
   *
   * @return This field qualified as a field of {@code u}
   */

  public Field<?> fieldQualified(
    final Table<?> u)
  {
    return u.field(this.field);
  }

  /**
   * @param u The table
   *
   * @return This field qualified as a field of {@code u}
   */

  public SortField<?> fieldQualifiedSort(
    final Table<?> u)
  {
    final Field<?> f = this.fieldQualified(u);
    return switch (this.order) {
      case ASCENDING -> f.asc();
      case DESCENDING -> f.desc();
    };
  }

  /**
   * @return This field as a sort field
   */

  public SortField<?> fieldOrdered()
  {
    return switch (this.order) {
      case ASCENDING -> this.field.asc();
      case DESCENDING -> this.field.desc();
    };
  }
}
