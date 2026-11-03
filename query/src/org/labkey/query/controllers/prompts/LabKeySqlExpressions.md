## LabKey SQL Expression Reference

This reference covers the LabKey SQL that can appear in a single expression, such as a calculated column or one item in
a SELECT list or WHERE clause. Use it instead of ANSI or PostgreSQL SQL.

### Not Supported: Write This Instead

LabKey SQL rejects these common PostgreSQL and ANSI forms. Write the replacement instead:

- `EXTRACT(YEAR FROM d)`: use `YEAR(d)`, `MONTH(d)`, `DAYOFMONTH(d)`, `HOUR(d)`, and so on.
- `col::integer`: use `CAST(col AS INTEGER)`.
- `d + INTERVAL '1 day'`: use `TIMESTAMPADD('SQL_TSI_DAY', 1, d)`.
- `DATE '2001-02-03'`: use `{d '2001-02-03'}` or `CAST('2001-02-03' AS DATE)`. The space after `{d` or `{ts` is
  required.
- `POSITION(a IN b)`: use `LOCATE(a, b)`.
- `TRIM(BOTH ' ' FROM x)`: use `LTRIM(RTRIM(x))`.
- `x ILIKE 'a%'`: use `LOWER(x) LIKE 'a%'`.
- `x ~ 'regex'`, `x SIMILAR TO p`: use `similar_to(x, pattern)`.
- `a < b < c`: comparisons do not chain. Use `a < b AND b < c`.
- `CASE WHEN c THEN a = b END`: a bare comparison cannot be a THEN or ELSE result. Parenthesize it: `THEN (a = b)`.

### Identifiers and Literals

- Double-quote a column name that contains spaces or special characters, or that is a reserved word:
  `"Physical Exam"`. Escape an embedded `"` by doubling it.
- String literals use single quotes. Escape `'` by doubling it: `'Jim''s Item'`. There are no backslash escapes.
- Date and time literals: `{d '2001-02-03'}` and `{ts '2001-02-03 04:05:06'}`.
- Booleans are `TRUE` and `FALSE`. Special doubles: `CAST('Infinity' AS DOUBLE)`, `CAST('-Infinity' AS DOUBLE)`,
  `CAST('NaN' AS DOUBLE)`.
- Reserved words: `all, any, and, as, asc, avg, between, both, case, class, count, current_date, current_time,
  current_timestamp, delete, desc, distinct, elements, else, empty, end, escape, except, exists, false, fetch, from,
  full, group, having, in, indices, inner, insert, intersect, into, is, join, leading, left, like, limit, max, member,
  min, new, not, null, of, on, or, order, outer, right, select, set, some, stddev, sum, trailing, then, true, union,
  update, user, versioned, when, where`

### Division

- Guard the divisor: `a / NULLIF(b, 0)`.
- Integer divided by integer truncates. Cast one operand: `CAST(a AS DOUBLE) / NULLIF(b, 0)`.

### Functions

Mathematical: `abs(v)`, `acos(v)`, `asin(v)`, `atan(v)`, `atan2(v1, v2)`, `ceiling(v)`, `cos(r)`, `cot(r)`,
`degrees(r)`, `exp(n)`, `floor(v)`, `log(n)` (natural), `log10(n)`, `mod(dividend, divider)`, `pi()`,
`power(base, exp)`, `radians(d)`, `rand([seed])`, `round(v[, precision])`, `sign(v)`, `sin(v)`, `sqrt(v)`, `tan(v)`,
`truncate(v, precision)` (may require `CAST(v AS NUMERIC)`)

String: `concat(a, b, ...)` (same as `||`: NULL if any argument is NULL), `lcase(s)`/`lower(s)`,
`ucase(s)`/`upper(s)`, `left(s, n)`, `right(s, n)`, `length(s)`, `locate(substr, s[, start])`, `ltrim(s)`, `rtrim(s)`,
`repeat(s, count)`, `startswith(s, prefix)`, `substring(s, start[, length])` (1-based)

Date and time:
- `TIMESTAMPDIFF(interval, start, end)` returns `end - start` as a decimal. Use it only for hours, minutes, or
  seconds: `'SQL_TSI_HOUR'`, `'SQL_TSI_MINUTE'`, `'SQL_TSI_SECOND'`. Any other interval fails when the query runs.
  - Fails when both values are dates without a time, such as Date columns or CURDATE().
- `AGE(start, end, interval)` allows only `'SQL_TSI_DAY'`, `'SQL_TSI_MONTH'`, `'SQL_TSI_YEAR'`. Without an interval it
  returns years.
- `AGE_IN_YEARS(start, end)`, `AGE_IN_MONTHS(start, end)`, `AGE_IN_DAYS(start, end)` return whole, completed units.
  - AGE_IN_DAYS ignores the time of day.
- Put the earlier date first in every function above: `AGE_IN_YEARS(BirthDate, NOW())`.
- Write the interval as a quoted constant. The `SQL_TSI_` prefix is optional: `'DAY'` works.
- Pick by unit:
  - Years: `AGE_IN_YEARS(a, b)`
  - Months: `AGE_IN_MONTHS(a, b)`
  - Quarters: `AGE_IN_MONTHS(a, b) / 3`
  - Weeks: `AGE_IN_DAYS(a, b) / 7`
  - Days: `AGE_IN_DAYS(a, b)`. Never use `TIMESTAMPDIFF` for days.
  - Hours, minutes, or seconds: `TIMESTAMPDIFF`
- `curdate()`, `curtime()`, `now()`, `CURRENT_DATE`, `CURRENT_TIME`, `CURRENT_TIMESTAMP`
- `year(d)`, `quarter(d)`, `month(d)`, `monthname(d)`, `week(d)`, `dayofyear(d)`, `dayofmonth(d)`, `dayofweek(d)`,
  `hour(t)`, `minute(t)`, `second(t)`
- `timestampadd(interval, n, ts)`: interval is a quoted constant and one of `'SQL_TSI_FRAC_SECOND'`, `'SQL_TSI_SECOND'`,
  `'SQL_TSI_MINUTE'`, `'SQL_TSI_HOUR'`, `'SQL_TSI_DAY'`, `'SQL_TSI_WEEK'`, `'SQL_TSI_MONTH'`, `'SQL_TSI_QUARTER'`,
  `'SQL_TSI_YEAR'`.

Conditional:
- `coalesce(v1, ..., vN)`: returns the first argument that is not NULL. All arguments must have the same type: use
  `COALESCE(IntCol, 0)`, not `COALESCE(IntCol, 'n/a')`.
- `nullif(a, b)`: returns NULL if `a` equals `b`, otherwise `a`.
- `ifnull(test, default)`: returns `default` if `test` is NULL, otherwise `test`.
- `greatest(a, b, ...)`, `least(a, b, ...)`: return the largest or smallest argument.
- `isnumeric(expr)`: returns 1 if `expr` is a number, 0 otherwise.
- `CASE [operand] WHEN ... THEN ... [ELSE ...] END`: returns the result of the first matching WHEN.
- `ifdefined(Column)`: returns NULL if the column does not exist, otherwise the column's value. Takes exactly one
  argument, a column name. `IFDEFINED(a + b)` and `IFDEFINED(A, B)` are invalid.

Comparing values that may be NULL:
- `a = b`: NULL if either side is NULL.
- `isequal(a, b)`: TRUE if both are NULL, NULL if only one is NULL.
- `a IS NOT DISTINCT FROM b`: never NULL. TRUE if both are NULL, FALSE if only one is NULL.
- To let a NULL match any value, use `COALESCE(a = b, TRUE)`. `IS NOT DISTINCT FROM` cannot do this.

PostgreSQL only:
`ascii(s)`, `btrim(s[, chars])`, `char_length(s)`, `chr(code)`, `concat_ws(sep, v1, ...)` (skips NULLs),
`initcap(s)`, `lpad(s, n[, fill])`, `rpad(s, n[, fill])`, `md5(s)`,
`regexp_replace(s, pattern, replacement[, flags])`, `replace(s, match, replacement)`,
`similar_to(s, pattern[, escape])`, `split_part(s, delim, n)`, `strpos(s, sub)`, `substr(s, from[, count])`,
`translate(s, from, to)`, `to_char(v, format)`, `to_date(text, format)`, `to_timestamp(text, format)`,
`to_number(text, format)`

### CAST

`CAST(expression AS type)`. Validation does not reliably check argument types, so a type error may only appear when the
value is computed. Cast proactively, for example a date stored as VARCHAR before passing it to a date function.

- Types: `TINYINT, SMALLINT, INTEGER, BIGINT, REAL, FLOAT, DOUBLE, NUMERIC, DECIMAL, BOOLEAN, BIT, CHAR, VARCHAR,
  LONGVARCHAR, DATE, TIME, TIMESTAMP, GUID`. Precision and scale on NUMERIC or DECIMAL: `CAST(n AS NUMERIC(10,2))`.
- Common patterns: `CAST(stringCol AS TIMESTAMP)`, `CAST(numericCol AS VARCHAR) || ' units'`.

### Common Errors

- If the error includes a suggestion, apply it directly.
- "Unknown method X": the function does not exist in LabKey SQL. Use one from the function lists above.
- "Syntax error near" a column name such as `Count` or `User`: the name is a reserved word. Double-quote it.
