# LabKey Data Analysis using R

This project is for performing data analysis against a LabKey Server instance using AI assistance.

**Connection defaults:** The LabKey server URL and API key can be inferred from `.mcp.json` in this directory. The `url` field (minus the `/mcp` path) provides the server endpoint, and the `apikey` header value provides the authentication token.

**Do not embed API keys in generated scripts.** Instead, ensure a `.netrc` file (Linux/Mac) or `_netrc` file (Windows) exists in the user's home directory with the server credentials. If the file does not exist, offer to create it using the API key from `.mcp.json`. The format is:

```
machine <hostname>
login apikey
password <api-key-from-mcp.json>
```

The `machine` value is the hostname only — no protocol (`https://`), no port, no path. For example, for `https://myserver.labkey.com:8443/labkey/mcp`, use `myserver.labkey.com`. On Linux/Mac, set permissions to 600 (`chmod 600 ~/.netrc`). On Windows, ensure the `_netrc` file is a plain file (not a "Text Document") and that a `HOME` environment variable points to the directory containing it.

When writing R scripts, read the server URL from `.mcp.json` (via `jsonlite::fromJSON`) to pre-populate `labkey.setDefaults(baseUrl=)`, but omit `apiKey` — Rlabkey reads `.netrc` automatically. Before running any script, confirm with the data analyst:
- Is this the correct server?
- Should the URL use `http://` or `https://`? (infer from the URL scheme in `.mcp.json`)
- What container path should be used? (use MCP `listContainers` to show available options)

Confirm all of these settings with the analyst before writing any script.

## Online Reference Material
https://www.labkey.org/Documentation/wiki-page.view?name=rAPI

## MCP Tools Available

A LabKey MCP server is configured (see `.mcp.json`). Use these tools to explore the server interactively:

| Tool | Purpose |
|---|---|
| `mcp__labkey__setContainer` | **Must be called first.** Sets the active container (project/folder) for subsequent calls. Path format: `MyProject/MyFolder` (no leading slash). |
| `mcp__labkey__whereAmIWhoAmITalkingTo` | Shows current user, server info, and active container. |
| `mcp__labkey__listContainers` | Lists all containers the user has read access to. |
| `mcp__labkey__listSchemas` | Lists all schemas in the active container. |
| `mcp__labkey__listTables` | Lists tables/queries within a schema. |
| `mcp__labkey__listColumns` | Shows column metadata (name, type, description) for a table. Also returns SQL source for saved queries. |
| `mcp__labkey__getSourceForSavedQuery` | Returns the SQL source of a saved query. |
| `mcp__labkey__validateSQL` | Validates LabKey SQL syntax without executing it. |

### MCP Workflow

1. Call `listContainers` to find available containers
2. Call `setContainer` with the desired container path
3. Use `listSchemas` -> `listTables` -> `listColumns` to explore the data model
4. Use `validateSQL` to check queries before running them
5. To actually retrieve data, write an R script using the `Rlabkey` package (see below)

## Rlabkey R Package

**Install:** `install.packages("Rlabkey")`
**Requires:** R 3.0+, LabKey Server v15.1+
**Dependencies:** httr, jsonlite, Rcpp
**CRAN:** https://cran.r-project.org/package=Rlabkey

### Connection Setup

Rlabkey functions take `baseUrl` and `folderPath` as explicit arguments on every call. Use `labkey.setDefaults()` to set the server URL:

**Preferred: `.netrc` authentication (no credentials in scripts)**

```r
library(Rlabkey)

# Set the server URL only — credentials are read from ~/.netrc automatically
labkey.setDefaults(baseUrl = "http://localhost:8080/")

# Then call functions without baseUrl:
rows <- labkey.selectRows(
    folderPath = "/home",
    schemaName = "lists",
    queryName  = "MyTable"
)

# Or pass baseUrl explicitly on every call:
rows <- labkey.selectRows(
    baseUrl    = "http://localhost:8080/",
    folderPath = "/home",
    schemaName = "lists",
    queryName  = "MyTable"
)
```

Rlabkey automatically reads credentials from `~/.netrc` (Linux/Mac) or `~/_netrc` (Windows). The `.netrc` entry should use `apikey` as the login and the API key as the password:

```
machine localhost
login apikey
password TheUniqueAPIKeyGeneratedForYou
```

The `machine` value must be the hostname only — no protocol, no port, no path. Set file permissions to 600 on Linux/Mac (`chmod 600 ~/.netrc`). Use `labkey.setCurlOptions(NETRC_FILE='/path/to/_netrc')` for a non-standard location.

Authentication options (in order of preference):
- **.netrc file** (recommended): Create `~/.netrc` with `machine`, `login apikey`, `password <key>` fields (chmod 600). No credentials appear in scripts.
- **API key in code** (avoid in generated scripts): Pass to `labkey.setDefaults(apiKey=)`. Only use this for quick interactive testing, not in saved scripts.
- **Email/password**: `labkey.setDefaults(email="user@example.com", password="pass")`
- **Session key**: Pass a session key via `labkey.setDefaults(apiKey=)`. Session keys tie R access to the user's browser session context (same authorizations, impersonation state, etc.).

To clear credentials: `labkey.setDefaults()` (called with no arguments resets all defaults).

### Data Retrieval APIs

#### labkey.selectRows -- Query a table/view

```r
rows <- labkey.selectRows(
    baseUrl       = NULL,           # str: server URL (e.g. "http://localhost:8080/")
    folderPath,                     # str: container path (e.g. "/home" or "/MyProject/MyFolder")
    schemaName,                     # str: e.g. "lists", "core", "study"
    queryName,                      # str: table or query name
    viewName      = NULL,           # str: named view to use
    colSelect     = NULL,           # vector or comma-sep string: columns to return
    maxRows       = NULL,           # int: max rows (NULL = ALL rows)
    rowOffset     = NULL,           # int: rows to skip (pagination)
    colSort       = NULL,           # str: column name prefixed with "+" or "-"
    colFilter     = NULL,           # makeFilter() result: row filters
    showHidden    = FALSE,          # logical: include hidden columns
    colNameOpt    = "caption",      # str: "caption", "fieldname", or "rname"
    containerFilter = NULL,         # str: e.g. "CurrentAndSubfolders"
    parameters    = NULL,           # named list: for parameterized queries
    includeDisplayValues = FALSE,   # logical: include lookup display values
    method        = "POST"          # str: HTTP method ("GET" or "POST")
)
```

**Returns:** A data frame with `stringsAsFactors = FALSE`. Column names are determined by `colNameOpt`.

#### labkey.executeSql -- Run LabKey SQL

```r
rows <- labkey.executeSql(
    baseUrl         = NULL,         # str: server URL
    folderPath,                     # str: container path
    schemaName,                     # str: target schema
    sql,                            # str: LabKey SQL query
    maxRows         = NULL,         # int: row limit
    rowOffset       = NULL,         # int: row offset
    colSort         = NULL,         # str: sort columns
    showHidden      = FALSE,        # logical: include hidden columns
    colNameOpt      = "caption",    # str: column naming option
    containerFilter = NULL,         # str: container scope
    parameters      = NULL          # named list: query parameters
)
```

**Returns:** A data frame with `stringsAsFactors = FALSE`.

#### labkey.getQueries -- List available tables/queries

```r
queries <- labkey.getQueries(
    baseUrl    = NULL,              # str: server URL
    folderPath,                     # str: container path
    schemaName                      # str: schema to explore
)
```

**Returns:** A data frame listing available queries in the schema.

#### labkey.getQueryDetails -- Get column metadata

```r
details <- labkey.getQueryDetails(
    baseUrl    = NULL,              # str: server URL
    folderPath,                     # str: container path
    schemaName,                     # str: schema name
    queryName                       # str: table/query name
)
```

**Returns:** A data frame with column metadata (name, type, caption, etc.).

#### labkey.getSchemas -- List schemas

```r
schemas <- labkey.getSchemas(
    baseUrl    = NULL,              # str: server URL
    folderPath                      # str: container path
)
```

**Returns:** A data frame listing available schemas.

### Session-Based API (Alternative Style)

Rlabkey also provides a session-based interface that wraps the direct functions:

```r
# Create a session
s <- getSession(
    baseUrl    = "http://localhost:8080/",
    folderPath = "/home"
)

# Explore
lsProjects("http://localhost:8080/")  # list projects (before session)
lsFolders(s)                           # list folders in session
lsSchemas(s)                           # list schemas in session

# Get schema and retrieve data
scobj <- getSchema(s, "lists")         # returns schema object with query names
df <- getRows(s, scobj$MyTable)        # returns data frame (colNameOpt defaults to "fieldname")
```

The session-based `getRows` function defaults to `colNameOpt='fieldname'` (unlike `labkey.selectRows` which defaults to `'caption'`).

### Response Format

Both `labkey.selectRows` and `labkey.executeSql` return R data frames directly:

```r
rows <- labkey.selectRows(
    baseUrl = "http://localhost:8080/",
    folderPath = "/home",
    schemaName = "lists",
    queryName  = "MyTable"
)

# The result is already a data frame:
nrow(rows)          # number of rows
colnames(rows)      # column names
str(rows)           # structure/types
head(rows)          # preview first rows

# Access columns directly:
rows$Name
rows$Age

# Date columns come back as strings -- convert explicitly:
rows$StartDate <- as.Date(rows$StartDate)

# Or for datetime with timezone:
rows$Created <- as.POSIXct(rows$Created, format = "%Y/%m/%d %H:%M:%S")
```

### Column Name Options (`colNameOpt`)

The `colNameOpt` parameter controls how data frame columns are named:

| Value | Description | Example |
|---|---|---|
| `"caption"` | Field caption/label (default for `labkey.selectRows`). Best for display, harder to script with. | `"Participant ID"` |
| `"fieldname"` | Field name as used in LabKey API calls (default for `getRows`). Best for scripting. | `"ParticipantId"` |
| `"rname"` | R-safe name: lowercase, spaces become `_`, slashes become `_`. Used by LabKey R Views. | `"participantid"` |

### Query Filters

```r
# Build filters with makeFilter():
filters <- makeFilter(
    c("Country", "EQUAL", "Germany"),
    c("Age", "GREATER_THAN_OR_EQUAL", "18"),
    c("Status", "IN", "Active;Enrolled")
)
rows <- labkey.selectRows(baseUrl = "http://localhost:8080/",
    folderPath = "/home", schemaName = "study",
    queryName = "Demographics", colFilter = filters)
```

The `makeFilter()` function accepts any number of filter triplets in the form `c("column", "OPERATOR", "value")`. Multiple filters are ANDed together.

#### Filter Operators Reference

**Comparison:** `EQUAL`, `NOT_EQUAL`, `GREATER_THAN`, `LESS_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL`, `NOT_EQUAL_OR_MISSING`

**Date comparison:** `DATE_EQUAL`, `DATE_NOT_EQUAL`, `DATE_GREATER_THAN`, `DATE_LESS_THAN`, `DATE_GREATER_THAN_OR_EQUAL`, `DATE_LESS_THAN_OR_EQUAL`

**String:** `STARTS_WITH`, `DOES_NOT_START_WITH`, `CONTAINS`, `DOES_NOT_CONTAIN`, `CONTAINS_ONE_OF`, `CONTAINS_NONE_OF`

**Set/Range:** `IN`, `NOT_IN` (semicolon-delimited), `BETWEEN`, `NOT_BETWEEN` (comma-delimited), `MEMBER_OF`

**Null checks (use empty string as value):** `MISSING`, `NOT_MISSING`, `MV_INDICATOR`, `NO_MV_INDICATOR`

**Array:** `ARRAY_CONTAINS_ALL`, `ARRAY_CONTAINS_ANY`, `ARRAY_CONTAINS_NONE`, `ARRAY_CONTAINS_EXACT`, `ARRAY_CONTAINS_NOT_EXACT`, `ARRAY_ISEMPTY`, `ARRAY_ISNOTEMPTY`

**Search:** `Q` (full-text search across table)

**Lineage:** `EXP_CHILD_OF`, `EXP_PARENT_OF`, `EXP_LINEAGE_OF`

**Ontology:** `ONTOLOGY_IN_SUBTREE`, `ONTOLOGY_NOT_IN_SUBTREE`

#### Filter Examples

```r
# Single filter (equals is common):
makeFilter(c("Country", "EQUAL", "Germany"))

# Multiple filters (ANDed):
makeFilter(
    c("TextFld", "CONTAINS", "h"),
    c("BooleanFld", "EQUAL", "TRUE")
)

# IN operator (semicolon-delimited values):
makeFilter(c("RowId", "IN", "2;3;6"))

# MISSING operator (empty string for value):
makeFilter(c("IntFld", "MISSING", ""))
```

### Container Filters

Control which containers are searched. Pass as `containerFilter=` to `labkey.selectRows`/`labkey.executeSql`:

- `"Current"` -- only the active container (default when NULL)
- `"CurrentAndSubfolders"` -- active container and its children
- `"CurrentPlusProject"` -- active container and its parent project
- `"CurrentAndParents"` -- active container and all ancestors
- `"CurrentPlusProjectAndShared"` -- current, project, and shared folder
- `"AllFolders"` -- everything the user can read

### Lookup Columns

To traverse lookup (foreign key) columns, use `/` in `colSelect`:

```r
# Include columns from a lookup target:
rows <- labkey.selectRows(baseUrl = "http://localhost:8080/",
    folderPath = "/home", schemaName = "lists",
    queryName = "AllTypes",
    colSelect = "TextFld,IntFld,IntFld/LookupValue"
)
```

Use `"*"` as `colSelect` to get all columns including those not in the default view.

### Error Handling

Rlabkey raises R errors (via `stop()`) on failure. Use `tryCatch` for error handling:

```r
tryCatch({
    rows <- labkey.selectRows(baseUrl = "http://localhost:8080/",
        folderPath = "/home", schemaName = "lists",
        queryName = "MyTable")
}, error = function(e) {
    message("Error: ", e$message)
})
```

Common error scenarios:
- **Wrong schema/query name**: HTTP 404 with "Query not found" message
- **Bad credentials**: HTTP 401 unauthorized
- **Wrong server URL**: Connection refused or "could not resolve host"
- **HTTP-to-HTTPS mismatch**: Unexpected redirect (302)

For debugging, enable verbose output:
```r
labkey.setDebugMode(TRUE)
# ... run your query ...
labkey.setDebugMode(FALSE)
```

### WAF Encoding

By default, Rlabkey WAF-encodes SQL in `labkey.executeSql` to pass through web application firewalls. This requires LabKey Server v23.9.0+. For older servers:

```r
labkey.setWafEncoding(FALSE)
labkey.executeSql(baseUrl = "http://localhost:8080/",
    folderPath = "/home", schemaName = "core",
    sql = "SELECT * FROM Containers")
```

### Data Modification APIs

For completeness -- use these when analysis requires writing back results:

- `labkey.insertRows(baseUrl, folderPath, schemaName, queryName, toInsert)` -- insert new rows (data frame)
- `labkey.updateRows(baseUrl, folderPath, schemaName, queryName, toUpdate)` -- update rows (must include PK)
- `labkey.deleteRows(baseUrl, folderPath, schemaName, queryName, toDelete)` -- delete rows (must include PK)
- `labkey.truncateTable(baseUrl, folderPath, schemaName, queryName)` -- delete all rows
- `labkey.importRows(baseUrl, folderPath, schemaName, queryName, toImport)` -- bulk import from data frame
- `labkey.moveRows(baseUrl, folderPath, targetFolderPath, schemaName, queryName, toMove)` -- move rows to another container

All modification APIs accept optional `provenanceParams` and `options` parameters. Common options include:
- `auditBehavior`: `"NONE"`, `"SUMMARY"`, or `"DETAILED"`
- `auditUserComment`: string attached to audit log records

Data frames passed to modification functions must be created with `stringsAsFactors = FALSE`. Column names must match the LabKey column names. To set a value to NULL, use an empty string `""`.

### Utility Functions

| Function | Purpose |
|---|---|
| `labkey.whoAmI(baseUrl)` | Returns current user info (displayName, id, email, impersonated status) |
| `labkey.setDefaults(apiKey, baseUrl, email, password)` | Set default connection parameters |
| `labkey.setDebugMode(debug)` | Enable/disable debug output for requests |
| `labkey.setWafEncoding(wafEncode)` | Enable/disable WAF encoding for SQL |
| `labkey.getSchemas(baseUrl, folderPath)` | List available schemas |
| `labkey.getQueries(baseUrl, folderPath, schemaName)` | List tables/queries in a schema |
| `labkey.getQueryDetails(baseUrl, folderPath, schemaName, queryName)` | Get column metadata for a table |
| `labkey.getQueryViews(baseUrl, folderPath, schemaName, queryName)` | List named views for a table |
| `labkey.getDefaultViewDetails(baseUrl, folderPath, schemaName, queryName)` | Get default view column details |
| `labkey.getLookupDetails(baseUrl, folderPath, schemaName, queryName, lookupKey)` | Get lookup target column details |
| `labkey.getFolders(baseUrl, folderPath)` | List subfolders |

## Important Considerations

1. **maxRows defaults to NULL (ALL rows)** in `labkey.selectRows`. Always set an explicit `maxRows` for large tables to avoid pulling the entire dataset into memory.

2. **Filter value delimiters are inconsistent**: `IN`/`NOT_IN` use semicolons (`"A;B;C"`), while `BETWEEN`/`NOT_BETWEEN` use commas (`"10,50"`). Null-check operators (`MISSING`, `NOT_MISSING`, etc.) require an empty string `""` as the value.

3. **colSelect accepts both vectors and comma-separated strings**: `colSelect = c("Name", "Age")` and `colSelect = "Name,Age"` both work. When using a string, do not include spaces between column names.

4. **Sort syntax**: prefix `+` for ascending or `-` for descending: `colSort = "+Age"` or `colSort = "-Name"`.

5. **WAF encoding**: `labkey.executeSql` WAF-encodes SQL by default. Requires LabKey Server v23.9.0+. Call `labkey.setWafEncoding(FALSE)` for older servers.

6. **folderPath requires a leading slash**: Use `"/home"` or `"/MyProject/MyFolder"`, not `"home"`.

7. **colNameOpt defaults differ**: `labkey.selectRows` defaults to `"caption"`, while `getRows` (session-based) defaults to `"fieldname"`. Use `colNameOpt = "fieldname"` for consistent, scriptable column names.

8. **Data frames for writes must use stringsAsFactors = FALSE**: When creating data frames for `labkey.insertRows`, `labkey.updateRows`, or `labkey.deleteRows`, always set `stringsAsFactors = FALSE`.

9. **Multiple filters on the same column** are supported -- pass multiple triplets to `makeFilter()`.

10. **baseUrl must include the context path and trailing slash**: e.g. `"http://localhost:8080/labkey/"` if the server uses a context path, or `"http://localhost:8080/"` if it does not.

11. **LabKey SQL is not standard SQL.** It is a SQL dialect specific to LabKey. Use `mcp__labkey__validateSQL` to check syntax before executing. Refer to LabKey documentation for dialect-specific features (e.g., lookup column traversal via `/` or `.` notation).

12. **SSL configuration**: For HTTPS servers on Windows, you may need to set the `RLABKEY_CAINFO_FILE` environment variable pointing to a CA bundle file. Use `labkey.acceptSelfSignedCerts()` for development servers with self-signed certificates.

## Typical Analysis Workflow

```r
library(Rlabkey)
library(jsonlite)

# 1. Read server URL from .mcp.json (credentials come from ~/.netrc)
config <- fromJSON(".mcp.json")
server_url <- sub("/mcp$", "/", config$mcpServers$labkey$url)

# 2. Set defaults (no apiKey — .netrc provides authentication)
labkey.setDefaults(baseUrl = server_url)

# 3. Explore (or use MCP tools for interactive exploration)
schemas <- labkey.getSchemas(baseUrl = server_url, folderPath = "/home")
queries <- labkey.getQueries(baseUrl = server_url, folderPath = "/home",
    schemaName = "lists")

# 4. Retrieve data
rows <- labkey.selectRows(baseUrl = server_url,
    folderPath = "/home",
    schemaName = "lists",
    queryName  = "Participants",
    colSelect  = c("ParticipantId", "Name", "Age", "Country"),
    colFilter  = makeFilter(c("Age", "GREATER_THAN_OR_EQUAL", "18")),
    maxRows    = 1000,
    colSort    = "+Age",
    colNameOpt = "fieldname"
)

# 5. Analyze
summary(rows)
table(rows$Country)
tapply(rows$Age, rows$Country, mean, na.rm = TRUE)

# 6. Complex queries with SQL
sql_result <- labkey.executeSql(baseUrl = server_url,
    folderPath = "/home",
    schemaName = "lists",
    sql = "SELECT Country, COUNT(*) AS N, AVG(Age) AS AvgAge
           FROM Participants
           GROUP BY Country
           ORDER BY N DESC",
    colNameOpt = "fieldname"
)
print(sql_result)
```
