# LabKey Data Analysis using Python

This project is for performing data analysis against a LabKey Server instance using AI assistance.

**Connection defaults:** The LabKey server URL and API key can be inferred from `.mcp.json` in this directory. The `url` field (minus the `/mcp` path) provides the server endpoint, and the `apikey` header value provides the authentication token.

**Do not embed API keys in generated scripts.** Instead, ensure a `.netrc` file (Linux/Mac) or `_netrc` file (Windows) exists in the user's home directory with the server credentials. If the file does not exist, offer to create it using the API key from `.mcp.json`. The format is:

```
machine <hostname>
login apikey
password <api-key-from-mcp.json>
```

The `machine` value is the hostname only — no protocol (`https://`), no port, no path. For example, for `https://myserver.labkey.com:8443/labkey/mcp`, use `myserver.labkey.com`. On Linux/Mac, set permissions to 600 (`chmod 600 ~/.netrc`).

When writing Python scripts, read the server URL from `.mcp.json` to pre-populate the `APIWrapper` connection parameters, but omit `api_key` — the `labkey` package reads `.netrc` automatically. Before running any script, confirm with the data analyst:
- Is this the correct server?
- Should `use_ssl` be True or False? (infer from the URL scheme in `.mcp.json`)
- What container path should be used? (use MCP `listContainers` to show available options)

Confirm all of these settings with the analyst before writing any script.

## Online Reference Material
https://www.labkey.org/Documentation/wiki-page.view?name=python

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
5. To actually retrieve data, write a Python script using the `labkey` Python API (see below)

## LabKey Python API (`labkey` package)

**Install:** `pip install labkey`
**Requires:** Python 3.11+, LabKey Server v15.1+
**Repo:** https://github.com/LabKey/labkey-api-python

### Connection Setup

**Preferred: `.netrc` authentication (no credentials in scripts)**

```python
from labkey.api_wrapper import APIWrapper

api = APIWrapper(
    "localhost:8080",           # domain (hostname or hostname:port)
    "MyProject/MyFolder",      # container path
    context_path=None,         # URL path segment after domain, e.g. "labkey"
    use_ssl=False,             # True for https
    verify_ssl=True,           # False for self-signed dev certs
)
```

The `labkey` package automatically reads credentials from `~/.netrc` (Linux/Mac) or `~/_netrc` (Windows). The `.netrc` entry should use `apikey` as the login and the API key as the password:

```
machine localhost
login apikey
password TheUniqueAPIKeyGeneratedForYou
```

The `machine` value must be the hostname only — no protocol, no port, no path. Set file permissions to 600 on Linux/Mac (`chmod 600 ~/.netrc`).

Authentication options (in order of preference):
- **.netrc file** (recommended): Create `~/.netrc` with `machine`, `login apikey`, `password <key>` fields (chmod 600). No credentials appear in scripts.
- **API key in code** (avoid in generated scripts): Pass `api_key=` to APIWrapper. Only use this for quick interactive testing, not in saved scripts.

### Data Retrieval APIs

#### select_rows -- Query a table/view

```python
result = api.query.select_rows(
    schema_name,                    # str: e.g. "lists", "core", "study"
    query_name,                     # str: table or query name
    view_name=None,                 # str: named view to use
    filter_array=None,              # list[QueryFilter]: row filters
    columns=None,                   # str: comma-separated column names
    max_rows=-1,                    # int: -1 = ALL rows (default!)
    sort=None,                      # str: comma-separated, prefix '-' for desc
    offset=None,                    # int: rows to skip (pagination)
    container_path=None,            # str: override container
    container_filter=None,          # str: e.g. "CurrentAndSubfolders"
    parameters=None,                # dict: for parameterized queries
    include_total_count=None,       # bool: include total in response
    timeout=300,                    # int: seconds
)
```

#### execute_sql -- Run LabKey SQL

```python
result = api.query.execute_sql(
    schema_name,                    # str: target schema
    sql,                            # str: LabKey SQL
    max_rows=None,                  # int: row limit
    sort=None,                      # str: sort columns
    offset=None,                    # int: row offset
    container_path=None,            # str: override container
    container_filter=None,          # str: container scope
    parameters=None,                # dict: query parameters
    timeout=300,                    # int: seconds
    waf_encode_sql=True,            # bool: WAF encoding (needs Server v23.09+)
)
```

Set `waf_encode_sql=False` if targeting LabKey Server older than v23.09.

#### get_queries -- List available tables/queries

```python
result = api.query.get_queries(
    schema_name,                    # str: schema to explore
    container_path=None,            # str: override container
    include_columns=None,           # bool: include column metadata
    include_system_queries=None,    # bool: include system-generated queries
    include_title=None,             # bool: include custom display titles
    include_user_queries=None,      # bool: include user-defined saved queries
    include_view_data_url=None,     # bool: include URLs for viewing data in browser
    query_detail_columns=None,      # bool: include detailed column info
    timeout=300,                    # int: seconds
)
```

### Response Format

Both `select_rows` and `execute_sql` return a dict:

```python
{
    "schemaName": "lists",
    "queryName": "MyTable",
    "rowCount": 25,                 # total rows (if include_total_count=True)
    "rows": [                       # list of row dicts
        {"Col1": "value", "Col2": 42, ...},
        ...
    ],
    "metaData": {
        "id": "Key",               # primary key column
        "fields": [{"name": "Col1", "type": "string"}, ...]
    }
}
```

Working with results:
```python
result = api.query.select_rows("lists", "MyTable")
for row in result["rows"]:
    print(row["Name"], row["Value"])

# Convert to pandas DataFrame:
import pandas as pd
df = pd.DataFrame(result["rows"])

# Date columns come back as strings -- convert explicitly:
df["StartDate"] = pd.to_datetime(df["StartDate"])

# Numeric columns may contain None for missing values -- pandas handles this
# but be aware when doing arithmetic:
df["Age"] = pd.to_numeric(df["Age"], errors="coerce")

# Lookup columns may return nested dicts (e.g. {"value": 1, "displayValue": "Group A"}).
# Extract the display value if needed:
if isinstance(df["Group"].iloc[0], dict):
    df["Group"] = df["Group"].apply(lambda x: x.get("displayValue") if isinstance(x, dict) else x)
```

### Query Filters

```python
from labkey.query import QueryFilter

filters = [
    QueryFilter("Country", "Germany"),                                    # equals (default)
    QueryFilter("Age", "18,65", QueryFilter.Types.BETWEEN),              # comma-delimited
    QueryFilter("Status", "Active;Enrolled", QueryFilter.Types.IN),      # semicolon-delimited
    QueryFilter("Name", "", QueryFilter.Types.IS_NOT_BLANK),             # no value needed
]
result = api.query.select_rows("study", "Demographics", filter_array=filters)
```

#### Filter Types Reference

**Comparison:** `EQUAL`, `NOT_EQUAL` (alias `NEQ`), `GT` / `GREATER_THAN`, `LT` / `LESS_THAN`, `GTE` / `GREATER_THAN_OR_EQUAL`, `LTE` / `LESS_THAN_OR_EQUAL`, `NEQ_OR_NULL` / `NOT_EQUAL_OR_MISSING`

**Date comparison:** `DATE_EQUAL`, `DATE_NOT_EQUAL`, `DATE_GREATER_THAN`, `DATE_LESS_THAN`, `DATE_GREATER_THAN_OR_EQUAL`, `DATE_LESS_THAN_OR_EQUAL`

**String:** `STARTS_WITH`, `DOES_NOT_START_WITH`, `CONTAINS`, `DOES_NOT_CONTAIN`, `CONTAINS_ONE_OF`, `CONTAINS_NONE_OF`

**Set/Range:** `IN` / `EQUALS_ONE_OF` (semicolons), `NOT_IN` / `EQUALS_NONE_OF` (semicolons), `BETWEEN` (commas), `NOT_BETWEEN` (commas)

**Null checks (no value needed):** `IS_BLANK`, `IS_NOT_BLANK`, `HAS_MISSING_VALUE`, `DOES_NOT_HAVE_MISSING_VALUE`, `HAS_ANY_VALUE`

**Array:** `ARRAY_CONTAINS_ALL`, `ARRAY_CONTAINS_ANY`, `ARRAY_CONTAINS_NONE`, `ARRAY_CONTAINS_EXACT`, `ARRAY_CONTAINS_NOT_EXACT`, `ARRAY_ISEMPTY`, `ARRAY_ISNOTEMPTY`

**Search:** `Q` (full-text search across table)

**Lineage:** `EXP_CHILD_OF`, `EXP_PARENT_OF`, `EXP_LINEAGE_OF`

**Ontology:** `ONTOLOGY_IN_SUBTREE`, `ONTOLOGY_NOT_IN_SUBTREE`

### Container Filters

Control which containers are searched. Pass as `container_filter=` to select_rows/execute_sql:

- `"Current"` -- only the active container
- `"CurrentAndSubfolders"` -- active container and its children
- `"CurrentPlusProject"` -- active container and its parent project
- `"CurrentAndParents"` -- active container and all ancestors
- `"CurrentPlusProjectAndShared"` -- current, project, and shared folder
- `"AllFolders"` -- everything the user can read

### Error Handling

```python
from labkey.exceptions import (
    RequestError,                   # base class for all server errors
    RequestAuthorizationError,      # 401 -- bad credentials
    QueryNotFoundError,             # 404 -- wrong schema/table name
    ServerNotFoundError,            # 404 -- wrong server or context_path
    ServerContextError,             # connection error, SSL error
    UnexpectedRedirectError,        # 302 -- usually http->https misconfiguration
)
from requests.exceptions import Timeout

try:
    result = api.query.select_rows("lists", "MyTable")
except QueryNotFoundError:
    print("Table not found -- check schema and query names")
except RequestAuthorizationError:
    print("Auth failed -- check API key or .netrc")
except ServerNotFoundError:
    print("Server not found -- check domain and context_path")
except Timeout:
    print("Request timed out")
except RequestError as e:
    print(f"Server error: {e.message}")
```

### Data Modification APIs

For completeness -- use these when analysis requires writing back results:

- `api.query.insert_rows(schema, query, rows)` -- insert new rows
- `api.query.update_rows(schema, query, rows)` -- update rows (must include PK)
- `api.query.delete_rows(schema, query, rows)` -- delete rows (must include PK)
- `api.query.truncate_table(schema, query)` -- delete all rows
- `api.query.import_rows(schema, query, data_file=f)` -- bulk import from file
- `api.query.save_rows(commands)` -- batch multi-table operations

All modification APIs accept `timeout=300`, `container_path=None`, `transacted=True`, and optional `audit_behavior` / `audit_user_comment`.

## Important Considerations

1. **max_rows defaults to -1 (ALL rows)** in `select_rows`. Always set an explicit `max_rows` for large tables to avoid pulling the entire dataset into memory.

2. **Filter value delimiters are inconsistent**: `IN`/`NOT_IN` use semicolons (`"A;B;C"`), while `BETWEEN`/`NOT_BETWEEN` use commas (`"10,50"`). This is a historical API quirk.

3. **columns is a comma-separated string**, not a list: `columns="Name,Age,Country"`.

4. **Sort syntax**: comma-separated column names, prefix `-` for descending: `sort="Age,-Name"`.

5. **WAF encoding**: `execute_sql` WAF-encodes SQL by default (since labkey v3.0.0). Requires LabKey Server v23.09+. Set `waf_encode_sql=False` for older servers.

6. **Container path override**: Every API method accepts `container_path=` to query a different folder without creating a new connection.

7. **CSRF tokens** are fetched automatically on the first request. This adds slight overhead to the first call.

8. **Default timeout is 300 seconds** (5 minutes) for all query operations.

9. **Multiple filters on the same column** are supported -- they are appended, not overwritten.

10. **select_rows sends a GET request** to `query-getQuery.api`. `execute_sql` sends a POST to `query-executeSql.api`.

11. **LabKey SQL is not standard SQL.** It is a SQL dialect specific to LabKey. Use `mcp__labkey__validateSQL` to check syntax before executing. Refer to LabKey documentation for dialect-specific features (e.g., lookup column traversal via `/` or `.` notation).

## Typical Analysis Workflow

```python
from labkey.api_wrapper import APIWrapper
from labkey.query import QueryFilter
import pandas as pd

# 1. Connect (credentials read from ~/.netrc automatically)
api = APIWrapper("localhost:8080", "MyProject", use_ssl=False)

# 2. Explore (or use MCP tools for interactive exploration)
schemas = api.query.get_queries("lists", include_columns=True)

# 3. Retrieve data
result = api.query.select_rows("lists", "Participants",
    columns="ParticipantId,Name,Age,Country",
    filter_array=[QueryFilter("Age", "18", QueryFilter.Types.GTE)],
    max_rows=1000,
    sort="Age"
)

# 4. Analyze with pandas
df = pd.DataFrame(result["rows"])
print(df.describe())
print(df.groupby("Country")["Age"].mean())

# 5. Complex queries with SQL
sql_result = api.query.execute_sql("lists",
    "SELECT Country, COUNT(*) as N, AVG(Age) as AvgAge "
    "FROM Participants GROUP BY Country ORDER BY N DESC"
)
summary = pd.DataFrame(sql_result["rows"])
```
