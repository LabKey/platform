### **LabKey SQL Documentation**

LabKey SQL is a unique SQL dialect that extends standard SQL functionality with features tailored for the LabKey Server platform, particularly for scientific data management.
LabKey SQL only implements data read operations.  It does not support INSERT/UPDATE/DELETE, nor does it support creating or altering tables.

-----

### **1. Lookups and Joins**

LabKey SQL simplifies data joining by providing an intuitive **lookup syntax** that often eliminates the need for explicit `JOIN` statements.

* **Syntax:**
  `SELECT parent_table.lookup_column.target_column FROM parent_table`
* **Functionality:**
  This syntax allows you to access columns from a foreign table by following the lookup relationship with dot notation. This is a powerful feature for simplifying queries that involve related data.
* **Examples:**
    * **Simple Lookup:** To retrieve a participant's gender from the `Demographics` table using a lookup from the `PhysicalExam` table:
      ```sql
      SELECT PhysicalExam.ParticipantId, PhysicalExam.weight_kg, Demographics.Gender, Demographics.Height
      FROM PhysicalExam
      ```
    * **Joining Across Folders:** To join data from a `Demographics` table in one folder with a `Languages` list in a different folder:
      ```sql
      JOIN "/Other/Folder".lists.Languages ON Demographics.Language=Languages.Language
      ```

-----

### **2. Calculated Columns**

LabKey SQL allows you to create virtual columns within a query by using SQL expressions. These calculated columns are not stored in the database but are computed on the fly.

* **Syntax:**
  `SELECT expression AS column_name FROM table`
* **Functionality:**
  The syntax involves performing a calculation and then aliasing the result with a new column name using the `as` keyword.
* **Examples:**
    * **Pulse Pressure:** To calculate pulse pressure from systolic and diastolic blood pressure values:
      ```sql
      PhysicalExam.systolicBP-PhysicalExam.diastolicBP as PulsePressure
      ```
    * **BMI (Body Mass Index):** A more complex example that uses an intermediate query to calculate BMI from height and weight data:
      ```sql
      ROUND(weight_kg / (height_m * height_m), 2) AS BMI
      ```

-----

### **3. Pivoting**

A `PIVOT` query helps you summarize and re-visualize data by transforming rows into columns.

* **Syntax for PIVOT...BY Query:**
  A pivot query is a `SELECT` statement specifying how to pivot and group columns. The basic syntax is `PIVOT [aggregating_column] BY [pivoting_column]`.
* **PIVOT...BY...IN Syntax:**
  You can use an `IN` clause to specify a fixed set of column names to pivot. This is more efficient.
  ```sql
  PIVOT new_column_name BY pivoting_column IN ('value1', 'value2')
  ```
  Note that pivot column names are case-sensitive. You may need to use `LOWER()` or `UPPER()` in your query to work around this issue.
  * **Pivoting by Two Columns:**
    Two levels of `PIVOT` are not directly supported. However, you can achieve a similar result by concatenating the two values together and pivoting on that "calculated" column.
    ```sql
    SELECT
      Run.SampleCondition || ' ' || PeakLabel AS ConditionPeak,
      AVG(Data.PercTimeCorrArea) AS AvgPercTimeCorrArea
    FROM Data
    GROUP BY Run.SampleCondition || ' ' || PeakLabel
    PIVOT AvgPercTimeCorrArea BY ConditionPeak
    ```

-----

### **4. Cross-Folder Queries**

You can write queries that access data from different folders within the LabKey Server instance, allowing for data integration across projects.

* **Syntax:**
  `SELECT * FROM Project."folder_path".schema.table`
* **Functionality:**
  The folder path is a dot-delimited string that specifies the location of the table, including the project name. The user must have "Reader" permissions in each folder referenced in the query.
* **Example:**
  ```sql
  SELECT
  p.ParticipantID,
  ROUND(AVG(p.Temp_C), 1) AS AverageTemp
  FROM Project."Tutorials/Demo/".study."Physical Exam" p
  GROUP BY p.ParticipantID
  ```

-----

### **5. Parameterized Queries**

LabKey SQL supports **parameterized queries** to improve security and reusability.

* **Syntax:**
  `PARAMETERS(param1 type, param2 type DEFAULT value) SELECT * FROM table WHERE column = param1`
* **Functionality:**
  The `PARAMETERS` keyword declares parameters that can be passed into the query.  If a DEFAULT is
  not specified, the value will default to NULL.
* **Example:** A query with two parameters, `MinTemp` and `MinWeight`:
  ```sql
  PARAMETERS(MinTemp double, MinWeight double DEFAULT 0.0)
  SELECT
    ParticipantID,
    temperature_C,
    weight_kg
  FROM PhysicalExam
  WHERE temperature_C >= MinTemp AND weight_kg >= MinWeight
  ```

-----

### **6. Metadata Annotations**

LabKey SQL allows you to directly annotate your SQL statements to override how column metadata is displayed in the LabKey interface.

* **Syntax:**
  `SELECT column_name @annotation FROM table`
* **Functionality:**
  Annotations control the display of a column without changing the underlying data.
* **Examples:**
    * **Hiding a Column:**
      ```sql
      SELECT ratio @hidden, log(ratio) as log_ratio
      ```
    * **Setting a Title and Format:**
      ```sql
      SELECT 10/7.0 AS Num @title='Calculated Number' @Format='0.00'
      ```

-----

### **7. Container Filters**

In addition to targeting a container by its path, LabKey SQL supports container filters to alter the scope
of a query. Annotate tables in the FROM clause with an optional container filter. Syntax:

SELECT * FROM Issues [ContainerFilter='CurrentAndSubfolders'] alias

Possible values include:
- AllFolders
- AllInProject
- AllInProjectPlusShared
- Current
- CurrentAndFirstChildren
- CurrentAndParents
- CurrentAndSubfolders
- CurrentAndSubfoldersPlusShared
- CurrentPlusProject
- CurrentPlusProjectAndShared.

### **8. Available Methods**

Here is a summary of the available functions and methods in LabKey SQL.

#### **Mathematical Functions**

* `abs(value)`: Returns the absolute value.
* `acos(value)`: Returns the arc cosine.
* `asin(value)`: Returns the arc sine.
* `atan(value)`: Returns the arc tangent.
* `atan2(value1, value2)`: Returns the arctangent of the quotient.
* `ceiling(value)`: Rounds the value up.
* `cos(radians)`: Returns the cosine.
* `cot(radians)`: Returns the cotangent.
* `degrees(radians)`: Returns degrees.
* `exp(n)`: Returns Euler's number 'e' raised to the nth power.
* `floor(value)`: Rounds down.
* `log(n)`: Returns the natural logarithm.
* `log10(n)`: Returns the base 10 logarithm.
* `mod(dividend, divider)`: Returns the remainder.
* `pi()`: Returns the value of pi.
* `power(base, exponent)`: Returns the base raised to the power of the exponent.
* `radians(degrees)`: Returns the radians.
* `rand()`, `rand(seed)`: Returns a random number.
* `round(value, precision)`: Rounds to the specified decimal places.
* `sign(value)`: Returns the sign of the value.
* `sin(value)`: Returns the sine.
* `sqrt(value)`: Returns the square root.
* `tan(value)`: Returns the tangent.
* `truncate(numeric value, precision)`: Truncates the numeric value.

#### **String Functions**

* `concat(value1, value2)`: Concatenates two values.
* `lcase(string)`, `lower(string)`: Converts to lower case.
* `left(string, integer)`: Returns the left side of the string.
* `length(string)`: Returns the length.
* `locate(substring, string, [startIndex])`: Returns the location of a substring.
* `ltrim(string)`: Trims white space from the left.
* `repeat(string, count)`: Repeats the string.
* `rtrim(string)`: Trims white space from the right.
* `startswith(string, prefix)`: Tests if a string starts with a prefix.
* `substring(string, start, length)`: Returns a portion of the string.
* `ucase(string)`, `upper(string)`: Converts to upper case.

#### **Date and Time Functions**

* `age(date1, date2, [interval])`: Supplies the difference in age.
* `age_in_days(date1, date2)`: Returns age in days.
* `age_in_months(date1, date2)`: Returns age in months.
* `age_in_years(date1, date2)`: Returns age in years.
* `curdate()`, `curtime()`: Returns the current date/time.
* `dayofmonth(date)`: Returns the day of the month.
* `dayofweek(date)`: Returns the day of the week.
* `dayofyear(date)`: Returns the day of the year.
* `hour(time)`, `minute(time)`, `second(time)`: Return time components.
* `month(date)`, `monthname(date)`: Return month values.
* `now()`: Returns the system date and time.
* `quarter(date)`: Returns the yearly quarter.
* `timestampadd(interval, number, timestamp)`: Adds an interval.
* `timestampdiff(interval, ts1, ts2)`: Finds the difference between timestamps.
* `week(date)`, `year(date)`: Return week and year values.

#### **Conditional and Utility Functions**

* `coalesce(v1,...,vN)`: Returns the first non-null value.
* `greatest(a, b, c, ...)`: Returns the greatest value.
* `ifdefined(column_name)`: References columns that may not exist.
* `ifnull(testValue, defaultValue)`: Returns a default value if the test value is null.
* `isequal(a,b)`: Returns true if `a` equals `b` or if both are `NULL`.
* `least(a, b, c, ...)`: Returns the smallest value.

#### **LabKey SQL Extensions**

* `contextPath()`, `folderName()`, `folderPath()`: Return path information.
* `ismemberof(groupid)`: Checks if a user is a member of a group.
* `javaConstant(fieldName)`: Provides access to Java static final variables.
* `moduleProperty(module name, property name)`: Returns a module property.
* `overlaps(START1, END1, START2, END2)`: Tests for overlapping time intervals (PostgreSQL only).
* `userid()`, `username()`: Return user information.
* `version()`: Returns the current schema version.

-----

### **9. JSON and JSONB Operators and Functions (PostgreSQL Only)**

LabKey SQL supports PostgreSQL JSON and JSONB operators and functions for working with JSON data stored in columns. These are **not available on MS SQL Server**. LabKey SQL does not natively understand arrays, but functions that expect them may still work. See the [PostgreSQL docs](https://www.postgresql.org/docs/14/functions-json.html) for detailed usage.

#### **Operators via `json_op`**

Native PostgreSQL operator syntax (`->`, `->>`, etc.) cannot be used directly in LabKey SQL. Instead, use the `json_op` pass-through function with three arguments: the left operand, the operator as a string, and the right operand.

* **Supported operators:** `->`, `->>`, `#>`, `#>>`, `@>`, `<@`, `?`, `?|`, `?&`, `||`, `-`, `#-`
* **Syntax:** `json_op(left_operand, 'operator', right_operand)`
* **Examples:**
    * **Extract by key (as JSON):** Get a nested value from a JSONB column:
      ```sql
      SELECT json_op(metadata, '->', 'name') AS name_json FROM samples
      ```
    * **Extract by key (as text):** Get the text value:
      ```sql
      SELECT json_op(metadata, '->>', 'name') AS name_text FROM samples
      ```
    * **Containment check:** Filter rows where JSONB contains a given structure:
      ```sql
      SELECT * FROM samples WHERE json_op(metadata, '@>', parse_jsonb('{"status":"active"}'))
      ```
    * **Key existence check:**
      ```sql
      SELECT * FROM samples WHERE json_op(metadata, '?', 'name')
      ```

#### **Conversion / Parsing Functions**

* `parse_json(text)`, `parse_jsonb(text)`: Cast a text value to JSON or JSONB. Use instead of `::jsonb` or `CAST(... AS JSONB)`.
  ```sql
  SELECT parse_jsonb('{"a":1, "b":null}')
  ```
* `to_json(value)`, `to_jsonb(value)`: Convert a value to JSON/JSONB. Text values become a single JSON string.
* `array_to_json(array)`: Convert an array to JSON.
* `row_to_json(value)`: Convert a scalar row to JSON. **Note:** Does not support converting an entire table to JSON; use `to_jsonb()` instead.

#### **Builder Functions**

* `json_build_array(...)`, `jsonb_build_array(...)`: Build a JSON array from arguments.
  ```sql
  SELECT jsonb_build_array(1, 'two', 3.0)
  ```
* `json_build_object(...)`, `jsonb_build_object(...)`: Build a JSON object from key/value arguments.
  ```sql
  SELECT jsonb_build_object('name', sample_name, 'type', sample_type) FROM samples
  ```
* `json_object(text_array)`, `jsonb_object(text_array)`: Build a JSON object from a text array.

#### **Query and Extraction Functions**

* `json_array_length(json)`, `jsonb_array_length(jsonb)`: Return the length of the outermost JSON array.
* `json_each(json)`, `jsonb_each(jsonb)`: Expand the outermost JSON object into key/value pairs. **Note:** Only scalar function usage is supported, not the table-returning version.
  ```sql
  SELECT json_each('{"a":"foo", "b":"bar"}') AS Value
  ```
* `json_each_text(json)`, `jsonb_each_text(jsonb)`: Like `json_each` but values are returned as text. Only scalar usage supported.
* `json_extract_path(json, ...)`, `jsonb_extract_path(jsonb, ...)`: Return the JSON value at the given path.
  ```sql
  SELECT jsonb_extract_path(metadata, 'address', 'city') FROM samples
  ```
* `json_extract_path_text(json, ...)`, `jsonb_extract_path_text(jsonb, ...)`: Return the value at the given path as text.
* `json_object_keys(json)`, `jsonb_object_keys(jsonb)`: Return the keys of the outermost JSON object.
* `json_array_elements(json)`, `jsonb_array_elements(jsonb)`: Expand a JSON array into a set of values.
* `json_array_elements_text(json)`, `jsonb_array_elements_text(jsonb)`: Expand a JSON array into a set of text values.

#### **Type Inspection and Cleanup Functions**

* `json_typeof(json)`, `jsonb_typeof(jsonb)`: Return the type of the outermost JSON value (e.g., `"object"`, `"array"`, `"string"`, `"number"`).
* `json_strip_nulls(json)`, `jsonb_strip_nulls(jsonb)`: Remove all null-valued keys from a JSON object.

#### **Modification Functions**

* `jsonb_insert(jsonb, path, new_value)`: Insert a value at a given path within a JSONB object.
* `jsonb_pretty(jsonb)`: Format a JSONB value as indented text.
* `jsonb_set(jsonb, path, new_value)`: Set the value at a given path. Strict: returns NULL on NULL input.
* `jsonb_set_lax(jsonb, path, new_value, null_behavior)`: Like `jsonb_set` but not strict. The `null_behavior` argument must be one of: `'raise_exception'`, `'use_json_null'`, `'delete_key'`, or `'return_target'`.

#### **Path Query Functions**

* `jsonb_path_exists(jsonb, path)`, `jsonb_path_exists_tz(...)`: Check whether the JSON path returns any item. The `_tz` variant is timezone-aware.
* `jsonb_path_match(jsonb, path)`, `jsonb_path_match_tz(...)`: Return the result of a JSON path predicate check.
* `jsonb_path_query(jsonb, path)`, `jsonb_path_query_tz(...)`: Return all items matched by the JSON path.
* `jsonb_path_query_array(jsonb, path)`, `jsonb_path_query_array_tz(...)`: Return matched items as an array.
* `jsonb_path_query_first(jsonb, path)`, `jsonb_path_query_first_tz(...)`: Return the first matched item.

#### **Not Supported**

The following functions are **not supported** in LabKey SQL:
`json_populate_record`, `jsonb_populate_record`, `json_populate_recordset`, `jsonb_populate_recordset`, `json_to_record`, `jsonb_to_record`, `json_to_recordset`, `jsonb_to_recordset`.

#### **Quick Reference for Writing LabKey SQL with JSON**

When writing LabKey SQL queries that work with JSON columns:

1. **Always use `json_op()` for operators** — never use raw PostgreSQL operator syntax like `->` or `->>`. Wrap them: `json_op(col, '->>', 'key')`.
2. **Use `parse_jsonb()` to create JSONB literals** — there is no `::jsonb` cast in LabKey SQL. Write `parse_jsonb('{"key":"value"}')`.
3. **Use `jsonb_extract_path_text()` for nested field access** — this is often the clearest way to extract a deeply nested text value: `jsonb_extract_path_text(col, 'level1', 'level2', 'field')`.
4. **Use `jsonb_build_object()` to construct JSON** — for building JSON from column values: `jsonb_build_object('id', rowid, 'name', label)`.
5. **Check database type first** — these functions only work on PostgreSQL. If the target server may use MS SQL Server, do not use them.
6. **The `validateSQL` MCP tool can verify syntax** — use it to check JSON function calls before the user saves a query.