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

import java.util.Objects;

/**
 * A page produced by keyset pagination. The {@link #seek} field specifies a
 * record that can be used to seek to the start of the given page. For the first
 * page, seek is the empty array (and so no seeking should be performed).
 *
 * @param seek        The record to which to seek to reach the start of this
 *                    page
 * @param orderBy     The fields by which to order records
 * @param index       The page number
 * @param limit       The maximum possible number of items in the page
 * @param firstOffset The offset of the first item
 */

public record JQKeysetRandomAccessPageDefinition(
  Object[] seek,
  Field<?>[] orderBy,
  long index,
  long limit,
  long firstOffset)
{
  /**
   * A page produced by keyset pagination. The {@link #seek} field specifies a
   * record that can be used to seek to the start of the given page. For the
   * first page, seek is the empty array (and so no seeking should be
   * performed).
   *
   * @param seek        The record to which to seek to reach the start of this
   *                    page
   * @param orderBy     The fields by which to order records
   * @param index       The page number
   * @param limit       The maximum possible number of items in the page
   * @param firstOffset The offset of the first item
   */

  public JQKeysetRandomAccessPageDefinition
  {
    Objects.requireNonNull(seek, "seek");
    Objects.requireNonNull(orderBy, "orderBy");
  }
}
