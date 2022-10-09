jqpage
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.jqpage/com.io7m.jqpage.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.jqpage%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/https/s01.oss.sonatype.org/com.io7m.jqpage/com.io7m.jqpage.svg?style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/jqpage/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m/jqpage.svg?style=flat-square)](https://codecov.io/gh/io7m/jqpage)

![jqpage](./src/site/resources/jqpage.jpg?raw=true)

| JVM             | Platform | Status |
|-----------------|----------|--------|
| OpenJDK LTS     | Linux    | [![Build (OpenJDK LTS, Linux)](https://img.shields.io/github/workflow/status/io7m/jqpage/main-openjdk_lts-linux)](https://github.com/io7m/jqpage/actions?query=workflow%3Amain-openjdk_lts-linux) |
| OpenJDK Current | Linux    | [![Build (OpenJDK Current, Linux)](https://img.shields.io/github/workflow/status/io7m/jqpage/main-openjdk_current-linux)](https://github.com/io7m/jqpage/actions?query=workflow%3Amain-openjdk_current-linux)
| OpenJDK Current | Windows  | [![Build (OpenJDK Current, Windows)](https://img.shields.io/github/workflow/status/io7m/jqpage/main-openjdk_current-windows)](https://github.com/io7m/jqpage/actions?query=workflow%3Amain-openjdk_current-windows)

## Usage

### JOOQ

The `jqpage` package uses [jooq](https://www.jooq.org/). APIs are expressed
in terms of `jooq` query structures.

### Sakila

This documentation is written using example data from the [sakila](https://github.com/jOOQ/sakila)
database.

### Offset Pagination

Offset pagination is provided by the `JQOffsetPagination` class. Offset
pagination is generally considered inferior to 
[keyset pagination](#keyset-pagination) but may be necessary when executing
queries that are too complex to use keyset pagination. The `JQOffsetPagination`
class implements pagination in a single database round trip by calculating
page boundary offsets using window functions. See
[Calculating Pagination Metadata](https://blog.jooq.org/calculating-pagination-metadata-without-extra-roundtrips-in-sql/)
for the general technique.

Assuming a base query that selects everything from an `ACTOR` table:

```
DSLContext context = ...

final var baseQuery =
  context.selectFrom(ACTOR)
    .orderBy(ACTOR.ACTOR_ID);
```

The `JQOffsetPagination.paginate()` method can paginate the query. The method
takes a base query, a list of fields used in the `ORDER BY` clause, a number
specifying the desired number of results per page, an offset value, and
a function from `Record` values to values of your application's domain
types. The method returns a `JQPage` value that contains a list of results,
a page number, and a count of the total number of pages that could be returned.

```
record Person(
  int id,
  String nameFirst,
  String nameLast)
{

}

Person toActor(
  final Record record)
{
  return new Person(
    record.getValue(ACTOR.ACTOR_ID, Integer.class).intValue(),
    record.getValue(ACTOR.FIRST_NAME, String.class),
    record.getValue(ACTOR.LAST_NAME, String.class)
  );
}

final JQPage<Person> page =
  JQOffsetPagination.paginate(
    context,
    baseQuery,
    List.of(ACTOR.ACTOR_ID),
    75L,
    0L,
    this::toActor
  );
```

### Keyset Pagination

Keyset pagination is provided by the `JQKeysetRandomAccessPagination` class.
The `JQKeysetRandomAccessPagination.createPageDefinitions()` function takes
a _table expression_, a list of fields for the `ORDER BY` clause, and
the desired page size. The method returns a list of
`JQKeysetRandomAccessPageDefinition` structures that individually contain
all the information required to seek directly to any page of the executed
query in more or less constant time. The `JQKeysetRandomAccessPagination`
class assumes the use of a database that supports window functions.

See [Faster SQL Pagination with Keysets](https://blog.jooq.org/faster-sql-pagination-with-keysets-continued/)
for the general technique.

```
Person toCustomer(
  final Record record)
{
  return new Person(
    record.getValue(CUSTOMER.CUSTOMER_ID, Integer.class).intValue(),
    record.getValue(CUSTOMER.FIRST_NAME, String.class),
    record.getValue(CUSTOMER.LAST_NAME, String.class)
  );
}

final List<JQKeysetRandomAccessPageDefinition> pages =
  JQKeysetRandomAccessPagination.createPageDefinitions(
    context,
    CUSTOMER.where(CUSTOMER.FIRST_NAME.like("%%I%%")),
    List.of(CUSTOMER.FIRST_NAME, CUSTOMER.LAST_NAME),
    75L
  );
  
final JQKeysetRandomAccessPageDefinition page = pages.get(1);

final List<Person> records =
  context.selectFrom(CUSTOMER)
    .where(CUSTOMER.FIRST_NAME.like("%%I%%"))
    .orderBy(page.orderBy())
    .seek(page.seek())
    .limit(page.limit())
    .fetch()
    .map(this::toCustomer);
```

