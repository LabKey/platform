### **LabKey SQL Documentation**

LabKey SQL is a SQL dialect that extends standard SQL with features tailored for the LabKey Server platform. Queries are checked against the user's permissions and cross-compiled to the underlying database (PostgreSQL, or MS SQL Server for some premium installations).

Core facts:
* **SELECT only.** No INSERT/UPDATE/DELETE, no DDL. A statement is: `[PARAMETERS(...)] [WITH ...] SELECT ... [set ops] [ORDER BY ...] [LIMIT n]`.
* Keywords, function names, schema/table/column names are **case-insensitive**.
* Comments: `-- line` and `/* block */`.
* If a `validateSQL` tool is available, use it to check syntax before saving or executing a query. Note that parse-time errors are descriptive, but type errors and dialect issues may only surface at execution time as an unhelpful generic message — apply the rules below proactively.

-----

### **1. NOT SUPPORTED — Write This Instead**

LabKey SQL rejects many constructs that are valid in PostgreSQL/ANSI SQL. **Check generated SQL against this table first.**

| Do NOT write | Why | Write instead |
| --- | --- | --- |
| `ROW_NUMBER() OVER (...)`, `RANK`, `LAG`, `SUM(x) OVER (...)` | No window functions, no `OVER` | Self-join or correlated subquery |
| `LIMIT 10 OFFSET 20`, `FETCH FIRST n ROWS`, `TOP n` | Only `LIMIT <integer literal>` exists; no OFFSET | `LIMIT 10`; page via client API `maxRows`/`offset` |
| `GROUP BY 1` | Ordinals/constants rejected in GROUP BY (error: "Expression in Group By clause must not be a constant") | Repeat the expression: `GROUP BY YEAR(Date)`. (`ORDER BY 1` **is** allowed.) |
| `EXTRACT(YEAR FROM d)` | Not supported | `YEAR(d)`, `MONTH(d)`, `DAYOFMONTH(d)`, `HOUR(d)`, … |
| `col::integer` | No `::` cast | `CAST(col AS INTEGER)` |
| `SUM(DISTINCT x)`, `AVG(DISTINCT x)` | `DISTINCT` only inside `COUNT()` and `GROUP_CONCAT()` | Aggregate over a `SELECT DISTINCT` subquery |
| `x ILIKE 'a%'`, `x ~ 'regex'`, `x SIMILAR TO p` | Operators not supported | `LOWER(x) LIKE 'a%'`; PostgreSQL only: `similar_to(x, pattern[, escape])` |
| `a IS DISTINCT FROM b` | Operator form not supported | `is_distinct_from(a, b)` / `is_not_distinct_from(a, b)` (both databases); `isequal(a, b)` = null-safe equals |
| `ORDER BY x NULLS LAST` | Not supported | `ORDER BY x IS NULL, x` |
| `d + INTERVAL '1 day'` | No INTERVAL literals | `TIMESTAMPADD('SQL_TSI_DAY', 1, d)` |
| `CONCAT(a, b, c)` | `CONCAT` takes exactly 2 arguments | `a \|\| b \|\| c` (note: `\|\|` yields NULL if any operand is NULL — wrap with `COALESCE`) |
| `POSITION(a IN b)` | Unknown method | `LOCATE(a, b[, startIndex])` |
| `TRIM(BOTH ' ' FROM x)` | Not supported | `LTRIM(RTRIM(x))`; PostgreSQL only: `btrim(x)` |
| `COUNT(*) FILTER (WHERE c)` | No FILTER clause | `SUM(CASE WHEN c THEN 1 ELSE 0 END)` |
| `JOIN b USING (id)`, `NATURAL JOIN` | Every non-CROSS join requires `ON` | `JOIN b ON a.id = b.id` |
| `SUM(a \|\| b)`, `MIN(a & b)` | Aggregate arguments cannot directly contain `\|\|` or bitwise operators | Add parentheses: `SUM((a \|\| b))` |
| `DATE '2001-02-03'` | No typed literals | `{d '2001-02-03'}` or `CAST('2001-02-03' AS DATE)` |
| `{d'2001-02-03'}` (no space) | Parse error — the space after `{d`/`{ts` is part of the token | `{d '2001-02-03'}`, `{ts '2001-02-03 04:05:06'}` |
| `WITH cte (col1, col2) AS (...)` | No CTE column lists | Alias inside: `WITH cte AS (SELECT x AS col1 ...)` |
| `a < b < c` | Comparisons don't chain | `a < b AND b < c` |
| `PARAMETERS (D DATE)` | DATE/TIME/BOOLEAN not parameter types | Use `TIMESTAMP` (or `BIT` for boolean) |
| `GROUPING SETS / ROLLUP / CUBE / LATERAL / TABLESAMPLE` | Not supported | Restructure (UNION of grouped queries, etc.) |

-----

### **2. Identifiers, Literals, and Reserved Words**

* **Identifiers**: double-quote names containing spaces/special characters or matching reserved words: `"Physical Exam"`. Escape an embedded `"` by doubling it.
* **String literals**: single quotes; escape `'` by doubling: `'Jim''s Item'`. No backslash escapes.
* **Date/time literals**: `{d '2001-02-03'}` and `{ts '2001-02-03 04:05:06'}` — JDBC escape syntax, **space after `{d`/`{ts` required**.
* **Booleans**: `TRUE`, `FALSE`. Special doubles: `CAST('Infinity' AS DOUBLE)`, `CAST('-Infinity' AS DOUBLE)`, `CAST('NaN' AS DOUBLE)`.
* **Reserved words** — these cannot be used as bare identifiers/aliases; double-quote them (`SELECT COUNT(*) AS "Count"`):

  `all, any, and, as, asc, avg, between, both, case, class, count, current_date, current_time, current_timestamp, delete, desc, distinct, elements, else, empty, end, escape, except, exists, false, fetch, from, full, group, having, in, indices, inner, insert, intersect, into, is, join, leading, left, like, limit, max, member, min, new, not, null, of, on, or, order, outer, right, select, set, some, stddev, sum, trailing, then, true, union, update, user, versioned, when, where`

* Do not end statements with `;` (produces a warning) and do not submit multiple statements.

-----

### **3. Query Structure and Clause Rules**

* **SELECT list**: `expr [AS] alias`. Alias every expression column — unaliased expressions get auto-names (`Expression1`, …) plus a warning. Duplicate output names are an error (`Duplicate column 'x'`). `*` and `table.*` cannot be aliased. A scalar subquery must return exactly one column. `SELECT 1 AS x` with no FROM is allowed.
* **FROM/JOIN**: `INNER | LEFT | RIGHT | FULL [OUTER] JOIN ... ON cond` and `CROSS JOIN` (no ON). Comma joins and nested parenthesized joins are supported. Subqueries in FROM should be aliased (warning otherwise). An unqualified column name found in two FROM tables is an error (`Ambiguous field`).
* **WHERE**: standard comparisons `= <> != < <= > >=`, `[NOT] IN (list | subquery)`, `[NOT] BETWEEN a AND b`, `[NOT] LIKE p [ESCAPE e]`, `IS [NOT] NULL`, `EXISTS (subquery)`, `ANY/SOME/ALL (subquery)`.
* **GROUP BY**: expressions only — no ordinals, no constants. **HAVING requires GROUP BY or an aggregate in the SELECT list** (an aggregate appearing only in HAVING is rejected).
* **ORDER BY**: column names, aliases, expressions, or ordinals (`ORDER BY 1`); `ASC`/`DESC`. **When a query is used as a subquery or saved and wrapped by the server (common), its ORDER BY is IGNORED unless LIMIT is also present.** Prefer sorting via the client API / grid view; if SQL sorting is needed, add `LIMIT`. To sort by an expression, put it in the SELECT list (optionally `@hidden`) and sort by its alias.
* **LIMIT**: `LIMIT <integer literal>` only, placed after ORDER BY. Applies to the whole UNION when used at the end of one.
* **Set operations**: `UNION`, `UNION ALL`, `INTERSECT`, `EXCEPT`. All terms need the same column count and compatible types; result column names come from the first term. A trailing ORDER BY may reference only output column names or ordinals. Parenthesized sub-statements are allowed.
* **Division**: `/` warns unless the divisor is guarded — write `dividend / NULLIF(divisor, 0)`. Integer ÷ integer truncates; cast one operand: `CAST(a AS DOUBLE) / b`.

-----

### **4. Lookups (Foreign-Key Dot Traversal) and Joins**

Lookup columns (foreign keys) can be traversed with dot notation instead of writing a JOIN. Follow the FK column with the target column name:

```sql
-- CreatedBy is a lookup to the Users table:
SELECT c.Name, c.CreatedBy.DisplayName AS Creator
FROM core.Containers c
```

Multi-hop traversal works (`Issues.AssignedTo.DisplayName`). Only reference tables in the FROM clause or reachable through lookups — referencing another table's columns directly is an error.

**Study datasets**: every dataset has a `Datasets` column joining to other datasets by subject(/visit):

```sql
SELECT a.MouseId, a.Datasets."DEM-1".DEMsex
FROM "APX-1" a
```

**Joining across folders** (see also section 12):

```sql
SELECT d.Language, l.TranslatorName
FROM Demographics d JOIN "/Other/Folder".lists.Languages l ON d.Language = l.Language
```

`IFDEFINED(ColumnName)` references a column that may not exist — it evaluates as NULL instead of failing, useful over PIVOT results or variable assay schemas.

-----

### **5. Aggregate Functions**

Available on both databases:

* `COUNT(*)`, `COUNT(expr)`, `COUNT(DISTINCT expr)`
* `SUM(expr)`, `MIN(expr)`, `MAX(expr)`, `AVG(expr)`
* `GROUP_CONCAT([DISTINCT] expr [, separator])`: comma-separated values of the group; e.g. `GROUP_CONCAT(DISTINCT Category, ';')`. (On MS SQL Server requires a separately-installed function and cannot be used in a sub-select.)
* `STDDEV(expr)`, `STDDEV_POP(expr)`, `VARIANCE(expr)`, `VAR_POP(expr)`, `STDERR(expr)`
* `MEDIAN(expr)`

PostgreSQL only: `BOOL_AND, BOOL_OR, EVERY, BIT_AND, BIT_OR, MODE, STDDEV_SAMP, VAR_SAMP` and two-argument regression aggregates `CORR(Y,X), COVAR_POP, COVAR_SAMP, REGR_AVGX, REGR_AVGY, REGR_COUNT, REGR_INTERCEPT, REGR_R2, REGR_SLOPE, REGR_SXX, REGR_SXY, REGR_SYY`.

Rules: `DISTINCT` is allowed only in `COUNT` and `GROUP_CONCAT`. No `FILTER` clause, no `OVER`, no `ARRAY_AGG`. To nest `||` or bitwise operators inside an aggregate, parenthesize: `MIN((a || b))`.

-----

### **6. Scalar Functions (Both Databases)**

#### Mathematical
`abs(v)`, `acos(v)`, `asin(v)`, `atan(v)`, `atan2(v1,v2)`, `ceiling(v)`, `cos(r)`, `cot(r)`, `degrees(r)`, `exp(n)`, `floor(v)`, `log(n)` (natural), `log10(n)`, `mod(dividend, divider)`, `pi()`, `power(base, exp)`, `radians(d)`, `rand([seed])`, `round(v[, precision])`, `sign(v)`, `sin(v)`, `sqrt(v)`, `tan(v)`, `truncate(v, precision)` (may require `CAST(v AS NUMERIC)`)

#### String
`concat(a, b)` (exactly 2 args; prefer `||`), `lcase(s)`/`lower(s)`, `ucase(s)`/`upper(s)`, `left(s, n)`, `right(s, n)`, `length(s)`, `locate(substr, s[, start])`, `ltrim(s)`, `rtrim(s)`, `repeat(s, count)`, `startswith(s, prefix)`, `substring(s, start[, length])` (1-based)

#### Date and Time
* `curdate()`, `curtime()`, `now()` — and the SQL-standard niladic keyword forms `CURRENT_DATE`, `CURRENT_TIME`, `CURRENT_TIMESTAMP` (**no parentheses** — `CURRENT_DATE()` is a syntax error, unlike the function forms)
* `year(d)`, `quarter(d)`, `month(d)`, `monthname(d)`, `week(d)`, `dayofyear(d)`, `dayofmonth(d)`, `dayofweek(d)`, `hour(t)`, `minute(t)`, `second(t)`
* `timestampadd(interval, n, ts)` — interval is a quoted constant, one of `'SQL_TSI_FRAC_SECOND'`, `'SQL_TSI_SECOND'`, `'SQL_TSI_MINUTE'`, `'SQL_TSI_HOUR'`, `'SQL_TSI_DAY'`, `'SQL_TSI_WEEK'`, `'SQL_TSI_MONTH'`, `'SQL_TSI_QUARTER'`, `'SQL_TSI_YEAR'` (the `SQL_TSI_` prefix may be omitted: `'DAY'`).
* `timestampdiff(interval, ts1, ts2)` — same constants, **but on PostgreSQL only `'SQL_TSI_SECOND'`, `'SQL_TSI_MINUTE'`, `'SQL_TSI_HOUR'`, `'SQL_TSI_DAY'` work**; YEAR/MONTH/WEEK/QUARTER fail at execution time. For those use:
* `age(d1, d2)` (years), `age(d1, d2, interval)` with `'SQL_TSI_DAY' | 'SQL_TSI_MONTH' | 'SQL_TSI_YEAR'`, `age_in_years(d1, d2)`, `age_in_months(d1, d2)`, `age_in_days(d1, d2)`

#### Conditional and Utility
`coalesce(v1, ..., vN)`, `nullif(a, b)` (NULL if a=b, else a — use for divide-by-zero guards), `ifnull(test, default)`, `isequal(a, b)` (true when equal or both NULL), `is_distinct_from(a, b)`, `is_not_distinct_from(a, b)`, `greatest(a, b, ...)`, `least(a, b, ...)`, `isnumeric(expr)`, `ifdefined(col)`, `CASE [operand] WHEN ... THEN ... [ELSE ...] END`

#### LabKey Extensions
`userid()`, `username()`, `ismemberof(groupid)`, `contextPath()`, `folderName()`, `folderPath()`, `moduleProperty('module','property')`, `javaConstant('class.FIELD')`, `version()`, `overlaps(start1, end1, start2, end2)` (PostgreSQL only)

-----

### **7. PostgreSQL-Only Scalar Functions**

Only when the server runs PostgreSQL (the common case). Passed through natively:

`ascii`, `btrim(s[,chars])`, `char_length`, `character_length`, `chr(code)`, `concat_ws(sep, v1, ...)` (variadic, skips NULLs), `decode`, `encode`, `initcap`, `lpad(s,n[,fill])`, `rpad(s,n[,fill])`, `md5`, `octet_length`, `quote_ident`, `quote_literal`, `regexp_replace(s, pattern, replacement[, flags])`, `replace(s, match, replacement)`, `similar_to(s, pattern[, escape])`, `split_part(s, delim, n)`, `strpos(s, sub)`, `substr(s, from[, count])`, `to_ascii`, `to_hex`, `translate(s, from, to)`, `to_char(v, format)`, `to_date(text, format)`, `to_timestamp(text, format)`, `to_number(text, format)`, `string_to_array`, `unnest`

(MS SQL Server-only equivalents exist — `charindex`, `len`, `patindex`, `replicate`, `stuff`, etc. — only relevant for premium MSSQL deployments.)

-----

### **8. WITH (Common Table Expressions)**

```sql
WITH AllDemo AS (
  SELECT * FROM "/Studies/Study A/".study.Demographics
  UNION
  SELECT * FROM "/Studies/Study B/".study.Demographics
)
SELECT ParticipantId FROM AllDemo
```

Rules:
* `WITH name AS (SELECT ...)` — **no column list** after the name.
* Multiple CTEs allowed; each may reference earlier ones.
* Recursive CTEs are supported (no `RECURSIVE` keyword needed). The recursive form is `anchor-query UNION ALL recursive-query`; the **first (anchor) branch must not reference the CTE**, and the CTE may be referenced **only once** in the recursive branch.
* **Warning**: a non-terminating recursive CTE can hang the server (PostgreSQL raises no error) — always ensure termination.

```sql
PARAMETERS ( Source VARCHAR DEFAULT NULL )
WITH Derivations AS (
  SELECT Item, Parent FROM Items WHERE Parent = Source
  UNION ALL
  SELECT i.Item, i.Parent FROM Items i INNER JOIN Derivations p ON i.Parent = p.Item
)
SELECT * FROM Derivations
```

-----

### **9. VALUES (Constant Tables)**

```sql
SELECT t.column1 AS Id, t.column2 AS Name
FROM (VALUES (1, 'one'), (2, 'two'), (3, 'three')) AS t
```

* The table alias (`AS t`) is **required**; column names are always `column1`, `column2`, … and cannot be declared in the VALUES clause — rename them in the outer SELECT.
* Rows must have equal length and compatible types; only constants/parameters (no column references or subqueries).

-----

### **10. PIVOT**

A pivot query summarizes and rotates a **grouped** query: `PIVOT aggCol [, aggCol...] BY pivotColumn [IN (...)]`.

```sql
SELECT ParticipantId, Visit, AVG(Score) AS AvgScore
FROM Results
GROUP BY ParticipantId, Visit
PIVOT AvgScore BY Visit IN ('V1', 'V2', 'V3')
```

Hard rules (violations are errors):
* The query **must have a GROUP BY clause**, and the BY column's expression must appear in GROUP BY **exactly as written**.
* Each pivoted column (`AvgScore` above) must be an aggregate in the SELECT list.
* Any additional non-pivoted, non-grouping aggregate column may only use `SUM`, `MIN`, `MAX`, or `COUNT` — not AVG/GROUP_CONCAT/stddev.
* `IN (...)` takes constants with optional aliases (`IN ('a' AS ColA, 'b' ColB)`) **or a subselect** (`IN (SELECT DISTINCT ...)`). Omitting IN computes distinct values automatically, but **a parameterized query requires an explicit IN list**.
* Pivot output columns are named `value::aggAlias` (e.g. `V1::AvgScore`). Names are case-insensitive; duplicate pivot values differing only by case are an error — normalize with `LOWER()`/`UPPER()` in the query.
* Two-level pivots are not supported; concatenate the two values into one column and pivot on that.

-----

### **11. Parameterized Queries**

```sql
PARAMETERS (MinTemp DOUBLE, MinWeight DOUBLE DEFAULT 0.0)
SELECT ParticipantID, temperature_C, weight_kg
FROM PhysicalExam
WHERE temperature_C >= MinTemp AND weight_kg >= MinWeight
```

* Allowed types: `BIGINT, BIT, CHAR, DECIMAL, DOUBLE, FLOAT, INTEGER, LONGVARCHAR, NUMERIC, REAL, SMALLINT, TIMESTAMP, TINYINT, VARCHAR` (NUMERIC accepts precision/scale). **DATE, TIME, and BOOLEAN are not allowed** — use TIMESTAMP / BIT.
* `DEFAULT` value must be a constant; without DEFAULT the parameter is required (NULL when unset via the API).
* A parameter name shadows an unqualified column of the same name — qualify the column (`R.X`) to disambiguate.
* Values are passed via the client API (e.g. `query.param.MinTemp`).

-----

### **12. Cross-Folder Queries**

Prefix the schema with a quoted folder path (user needs Reader permission in each referenced folder):

```sql
SELECT p.ParticipantID, ROUND(AVG(p.Temp_C), 1) AS AverageTemp
FROM "/Tutorials/Demo".study."Physical Exam" p
GROUP BY p.ParticipantID
```

`Project` refers to the current project root: `Project."SubFolder".lists.MyList`.

-----

### **13. Container Filters**

Annotate a table in the FROM clause to broaden the query scope:

```sql
SELECT * FROM Issues [ContainerFilter='CurrentAndSubfolders'] i
```

Values: `AllFolders`, `AllInProject`, `AllInProjectPlusShared`, `Current`, `CurrentAndFirstChildren`, `CurrentAndParents`, `CurrentAndSubfolders`, `CurrentAndSubfoldersPlusShared`, `CurrentPlusProject`, `CurrentPlusProjectAndShared`.

-----

### **14. Metadata Annotations**

Column annotations after a select expression override display metadata:

```sql
SELECT ratio @hidden,
       10/7.0 AS Num @title='Calculated Number' @format='0.00'
```

Recognized: `@title`, `@format`, `@hidden`, `@concept`, `@nolookup`, `@preservetitle`. Value syntax: `@title='x'` or `@title('x')`.

-----

### **15. CAST**

`CAST(expression AS type)`. LabKey SQL does **not** reliably check argument types at parse time — type errors surface at execution as a generic error, so cast proactively (e.g., a date stored as VARCHAR passed to a date function).

* Target types: `TINYINT, SMALLINT, INTEGER, BIGINT, REAL, FLOAT, DOUBLE, NUMERIC, DECIMAL, BOOLEAN, BIT, CHAR, VARCHAR, LONGVARCHAR, DATE, TIME, TIMESTAMP, GUID, BINARY, VARBINARY, LONGVARBINARY`. Precision/scale on NUMERIC/DECIMAL: `CAST(n AS NUMERIC(10,2))`; length on CHAR/VARCHAR.
* Common patterns: `CAST(stringCol AS TIMESTAMP)`, `CAST(numericCol AS VARCHAR) || ' units'`, `CAST(numerator AS DOUBLE) / denominator` (avoid integer-division truncation).

-----

### **16. JSON and JSONB Operators and Functions (PostgreSQL Only)**

Not available on MS SQL Server. See the [PostgreSQL docs](https://www.postgresql.org/docs/14/functions-json.html) for semantics.

#### Operators via `json_op`

Native operator syntax (`->`, `->>`, etc.) cannot be used. Use the `json_op` pass-through function: `json_op(left_operand, 'operator', right_operand)`.

* Supported operators: `->`, `->>`, `#>`, `#>>`, `@>`, `<@`, `?`, `?|`, `?&`, `||`, `-`, `#-`
* Examples:
  ```sql
  SELECT json_op(metadata, '->>', 'name') AS name_text FROM samples
  SELECT * FROM samples WHERE json_op(metadata, '@>', parse_jsonb('{"status":"active"}'))
  SELECT * FROM samples WHERE json_op(metadata, '?', 'name')
  ```

#### Conversion / Parsing
* `parse_json(text)`, `parse_jsonb(text)`: cast text to JSON/JSONB (instead of `::jsonb` / `CAST(... AS JSONB)`).
* `to_json(value)`, `to_jsonb(value)`; `array_to_json(array)`; `row_to_json(value)` (scalar rows only — use `to_jsonb()` for whole-row).

#### Builders
* `json_build_array(...)`, `jsonb_build_array(...)`; `json_build_object(k1, v1, ...)`, `jsonb_build_object(...)`; `json_object(text_array)`, `jsonb_object(text_array)`.

#### Query and Extraction
* `json_array_length`, `jsonb_array_length`; `json_extract_path(json, ...)`, `jsonb_extract_path(...)`; `json_extract_path_text(...)`, `jsonb_extract_path_text(...)` (clearest for nested text values); `json_object_keys`, `jsonb_object_keys`; `json_array_elements[_text]`, `jsonb_array_elements[_text]`; `json_each`, `jsonb_each`, `json_each_text`, `jsonb_each_text` (**scalar usage only** — not usable as table sources in FROM).

#### Type Inspection, Cleanup, Modification
* `json_typeof`, `jsonb_typeof`; `json_strip_nulls`, `jsonb_strip_nulls`; `jsonb_insert(jsonb, path, val)`; `jsonb_pretty(jsonb)`; `jsonb_set(jsonb, path, val)` (strict — NULL in, NULL out); `jsonb_set_lax(jsonb, path, val, null_behavior)` where null_behavior ∈ `'raise_exception' | 'use_json_null' | 'delete_key' | 'return_target'`.

#### Path Queries
* `jsonb_path_exists`, `jsonb_path_match`, `jsonb_path_query`, `jsonb_path_query_array`, `jsonb_path_query_first` — each with a timezone-aware `_tz` variant.

#### Not Supported
`json_populate_record`, `jsonb_populate_record`, `json_populate_recordset`, `jsonb_populate_recordset`, `json_to_record`, `jsonb_to_record`, `json_to_recordset`, `jsonb_to_recordset`.

-----

### **17. Array Functions (PostgreSQL Only)**

Not available on MS SQL Server.

#### Construction
* `ARRAY[elem, ...]` — builds an array. **No space between `ARRAY` and `[`.**
* `TEXTARRAY[elem, ...]` — like `ARRAY[]` but cast to `TEXT[]`. (One word — `TEXT_ARRAY` is wrong.)

#### Membership and Comparison
* `array_contains_element(array, elem)` — true if elem in array (`= ANY`). Negate with `NOT array_contains_element(...)`.
* `array_contains_all(a, b)` — every element of b is in a (`a @> b`).
* `array_contains_any(a, b)` — at least one element of b is in a (`a && b`).
* `array_contains_none(a, b)` — no element of b is in a.
* `array_is_same(a, b)` — unordered set equality.
* `array_is_empty(a)` — argument must be an ARRAY type (checked at parse time).

#### Not Supported
`array_agg`, `array_length`, `array_append`, `array_prepend`, `array_cat`, `array_remove`, `array_replace`, `array_position`, `array_to_string`, and subscript access (`arr[n]`).

-----

### **18. Database Portability Notes**

Most LabKey servers run PostgreSQL; some premium deployments run MS SQL Server. If the target may be MSSQL:

* Avoid PostgreSQL-only features: JSON functions (§16), arrays (§17), `similar_to`/`regexp_replace`/`to_char`-family (§7), `overlaps()`, and the PG-only aggregates (§5).
* `GROUP_CONCAT` on MSSQL requires a separately-installed function and cannot appear in a sub-select; when missing, it returns a literal `'<GROUP_CONCAT function not supported...>'` string instead of data.
* String comparison and grouping are case-sensitive on PostgreSQL, case-insensitive on MSSQL.
* `timestampdiff` interval limits differ (§6) — the `age*` functions are portable.

-----

### **19. Common Error Messages → Fixes**

Many parse errors now include an inline suggestion (e.g. `Syntax error near 'OFFSET' ... OFFSET is not supported. Use LIMIT n...` or `Unknown method TRIM. Use LTRIM(RTRIM(x)).`). When a suggestion is present, apply it directly — it is dialect-appropriate for the server that produced it.

| Error | Likely cause / fix |
| --- | --- |
| `Syntax error near 'OFFSET'` / near `'('` after OVER | Unsupported OFFSET / window function — see §1 |
| `Expression in Group By clause must not be a constant` | `GROUP BY 1` — repeat the expression instead |
| `Syntax error near 'DISTINCT'` | `SUM(DISTINCT ...)` or `IS DISTINCT FROM` — see §1 |
| `CURRENT_DATE/CURRENT_TIME/CURRENT_TIMESTAMP take no parentheses` | Drop the parens: bare `CURRENT_DATE`, not `CURRENT_DATE()` |
| `Unknown method X` | Function doesn't exist in LabKey SQL (check §6-§7) or is dialect-specific |
| `CONCAT function expects 2 arguments` | Use `\|\|` for 3+ values |
| `Syntax error near 'Count'` (or other keyword) | Reserved word used as alias — double-quote it |
| `HAVING requires an aggregate in the SELECT list` | Add the aggregate to the SELECT list or add GROUP BY |
| `Duplicate column 'x'` | Two select items produce the same name — alias one |
| `Ambiguous field: x` | Column exists in multiple FROM tables — qualify it |
| `VALUES expression requires an alias` | Add `AS t` after `(VALUES ...)` |
| `PIVOT queries must include a GROUP BY clause` | See §10 |
| `Could not find pivot column in group by list, expression must match exactly` | GROUP BY entry must textually match the PIVOT BY column's expression |
| `Parameter type is not supported: DATE` | Use TIMESTAMP (§11) |
| `The underlying database does not support nested ORDER BY unless LIMIT...` (warning) | ORDER BY is being dropped — add `LIMIT` or sort via the API |
| `ExecutingSelector; bad SQL grammar []` | Runtime (database-level) failure with no detail — usually a type mismatch (add CASTs) or a dialect-specific function limit (e.g. `timestampdiff` YEAR on PostgreSQL, `sum` over text) |

For any LabKey SQL topic not covered above, call `searchDocumentation`.
