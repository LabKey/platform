# Writing Assay Transform Scripts

A transform script runs **inside** LabKey Server during assay import. The server writes the uploaded data and run metadata to files, runs the script, and reads back the files the script writes. The script can clean or reshape result data, compute new columns, change run/batch properties, and reject or warn about an import. It is a file-exchange contract, not an HTTP API: most scripts never call back into the server.

This guide covers the contract as the server implements it, the traps, and a local development loop. For UI walkthroughs, module-based assays, and worked examples, call `searchDocumentation` (e.g. `searchDocumentation("transform scripts run properties reference")`, `searchDocumentation("transformation script warnings")`), then `retrieveDocument`. Where this guide and the documentation disagree, trust this guide.

## Before Writing Code

1. `setContainer`, then find the assay's tables: the schema is `assay.<Provider>.<AssayName>` (for a Standard assay, `assay.General.<AssayName>`), with tables `Batches`, `Runs`, and `Data`.
2. `listColumns` on `Data`, `Runs`, and `Batches`. The script must read and write these exact field names. Ask for a sample data file if the user has one.
3. Confirm with the user:
   - Language (Python `.py` or R `.R`). The server picks the engine by **file extension** from the engines configured in Admin Console > Views and Scripting; the engine must be local (remote/Rserve engines can't run transform scripts).
   - Whether the script transforms data, only validates it, or both.
   - Who will install it. The script must be in the assay design's container's **`@scripts` directory**, which only **Platform Developers** and **Site Admins** can write to, and only they can attach it to a design. With those roles, upload it with a WebDAV `PUT` to `<server>/_webdav/<container path>/@scripts/<file name>`; otherwise the user does it.

Uploading a script, saving an assay design, and importing a test run all change the server. Describe each change and get the user's confirmation before making it, even when your permissions and client mode would let you proceed.

## How a Run Executes

For each script on the assay design, in order:

1. The server creates an empty working directory and makes it the script's current directory.
2. It copies the script into that directory, **substituting `${...}` tokens in the script text** (see below), and writes `runProperties.tsv` plus the input data files.
3. It runs the script. A **non-zero exit code fails the import** and shows the script's combined stdout/stderr to the user; output from a successful run is discarded.
4. It reads back the error file, the transformed data file, and the transformed run properties file, if the script wrote them.
5. Transformed data and properties are passed on to the next script, then imported.

The working directory is deleted afterward unless the assay design has **Save Script Data for Debugging** checked. In that case it is kept under `@scripts/TransformAndValidationFiles/AssayId_<protocolId>/work*/` (one `work*` directory per import), which is the easiest way to see real inputs.

**When the data came from an uploaded file, every other file the script leaves in the working directory is attached to the run as an output file** (moved next to the uploaded data file). Write scratch files to a `tempfile` location unless you want them attached.

## Substitution Tokens

The server replaces these in the script text before running it:

| Token | Value |
|---|---|
| `${runInfo}` | Path to `runProperties.tsv`, always with forward slashes. The script's entry point. |
| `${transformOperation}` | `INSERT` (run import) or `UPDATE` (results edited, only if the script is set to run on edit) |
| `${srcDirectory}` | Directory of the original script — use it to load helper files. `__file__` points at the copy in the working directory. |
| `${baseServerURL}` | Server base URL including context path |
| `${containerPath}` | Container path of the import |
| `${apikey}` | Short-lived API key for the importing user, valid only while the script runs. Use it (with `${baseServerURL}`) only when the script must query the server; it is the one credential that may appear in a transform script, because it is substituted at run time. |

**After substitution, any remaining `${...}` anywhere in the script — code, strings, or comments — fails the import** with `Unreplaced substitution parameter(s) found in script`. Avoid shell-style `${VAR}`, `string.Template`, and JavaScript-style templates in the script text. Tokens such as `${rLabkeySessionId}` are legacy and exist only when a deprecated feature flag is on; don't use them.

## runProperties.tsv

Tab-separated, **no header**, one property per line. Values containing tabs, quotes, or newlines are quoted, so parse it with a TSV/CSV reader, never with `split("\t")`.

| Row | Columns after the name |
|---|---|
| Each run and batch field | value, Java type name (e.g. `java.lang.Integer`) |
| `assayId` | run name (INSERT only) |
| `runComments` | run comments (INSERT only) |
| `reRunId` | RowId of the run being replaced, on re-import only |
| `originalFileLocation` | present when the data file was already on the server |
| `baseUrl`, `containerPath`, `assayType`, `assayName`, `protocolId`, `protocolLsid`, `protocolDescription` | context, as strings |
| `userName` | email of the user **performing the import** (the documentation wrongly says the assay design's creator) |
| `workingDir` | the working directory |
| `runDataUploadedFile` | path(s) of the file(s) the user uploaded, `;`-separated. Absent when data was posted as rows through the API. |
| `runDataFile` | 1: path of the server's **parsed TSV** of the data · 2: internal namespace (ignore) · 3: path where the script may write transformed data |
| `errorsFile` | path of the error file the script may write |
| `transformedRunPropertiesFile` | path where the script may write changed run/batch properties (INSERT only) |
| `severityLevel` | `WARN` or `ERROR` — whether warnings are allowed on this pass (INSERT only) |

**Read `runDataFile` column 1, not `runDataUploadedFile`.** The parsed TSV already handles Excel, CSV, and TSV uploads, has a header row of column names, and holds values the server converted where it could (dates in ISO format). Values it couldn't convert are left as-is so the script can clean them up. The raw uploaded file may be Excel, may be several files, or may not exist. The `transform_helper` in the `labkey` Python package reads the raw uploaded file with naive comma/tab splitting — don't use it except for trivial CSV/TSV cases.

## What the Script Writes

All outputs are optional. A validation-only script writes only the error file.

- **Transformed data** → the path in `runDataFile` column 3. Tab-separated with a header row; columns are matched to result fields by name or import alias, and unknown columns are ignored. If this file exists it **replaces** the uploaded data entirely, so include every row and column you want imported. If you don't write it, the original data is imported unchanged.
- **Errors** → the `errorsFile` path. Tab-separated, no header, one line per message: `error<TAB><field><TAB><message>`. `<field>` may be empty or a run/batch field name (the form highlights that field). Any `error` line fails the import. `warn` lines never reach the user: background imports write them to the job log, API imports to the server log, and wizard imports drop them.
- **Changed properties** → the `transformedRunPropertiesFile` path. Tab-separated `name<TAB>value` lines. Run fields match by name or import alias, batch fields by name.

**Exit 0 after writing error lines.** A non-zero exit skips reading the error file, and the user sees the raw script output instead of your messages. Reserve non-zero exits for real script failures.

## Warnings (User May Proceed)

Only the interactive import wizard supports warnings. When `severityLevel` is `WARN`:

1. Write `maximumSeverity<TAB>WARN` to the transformed run properties file.
2. Write an HTML message to a file named **`errors.html` in the working directory**.

The user sees the message and can cancel or proceed. **Proceeding re-runs the script with `severityLevel` set to `ERROR`**. On that pass the script must not warn again — if `errors.html` exists when warnings aren't allowed, it is treated as an error and the import fails. Always check `severityLevel` before warning.

Background imports fail outright on a warning ("Background assay import does not support warnings"). API imports (`assay-importRun.api`, client `importRun`) **ignore warnings**: the import succeeds and `errors.html` is just attached as a run output. Report anything that must block an API import as an error. `maximumSeverity<TAB>ERROR` fails the import, showing `errors.html` if the data came from a file and a generic message otherwise; prefer the error file.

## Python Skeleton

```python
import csv
import sys

# Local runs pass a runProperties.tsv path; on the server the token is substituted.
RUN_PROPS = sys.argv[1] if len(sys.argv) > 1 else '${runInfo}'


def read_run_properties(path):
    with open(path, newline='', encoding='utf-8') as f:
        return {row[0]: row[1:] for row in csv.reader(f, delimiter='\t') if row}


props = read_run_properties(RUN_PROPS)
errors_path = props['errorsFile'][0]
errors = []

if 'runDataFile' not in props:
    errors.append(['error', '', 'No result data was provided.'])
else:
    data_in, _namespace, data_out = props['runDataFile'][:3]
    with open(data_in, newline='', encoding='utf-8') as f:
        rows = list(csv.DictReader(f, delimiter='\t'))

    for i, row in enumerate(rows, start=1):
        try:
            row['Average'] = (float(row['Result1']) + float(row['Result2'])) / 2
        except (ValueError, KeyError):
            errors.append(['error', '', f'Row {i}: Result1 and Result2 must be numbers.'])

    if not errors:
        with open(data_out, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()) if rows else [], delimiter='\t')
            writer.writeheader()
            writer.writerows(rows)

if errors:
    with open(errors_path, 'w', newline='', encoding='utf-8') as f:
        csv.writer(f, delimiter='\t').writerows(errors)
# Exit 0 even with errors so the server reads the error file.
```

pandas works too: `pd.read_csv(data_in, sep='\t')` and `df.to_csv(data_out, sep='\t', index=False)`. Make sure pandas is installed for the interpreter the server's Python engine uses.

## R Notes

The contract is identical. Rlabkey provides `labkey.transform.readRunPropertiesFile("${runInfo}")` and `labkey.transform.getRunPropertyValue(runProps, "runDataFile")`. Read the data with `read.delim(path, stringsAsFactors = FALSE, check.names = FALSE)` (`check.names = FALSE` keeps field names intact) and write with `write.table(df, path, sep = "\t", quote = FALSE, row.names = FALSE, na = "")`. For R-specific examples, `searchDocumentation("transformation scripts in R")`.

## Developing Locally

The server is a slow place to debug, so build a fixture and run the script directly first.

1. **Get real inputs.** Best: have the user check **Save Script Data for Debugging** on the assay design and import once; the saved `runProperties.tsv` and `runDataFile.tsv` are exact. Otherwise build them: a `runData.tsv` in the parsed format (header row of the `Data` field names from `listColumns`) and a `runProperties.tsv` such as:

   ```
   assayId	Run 1	java.lang.String
   runDataFile	/tmp/fixture/runData.tsv	ignored	/tmp/fixture/out/transformed.tsv
   errorsFile	/tmp/fixture/out/validationErrors.tsv
   transformedRunPropertiesFile	/tmp/fixture/out/transformedRunProperties.tsv
   severityLevel	WARN
   ```

   Rewrite paths in a saved copy to point at your fixture directory.
2. **Run it** from a scratch working directory: `python transform.py /tmp/fixture/runProperties.tsv`. Check the exit code, the transformed TSV, and the error file. Test bad input and, if you use warnings, both `severityLevel` values.
3. **Search the script text for stray `${`** other than the supported tokens.
4. **Install and import.** Upload the script to `@scripts` (WebDAV `PUT`; re-uploading replaces it in place, so the design picks up the new version on the next import). Attach it in the assay designer's Transform Scripts section, or through `assay-saveProtocol.api` with `protocolTransformScripts: [{scriptPath, runOnImport, runOnEdit}]`, where `scriptPath` is the file's absolute server path under `@scripts`. Import a sample file (`assay-importRun.api` returns the script's error messages directly), then confirm the results with `executeSql` or `select_rows` against `assay.<Provider>.<AssayName>.Data`.

## Other Considerations

1. **Multiple scripts** run in design order. Each sees the previous script's transformed data and properties.
2. **Run on edit** (`runOnEdit`) runs the script with `${transformOperation}` = `UPDATE` when results are edited. There are no run properties to change and no warnings on that path, so branch on the operation if one script handles both.
3. **Scripts run as the server's OS user**, with whatever Python/R packages that interpreter has. Confirm with the user rather than assuming a package is installed.
