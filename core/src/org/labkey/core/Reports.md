# Converting a Script into a LabKey Report

This guide is the delta between "a script that talks to LabKey over HTTP" and "a script that runs *inside* LabKey as a saved Report." Read `DataAnalysis_Python.md` or `DataAnalysis_R.md` first to build the analysis script (external execution model: explicit connection, API key, `select_rows`/`labkey.selectRows` HTTP calls). This guide covers what changes when that script becomes a Report (server-side execution model: injected context, no connection code, viewing-user permissions). See `FileBasedModules.md` for general file-based module structure — this guide goes deeper on the report-specific pieces it only summarizes.

Jump to the track that matches your language:
- R → [R Report Track](#r-report-track)
- Python/Jupyter → [Python / Jupyter Report Track](#python--jupyter-report-track)

Everything else on this page (data-bound vs. not, authorization, UI vs. file-based module) applies to both languages.

For report topics not covered here (e.g. additional export formats, less common report types), call `searchDocumentation`.

## Report Type Landscape

LabKey has several built-in report types: Query Report (renders a query view, no script), Attachment Report (uploaded static document), Link Report (URL pointer), JavaScript Report (runs in the *viewer's browser*, not server-side), R Report, Jupyter Report, and Query Snapshot (a persisted table, not really a "report"). **R Reports** and **Jupyter Reports** are the two paths this guide covers for turning an analyst-authored script into a server-side report. (A generic `ExternalScriptEngineReport`/`InternalScriptEngineReport` mechanism also exists for other JSR223-compatible engines an admin configures — historically used for Perl — but there's no conversion track for it here.)

## Data-Bound vs. Not Data-Bound

A report is "data-bound" if its descriptor has a `schemaName` property (usually with `queryName` and optionally `viewName`). This one property controls everything about whether a query result set gets piped into the script:

- **Data-bound**: LabKey runs the query as the *viewing user*. For **R**, the result set is streamed into the script's working directory before the script executes, and read automatically into `labkey.data` (see [R Report Track](#r-report-track)). For **Jupyter**, nothing is pre-staged — LabKey only writes a *query descriptor* (schema/query/columns/filters) into `report_config.json`, and `get_report_data()` uses that descriptor to make its own HTTP call back to the server *at cell-execution time*. Don't assume calling it is "free" the way reading `labkey.data` is — it's a live query, subject to the viewing user's live permissions and to query latency, at whatever moment the cell runs (see [Python / Jupyter Report Track](#python--jupyter-report-track)).
- **Not data-bound**: no query result set is ever provided. The script must fetch its own data at runtime (Rlabkey `labkey.selectRows()`, or Jupyter's `get_report_api_wrapper()`) — the same external-API model as `DataAnalysis_Python.md`/`DataAnalysis_R.md`.

### Setting it via the UI

- **R Report from a Data Grid** (Charts/Reports menu on a grid) — bound to that grid's query/view/filters.
- **R Report independent of a grid** (⚙ → Manage Views → Add Report → R Report) — not bound; listed under "Uncategorized" in Manage Views.

### Setting it via a file-based module

Placing an R script two directories deep under `reports/schemas/` auto-derives the binding from the path — no XML needed:

```
resources/reports/schemas/<schemaName>/<queryName>/MyReport.r
```

Real example from the `ehr` module — `MyReport.r`'s sibling `.report.xml` doesn't even need a `schemaName` property, because the directory position (`schemas/study/Weight/`) already supplies `schemaName=study`, `queryName=Weight`:

`server/modules/ehrModules/ehr/resources/reports/schemas/study/Weight/Weight Graph.report.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ReportDescriptor>
    <Properties>
        <Prop name="dataRegionName">query</Prop>
    </Properties>
</ReportDescriptor>
```

`server/modules/ehrModules/ehr/resources/reports/schemas/study/Weight/Weight Graph.r` (excerpt) — `labkey.data` is populated purely because of the directory it lives in:
```r
library(lattice);
labkey.data$date = as.Date(labkey.data$date);
size = length(unique(labkey.data$id));
png(filename="${imgout:graph.png}", width=800, height=(400 * size));
xyplot(weight ~ date | id, data=labkey.data, layout=c(1,size), xlab="Date", ylab="Weight (kg)");
dev.off();
```

A script placed directly at `reports/schemas/foo.r` (only one path segment, no schema/query subfolders) gets no binding at all — it's the not-data-bound case.

**This path-based auto-derivation works for R but NOT for Jupyter.** `.ipynb` files never parse `schemaName`/`queryName` from their directory position — a data-bound Jupyter module report must set both explicitly via `<Prop>` entries in its `.report.xml` (see [Python / Jupyter Report Track](#python--jupyter-report-track)).

## Authorization & Execution Model

Two independent layers govern what happens when a report runs:

**1. Who can view/edit the report object.** Every report has an access setting — public (readable by anyone with permission on the underlying data), private (creator only), or custom (explicit ACL). Editing a *shared* report someone else created requires the `EditSharedReportPermission` role; editing your own private report just requires ownership.

**2. What data the script can see at execution time — and this is the critical fact:**

> **The script always executes under the *viewing* user's LabKey permissions, not the author's.** When a data-bound report runs, LabKey resolves the query using the current viewer's session (`context.getUser()`), so `labkey.data` / `get_report_data()` reflects that viewer's row- and column-level permissions and any custom-view filters — not whoever wrote the script.

Any live callback the script makes back into LabKey (an Rlabkey API call, or Jupyter's `get_report_api_wrapper()`) carries a short-lived credential scoped to that same viewing session — never a hardcoded credential:
- R: an in-script `labkey.apiKey` variable, resolved per-session.
- Jupyter: `X-LABKEY-APIKEY` / `X-LABKEY-USERID` / `X-LABKEY-EMAIL` HTTP headers sent to the notebook execution service.

**Authoring gate.** Creating or editing an R or Jupyter report requires the **Trusted Analyst** (a Premium-only role — on a non-Premium build only Platform Developer/Site Admin can satisfy it) or **Platform Developer** role. For **R reports specifically**, if you're not a Platform Developer the script engine must additionally be configured as **sandboxed** (an admin-declared isolation claim — LabKey does not verify it). This gate exists because R script execution is **not sandboxed by default**: an R script can call `system()` and reach the OS shell with the LabKey server process's own privileges, bypassing LabKey's permission model entirely for anything outside the app (file access, network calls, etc.). Treat authoring an R report as granting real code-execution capability, not just a data query. **Jupyter reports have no equivalent sandbox check** — and don't need one, since a Jupyter report never executes in-process on the LabKey host; it always ships the notebook over HTTP to a separate, admin-configured execution service (see [Python / Jupyter Report Track](#python--jupyter-report-track)).

**`runInBackground`** (a `<Prop>` on the report descriptor) routes execution through a pipeline job instead of the request thread. This changes *where* the script runs, not *whose* permissions it runs under. **This only works for R reports.** Jupyter reports can't run in the background this way — the "Run in background" option is hidden in the designer for Jupyter reports, and setting the property another way has no effect (the code paths that would honor it all gate on the report being an R report first).

## R Report Track

### What's automatically injected

If the report is data-bound (see above), LabKey prepends a textual prolog to your script before it runs, defining:

```r
labkey.data                 # data frame — the query result set (only if data-bound)
labkey.url.base             # e.g. "http://localhost:8080/labkey/"
labkey.url.path             # container path portion of the current URL
labkey.url.params           # list of URL params / applied grid filters, or NULL
labkey.user.email           # the viewing user's email
labkey.file.root            # absolute path to the container's file root, or NULL
labkey.pipeline.root        # absolute path to the pipeline root, or NULL
labkey.apiKey               # short-lived API key scoped to the viewing session
labkey.url(controller, action, list)   # helper: builds a LabKey URL
labkey.resolveLSID(lsid)               # helper: builds a resolveLSID URL
```

`quit()` is overridden to raise an error instead of killing the R session — a script that calls `quit()`/`q()` expecting to stop execution will instead see an error, by design.

Column names in `labkey.data` are lower-cased and sanitized: a bare space becomes an underscore, but most special characters are spelled out rather than collapsed to `_` — e.g. `CD4+` → `cd4_plus_`, and a slash is spelled out too (`Weight/Height` → `weight_fs_height`, not `weight_height`). Run `names(labkey.data)` early when porting a script — don't assume the original column names survive, and don't assume a slash just becomes an underscore.

### Substitution tokens

Tokens have the form `${id:name}` and are resolved to real file paths before the script runs (for outputs) or after it finishes (LabKey then renders/serves whatever file was written). **Modern syntax**: put a comment line `#${id:name}` immediately before the line that consumes it:

```r
#${imgout:graph.png}
png(filename="${imgout:graph.png}");
plot(labkey.data$x, labkey.data$y);
dev.off();
```

The older bare inline form (`${id:name}` with no leading `#`) still works but is deprecated — prefer the comment-line form in new scripts.

| Token | Purpose |
|---|---|
| `${input_data}` / `${input_data:name}` | path to the query-result TSV (usually consumed automatically via `labkey.data`, not written by hand) |
| `${tsvout:name}` | write a TSV, rendered inline as an HTML grid |
| `${txtout:name}` | plain text output, rendered inline |
| `${consoleout:name}` | captured console/stdout output |
| `${htmlout:name}` | raw (unescaped) HTML, rendered inline |
| `${svgout:name}` | SVG image, rendered inline |
| `${imgout:name}` | generic raster image (default extension `jpg`) |
| `${jpgout:name}` / `${pngout:name}` | JPEG / PNG image specifically |
| `${pdfout:name}` | downloadable PDF |
| `${psout:name}` | downloadable Postscript |
| `${fileout:name}` | any other downloadable file type |
| `${jsonout:name}` | JSON output |
| `${hrefout:name}` | a link/URL to a produced artifact |
| `${knitrout:name}` | knitr-rendered document |

Use `regex(...)` inside a token — e.g. `${fileout:regex(.*?\.gct)}` — when the script generates files whose exact names aren't known ahead of time; LabKey maps any file matching the pattern to that output slot.

A separate set of tokens is substituted both into the engine invocation command line *and* — if you reference them directly — into the script body itself, since both substitution passes share the same replacement map: `${scriptName}`, `${scriptFile}`, `${workingDir}`, `${apikey}`, `${rLabkeySessionId}`, `${httpSessionId}`, `${sessionCookieName}`, `${baseServerURL}`, `${containerPath}`.

**`${srcDirectory}` does not work for Reports** despite being defined alongside this family — it's only ever populated for assay *transform* scripts, a different feature. If you reference it in a report script (e.g. `source("${srcDirectory}/util.R")`), it will not resolve, and — unlike the command-line substitution pass, which silently strips unmatched tokens — the script-body substitution pass writes it out **verbatim**, so the script fails at runtime trying to open a file literally named `${srcDirectory}/...`. Don't use it when porting a script into an R report.

### Knitr support (`.rhtml` / `.rmd`)

Both extensions are valid report script files with the same injected variables and token substitution available inside their chunks:

- **`.rhtml`** — an HTML page with knitr chunks delimited by HTML comments:
  ```html
  <!--begin.rcode echo=FALSE
  plot(labkey.data$diastolicbloodpressure, labkey.data$systolicbloodpressure);
  end.rcode-->
  ```
- **`.rmd`** — Markdown with fenced knitr chunks:
  ````markdown
  ```{r blood-pressure-scatter, echo=FALSE}
  plot(labkey.data$diastolicbloodpressure, labkey.data$systolicbloodpressure)
  ```
  ````

Real examples in the `scriptpad` test module: `server/testAutomation/modules/scriptpad/resources/reports/schemas/script_rhtml.rhtml` and `script_rmd.rmd` both build the same blood-pressure scatter plot from `labkey.data`.

If a knitr report needs an external JS/CSS library, declare it in the `.report.xml` sidecar's `<dependencies>` block rather than loading it from the script — `kable.rmd`'s sidecar:

`server/testAutomation/modules/scriptpad/resources/reports/schemas/kable.report.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ReportDescriptor xmlns="http://labkey.org/query/xml">
    <label>kable</label>
    <description>The uncool query</description>
    <hidden>false</hidden>
    <reportType>
        <R>
            <dependencies>
                <dependency path="https://cdn.datatables.net/1.13.6/js/jquery.dataTables.min.js"/>
                <dependency path="https://cdn.datatables.net/1.13.6/css/jquery.dataTables.min.css"/>
            </dependencies>
        </R>
    </reportType>
</ReportDescriptor>
```

### Batch-mode gotchas when porting a standalone script

- No interactive session: `library()`/`help()`/`data()` GUI popups don't work — call `library(...)` explicitly at the top of the script (every run is a fresh R instance, so nothing is pre-loaded across runs).
- Nothing opens a graphics device for you. Explicitly call `png()`/`Cairo()`/`pdf()`/etc. and always call `dev.off()` when finished — a script that relied on an interactive plot window will silently produce nothing.
- On headless Unix servers, base `jpeg()`/`png()` may fail without X11 — use the `Cairo` or `GDD` packages instead.
- Output from anything not evaluated at the top level (a `source()`d file, a function body) is not auto-printed — wrap it in `print()`. This is standard R semantics, but easy to forget when porting a script that always ran top-level in an interactive console.

### Conversion checklist

- Replace `read.csv("local_file.csv")` / hardcoded paths with `labkey.data` (if data-bound) or an explicit `${input_data}` read (if not).
- Redirect every plot from an interactive window or `ggsave()` to `${imgout:...}`/`${pngout:...}` + explicit `dev.off()`.
- Add explicit `print()` wherever the original script relied on top-level auto-printing inside a function or sourced file.
- Audit and remove/justify any `system()` calls — they run with the LabKey server's OS privileges, not the viewer's.
- Confirm column names via `names(labkey.data)` — don't assume the source data's original casing/characters survived sanitization, and remember slashes are spelled out (`_fs_`), not collapsed to `_`.
- Don't reference `${srcDirectory}` in a ported script — it doesn't resolve for Reports and will be written verbatim into the generated script file, causing a runtime file-not-found error.

## Python / Jupyter Report Track

**There is no plain-`.py` report path — in the UI or in a file-based module.** LabKey has no Python-language script engine wired into a report builder, and file-based module report discovery only recognizes `.r`/`.rmd`/`.rhtml`, `.ipynb`, `.js`, and `.xml` — never `.py`. The only supported mechanism for a Python-language report is a **Jupyter Report** (`.ipynb`), a Premium feature that requires an admin-configured Jupyter Report Engine (a separate Docker-hosted execution service). If someone asks for "a Python report," what they need is a Jupyter Report.

### What's injected — fundamentally different from R

Jupyter reports get **no** auto-populated `labkey.data`-equivalent variable. Instead, LabKey writes a `report_config.json` alongside the notebook before execution:

```json
{
  "baseUrl": "http://localhost:8080",
  "contextPath": "/labkey",
  "scriptName": "myReport.ipynb",
  "containerPath": "/my studies/demo",
  "parameters": [["pageId", "study.DATA_ANALYSIS"]],
  "version": 1
}
```

A `sourceQuery` block (the bound schema/query/view/filters) is added to `report_config.json` **only if the report is data-bound** — otherwise there's nothing for the notebook to load. The notebook must explicitly import and call a bundled helper module to get anything:

```python
from ReportConfig import get_report_api_wrapper, get_report_data, get_report_parameters
print(get_report_parameters())
print(get_report_data())
```

Authentication for the notebook's execution is carried via HTTP headers to the execution service (`X-LABKEY-APIKEY`, `X-LABKEY-USERID`, `X-LABKEY-EMAIL`, `X-LABKEY-CSRF`) — not an in-notebook variable like R's `labkey.apiKey`.

**No `${...}` substitution tokens apply to Jupyter reports.** Output capture relies entirely on the notebook's native cell-output rendering, the same as when you open the `.ipynb` in Jupyter directly — there's no LabKey-side token replacement for plots or files.

### Making a Jupyter report data-bound

Since path-based auto-derivation doesn't apply to `.ipynb`, a data-bound Jupyter module report must set `schemaName`/`queryName` explicitly in its `.report.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ReportDescriptor xmlns="http://labkey.org/query/xml">
    <Properties>
        <Prop name="schemaName">study</Prop>
        <Prop name="queryName">Weight</Prop>
    </Properties>
</ReportDescriptor>
```

### Conversion checklist

- Replace the notebook's original data-loading cell with `get_report_data()` (data-bound) or `get_report_api_wrapper()` + an explicit query call (not data-bound) — there's no automatic equivalent to R's `labkey.data`.
- Confirm the Jupyter engine's Docker image actually has your notebook's package dependencies installed — there's no per-report package install step.
- Don't port any `${imgout:...}`-style output-capture code from an R report you're referencing — it has no effect in a Jupyter report. Rely on normal notebook cell output.
- Don't port `runInBackground`/"run as pipeline job" expectations from an R report — Jupyter reports can't run this way (the option is hidden in the designer, and setting it another way is simply ignored at execution time).
- Remember this is a Premium feature — confirm a Jupyter Report Engine is configured (⚙ → Site → Admin Console → Views and Scripting) before assuming the report type is available.

## Saved via UI vs. Saved in a File-Based Module

| | UI-saved | File-based module |
|---|---|---|
| Storage | Database row, per folder | `resources/reports/schemas/<schema>/<query>/name.<ext>` in the module's source tree |
| Editable by end users | Yes, via the Source tab (if permitted) | No — only via the premium ModuleEditor tool, which writes back into the module source, not a per-folder DB row |
| Scope | One folder, one author (unless explicitly shared) | Ships with the module; runs identically everywhere the module is enabled |
| Deployment | Immediate | Hot-deployed — no server restart needed |

A file-based report's optional `name.report.xml` sidecar (same basename as the script) uses the `http://labkey.org/query/xml` `ReportDescriptor` schema. Three real shapes from this repo, in increasing complexity:

**Minimal** (just sets a property) — `Weight Graph.report.xml`, shown above under [Data-Bound vs. Not Data-Bound](#data-bound-vs-not-data-bound).

**With external dependencies** — `kable.report.xml`, shown above under [R Report Track](#r-report-track).

**Remote engine / session sharing** — `server/testAutomation/modules/scriptpad/resources/reports/schemas/script_rserve.report.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<ReportDescriptor>
    <description>Rserve Session Sharing</description>
    <reportType>
        <R>
            <scriptEngine remote="true"/>
            <functions>
                <function name="helloWorld"/>
            </functions>
        </R>
    </reportType>
</ReportDescriptor>
```
