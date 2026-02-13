### **LabKey SQL Documentation**

LabKey SQL is a unique SQL dialect that extends standard SQL functionality with features tailored for the LabKey Server platform, particularly for scientific data management.

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