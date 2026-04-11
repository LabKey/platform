/*
 * Copyright (c) 2025 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.query.controllers;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for capturing and comparing row-level data for a single schema+query across databases.
 * Uses SchemaCompareNormalizer for cross-database value normalization.
 */
public class QueryDataService
{
    private static final Logger LOG = LogHelper.getLogger(QueryDataService.class, "Query data capture and diff");
    private static final int DEFAULT_QUERY_TIMEOUT = 120;
    private static final int MAX_BASELINE_FILES = 10_000;
    private final CanonicalQueryDataService _canonicalQueryDataService = new CanonicalQueryDataService();

    // ---- Capture ----

    public record CaptureResult(JSONObject metadata, Path tsvFile, Path metaFile) {}

    /**
     * Capture normalized row data for a single query to TSV + metadata JSON.
     */
    public CaptureResult captureQueryData(User user, Container container, String schemaName, String queryName,
                                          int rowLimit, int queryTimeout, @Nullable Map<String, Object> namedParameters)
    {
        if (queryTimeout <= 0)
            queryTimeout = DEFAULT_QUERY_TIMEOUT;

        // Wrap all DB operations in a transaction to ensure connections are returned to the pool
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        long[] rowCount = {0};
        List<String> sortedColumnNames;
        Map<String, JdbcType> columnTypes;
        List<String> pkColumnNames;
        boolean syntheticPk;

        try (DbScope.Transaction ignored = DbScope.getLabKeyScope().ensureTransaction())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (schema == null)
                throw new IllegalArgumentException("Schema not found: " + schemaName);

            TableInfo tableInfo = resolveTableInfo(schema, queryName);
            if (tableInfo == null)
                throw new IllegalArgumentException("Query/table not found: " + schemaName + "." + queryName);

            CanonicalQueryDataService.CanonicalQueryPlan plan = _canonicalQueryDataService.createPlan(tableInfo);
            List<ColumnInfo> columns = plan.columns();
            columnTypes = plan.columnTypes();
            sortedColumnNames = plan.columnNames();
            List<String> realPkColumnNames = plan.realPrimaryKeyColumnNames();

            // Effective PK: real PKs if available, otherwise first 3 columns
            pkColumnNames = getEffectivePkColumns(realPkColumnNames, sortedColumnNames);
            syntheticPk = realPkColumnNames.isEmpty();

            TableSelector selector = _canonicalQueryDataService.createSelector(
                tableInfo, columns, rowLimit, true, namedParameters, queryTimeout);

            selector.forEachMap(row ->
            {
                Map<String, Object> rawRow = _canonicalQueryDataService.extractRow(row, sortedColumnNames);
                Map<String, Object> normalizedRow = _canonicalQueryDataService.normalizeRowForDiff(rawRow, columnTypes);
                normalizedRows.add(normalizedRow);
                rowCount[0]++;
            });

            ignored.commit();
        }

        // Prepare output directory and file paths (no DB needed)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
        String safeSchema = sanitizeFileName(schemaName);
        String safeQuery = sanitizeFileName(queryName);
        String baseName = "query-baseline-" + safeSchema + "-" + safeQuery + "-" + timestamp;

        Path dir = getSchemaCompareDir(container);
        Path tsvFile = dir.resolve(baseName + ".tsv");
        Path metaFile = dir.resolve(baseName + ".meta.json");

        // Write TSV
        try (TSVMapWriter writer = new TSVMapWriter(new ArrayList<>(sortedColumnNames), normalizedRows))
        {
            Files.createDirectories(dir);
            writer.write(tsvFile.toFile());
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write TSV file: " + e.getMessage(), e);
        }

        // Build metadata
        JSONObject metadata = new JSONObject();
        metadata.put("schemaName", schemaName);
        metadata.put("queryName", queryName);
        metadata.put("capturedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("database", detectDatabaseType());
        metadata.put("containerPath", container.getPath());
        metadata.put("baseUrl", AppProps.getInstance().getBaseServerUrl());
        metadata.put("rowCount", rowCount[0]);
        metadata.put("rowLimit", rowLimit);
        metadata.put("queryTimeout", queryTimeout);

        if (namedParameters != null && !namedParameters.isEmpty())
            metadata.put("namedParameters", toJsonObject(namedParameters));

        // Column metadata
        JSONArray columnsJson = new JSONArray();
        for (String colName : sortedColumnNames)
        {
            JdbcType type = columnTypes.get(colName);
            JSONObject colJson = new JSONObject();
            colJson.put("name", colName);
            colJson.put("jdbcType", type != null ? type.name() : "OTHER");
            colJson.put("isKeyField", pkColumnNames.contains(colName));
            columnsJson.put(colJson);
        }
        metadata.put("columns", columnsJson);
        metadata.put("primaryKeyColumns", new JSONArray(pkColumnNames));
        metadata.put("syntheticPrimaryKey", syntheticPk);
        metadata.put("tsvFileName", tsvFile.getFileName().toString());

        // Write metadata JSON
        try
        {
            Files.writeString(metaFile, metadata.toString(2), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write metadata file: " + e.getMessage(), e);
        }

        LOG.info("Captured {} rows for {}.{} to {}", rowCount[0], schemaName, queryName, tsvFile.getFileName());

        return new CaptureResult(metadata, tsvFile, metaFile);
    }

    // ---- Diff ----

    /**
     * Compare live query data against a previously captured TSV baseline.
     */
    public JSONObject diffAgainstBaseline(User user, Container container, String baselineMetaFileName,
                                          int queryTimeout, int maxDiffs, List<String> overridePkColumns)
    {
        if (queryTimeout <= 0)
            queryTimeout = DEFAULT_QUERY_TIMEOUT;
        if (maxDiffs <= 0)
            maxDiffs = 500;

        Path dir = getSchemaCompareDir(container);

        // Load metadata
        JSONObject baselineMeta = loadMetadataFile(dir, baselineMetaFileName);
        String schemaName = baselineMeta.getString("schemaName");
        String queryName = baselineMeta.getString("queryName");
        String tsvFileName = baselineMeta.getString("tsvFileName");

        // Load column info from metadata
        JSONArray columnsJson = baselineMeta.getJSONArray("columns");
        List<String> columnNames = new ArrayList<>();
        Map<String, JdbcType> columnTypes = new LinkedHashMap<>();
        for (int i = 0; i < columnsJson.length(); i++)
        {
            JSONObject colJson = columnsJson.getJSONObject(i);
            String name = colJson.getString("name");
            columnNames.add(name);
            String jdbcTypeName = colJson.optString("jdbcType", "OTHER");
            try
            {
                columnTypes.put(name, JdbcType.valueOf(jdbcTypeName));
            }
            catch (IllegalArgumentException e)
            {
                columnTypes.put(name, JdbcType.OTHER);
            }
        }

        // Primary key columns: user override > baseline metadata > synthetic (first 3 columns)
        List<String> pkColumns;
        boolean syntheticPk;

        if (overridePkColumns != null && !overridePkColumns.isEmpty())
        {
            pkColumns = overridePkColumns;
            syntheticPk = false;
        }
        else
        {
            JSONArray pkJson = baselineMeta.optJSONArray("primaryKeyColumns");
            List<String> metaPkColumns = new ArrayList<>();
            if (pkJson != null)
            {
                for (int i = 0; i < pkJson.length(); i++)
                    metaPkColumns.add(pkJson.getString(i));
            }
            pkColumns = getEffectivePkColumns(metaPkColumns, columnNames);
            syntheticPk = metaPkColumns.isEmpty();
        }

        // Load baseline TSV into map
        Map<String, Map<String, String>> baselineRows = loadBaselineTsv(dir, tsvFileName, columnNames, pkColumns);
        long baselineRowCount = baselineRows.size();

        // Read stored named parameters from baseline metadata
        Map<String, Object> namedParameters = getNamedParameters(baselineMeta);

        // Track which baseline rows we've seen
        Map<String, Boolean> baselineSeen = new LinkedHashMap<>();
        for (String key : baselineRows.keySet())
            baselineSeen.put(key, false);

        // Diff accumulators
        JSONArray addedRows = new JSONArray();
        JSONArray deletedRows = new JSONArray();
        JSONArray modifiedRows = new JSONArray();
        long[] liveRowCount = {0};
        long[] matchedCount = {0};

        int finalMaxDiffs = maxDiffs;
        boolean[] truncatedHolder = {false};

        // Wrap all DB operations in a transaction to ensure connections are returned to the pool
        try (DbScope.Transaction ignored = DbScope.getLabKeyScope().ensureTransaction())
        {
            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (schema == null)
                throw new IllegalArgumentException("Schema not found: " + schemaName);

            TableInfo tableInfo = resolveTableInfo(schema, queryName);
            if (tableInfo == null)
                throw new IllegalArgumentException("Query/table not found: " + schemaName + "." + queryName);

            CanonicalQueryDataService.CanonicalQueryPlan plan = _canonicalQueryDataService.createPlan(tableInfo);
            List<ColumnInfo> liveCols = plan.columns();
            Set<String> availableColumnNames = Set.copyOf(plan.columnNames());

            List<String> skippedColumns = columnNames.stream()
                .filter(name -> !availableColumnNames.contains(name))
                .collect(Collectors.toList());
            if (!skippedColumns.isEmpty())
                LOG.info("Skipping {} canonical column(s) absent from live diff shape: {}", skippedColumns.size(), skippedColumns);

            columnNames = columnNames.stream()
                .filter(availableColumnNames::contains)
                .collect(Collectors.toList());

            List<String> effectiveColumnNames = columnNames;

            int baselineRowLimit = baselineMeta.optInt("rowLimit", 0);
            TableSelector selector = _canonicalQueryDataService.createSelector(
                tableInfo, liveCols, baselineRowLimit, true, namedParameters, queryTimeout);

            selector.forEachMap(row ->
            {
                liveRowCount[0]++;

                Map<String, Object> rawRow = _canonicalQueryDataService.extractRow(row, effectiveColumnNames);
                Map<String, Object> normalizedLiveObj = _canonicalQueryDataService.normalizeRowForDiff(rawRow, columnTypes);
                Map<String, String> normalizedLive = new LinkedHashMap<>();
                normalizedLiveObj.forEach((key, value) -> normalizedLive.put(key, value == null ? null : value.toString()));

                // Build row key from PK columns (real or synthetic)
                String rowKey = buildPrimaryKey(normalizedLive, pkColumns);

                Map<String, String> baselineRow = baselineRows.get(rowKey);

                if (baselineRow == null)
                {
                    if (LOG.isDebugEnabled() && addedRows.length() < 5)
                        LOG.debug("Live row PK '{}' not found in baseline ({} baseline keys) for {}.{}",
                            rowKey, baselineRows.size(), schemaName, queryName);

                    // Added row
                    if (!truncatedHolder[0])
                    {
                        JSONObject addedRow = new JSONObject();
                        for (String colName : effectiveColumnNames)
                            addedRow.put(colName, displayValue(normalizedLive.get(colName)));
                        addedRows.put(addedRow);
                        if (addedRows.length() + deletedRows.length() + modifiedRows.length() >= finalMaxDiffs)
                            truncatedHolder[0] = true;
                    }
                }
                else
                {
                    baselineSeen.put(rowKey, true);

                    // Compare values
                    JSONObject changes = new JSONObject();
                    for (String colName : effectiveColumnNames)
                    {
                        String baseVal = baselineRow.get(colName);
                        String liveVal = normalizedLive.get(colName);

                        if (baseVal == null) baseVal = CanonicalQueryDataService.NULL_SENTINEL;
                        if (liveVal == null) liveVal = CanonicalQueryDataService.NULL_SENTINEL;

                        if (!baseVal.equals(liveVal))
                        {
                            JSONObject change = new JSONObject();
                            change.put("baseline", displayValue(baseVal));
                            change.put("live", displayValue(liveVal));
                            changes.put(colName, change);
                        }
                    }

                    if (!changes.isEmpty())
                    {
                        if (!truncatedHolder[0])
                        {
                            JSONObject modifiedRow = new JSONObject();
                            JSONObject pkObj = new JSONObject();
                            for (String pkCol : pkColumns)
                                pkObj.put(pkCol, displayValue(normalizedLive.get(pkCol)));
                            modifiedRow.put("primaryKey", pkObj);
                            modifiedRow.put("changes", changes);
                            modifiedRows.put(modifiedRow);
                            if (addedRows.length() + deletedRows.length() + modifiedRows.length() >= finalMaxDiffs)
                                truncatedHolder[0] = true;
                        }
                    }
                    else
                    {
                        matchedCount[0]++;
                    }
                }
            });

            ignored.commit();
        }

        // Deleted rows: baseline rows not seen in live
        for (Map.Entry<String, Boolean> entry : baselineSeen.entrySet())
        {
            if (!entry.getValue())
            {
                if (!truncatedHolder[0])
                {
                    Map<String, String> baselineRow = baselineRows.get(entry.getKey());
                    JSONObject deletedRow = new JSONObject();
                    for (String colName : columnNames)
                        deletedRow.put(colName, displayValue(baselineRow.get(colName)));
                    deletedRows.put(deletedRow);
                    if (addedRows.length() + deletedRows.length() + modifiedRows.length() >= finalMaxDiffs)
                        truncatedHolder[0] = true;
                }
            }
        }

        // Build report
        JSONObject report = new JSONObject();

        JSONObject reportMeta = new JSONObject();
        reportMeta.put("schemaName", schemaName);
        reportMeta.put("queryName", queryName);
        reportMeta.put("baselineDatabase", baselineMeta.optString("database", "unknown"));
        reportMeta.put("baselineCapturedAt", baselineMeta.optString("capturedAt", "unknown"));
        reportMeta.put("liveDatabase", detectDatabaseType());
        reportMeta.put("comparedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        reportMeta.put("baselineFileName", baselineMetaFileName);
        reportMeta.put("hasPrimaryKey", !syntheticPk);
        reportMeta.put("syntheticPrimaryKey", syntheticPk);
        reportMeta.put("primaryKeyColumns", new JSONArray(pkColumns));
        if (syntheticPk)
            reportMeta.put("warning", "No primary key — comparison used first " + pkColumns.size() + " columns as synthetic key");
        if (namedParameters != null && !namedParameters.isEmpty())
            reportMeta.put("namedParameters", toJsonObject(namedParameters));
        report.put("metadata", reportMeta);

        JSONObject summary = new JSONObject();
        summary.put("baselineRowCount", baselineRowCount);
        summary.put("liveRowCount", liveRowCount[0]);
        summary.put("matchedRows", matchedCount[0]);
        summary.put("addedRows", addedRows.length());
        summary.put("deletedRows", deletedRows.length());
        summary.put("modifiedRows", modifiedRows.length());
        summary.put("diffsTruncated", truncatedHolder[0]);
        report.put("summary", summary);

        report.put("added", addedRows);
        report.put("deleted", deletedRows);
        report.put("modified", modifiedRows);

        // Save diff report
        saveDiffReport(container, schemaName, queryName, report);

        LOG.info("Diff complete for {}.{}: {} baseline rows, {} live rows, {} matched, {} added, {} deleted, {} modified",
            schemaName, queryName, baselineRowCount, liveRowCount[0], matchedCount[0],
            addedRows.length(), deletedRows.length(), modifiedRows.length());

        return report;
    }

    // ---- Baseline listing ----

    public JSONObject listBaselines(Container container)
    {
        JSONArray baselines = new JSONArray();
        Path dir = getSchemaCompareDir(container);

        if (Files.exists(dir))
        {
            try (Stream<Path> files = Files.list(dir))
            {
                files
                    .filter(p -> p.getFileName().toString().endsWith(".meta.json"))
                    .filter(p -> p.getFileName().toString().startsWith("query-baseline-"))
                    .sorted((a, b) -> {
                        try
                        {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        }
                        catch (IOException e)
                        {
                            return 0;
                        }
                    })
                    .limit(MAX_BASELINE_FILES)
                    .forEach(metaFile ->
                    {
                        try
                        {
                            String content = Files.readString(metaFile, StandardCharsets.UTF_8);
                            JSONObject meta = new JSONObject(content);
                            JSONObject entry = new JSONObject();
                            entry.put("fileName", metaFile.getFileName().toString());
                            entry.put("schemaName", meta.optString("schemaName", ""));
                            entry.put("queryName", meta.optString("queryName", ""));
                            entry.put("database", meta.optString("database", ""));
                            entry.put("capturedAt", meta.optString("capturedAt", ""));
                            entry.put("rowCount", meta.optLong("rowCount", 0));
                            Map<String, Object> namedParameters = getNamedParameters(meta);
                            if (namedParameters != null && !namedParameters.isEmpty())
                                entry.put("namedParameters", toJsonObject(namedParameters));
                            baselines.put(entry);
                        }
                        catch (Exception e)
                        {
                            LOG.warn("Failed to read baseline metadata {}: {}", metaFile.getFileName(), e.getMessage());
                        }
                    });
            }
            catch (IOException e)
            {
                LOG.warn("Failed to list baselines: {}", e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("baselines", baselines);
        result.put("liveDatabase", detectDatabaseType());
        return result;
    }

    public JSONArray getQueryParameters(User user, Container container, String schemaName, String queryName)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
        if (schema == null)
            throw new IllegalArgumentException("Schema not found: " + schemaName);

        TableInfo tableInfo = resolveTableInfo(schema, queryName);
        if (tableInfo == null)
            throw new IllegalArgumentException("Query/table not found: " + schemaName + "." + queryName);

        JSONArray parameters = new JSONArray();
        Collection<QueryService.ParameterDecl> paramDecls = tableInfo.getNamedParameters();
        for (QueryService.ParameterDecl param : paramDecls)
        {
            JSONObject paramJson = new JSONObject();
            paramJson.put("name", param.getName());
            JdbcType jdbcType = param.getJdbcType();
            paramJson.put("type", jdbcType != null ? jdbcType.name() : JdbcType.VARCHAR.name());
            paramJson.put("required", param.isRequired());
            parameters.put(paramJson);
        }

        return parameters;
    }

    // ---- Utilities ----

    @Nullable
    private TableInfo resolveTableInfo(UserSchema schema, String queryName)
    {
        try
        {
            QueryDefinition qdef = schema.getQueryDefForTable(queryName);
            if (qdef != null)
            {
                List<QueryException> qerrors = new ArrayList<>();
                TableInfo tableInfo = qdef.getTable(schema, qerrors, true);
                if (!qerrors.isEmpty())
                    throw new IllegalArgumentException("Query parse error: " + qerrors.get(0).getMessage());
                return tableInfo;
            }
            else
            {
                return schema.getTable(queryName);
            }
        }
        catch (IllegalArgumentException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("Failed to resolve table: " + e.getMessage(), e);
        }
    }

    @Nullable
    private Map<String, Object> getNamedParameters(JSONObject metadata)
    {
        Map<String, Object> namedParameters = new LinkedHashMap<>();

        JSONObject paramsJson = metadata.optJSONObject("namedParameters");
        if (paramsJson != null)
        {
            for (String key : paramsJson.keySet())
                addNamedParameter(namedParameters, key, paramsJson.opt(key));
        }

        addNamedParameter(namedParameters, "StartDate", metadata.opt("startDate"));
        addNamedParameter(namedParameters, "EndDate", metadata.opt("endDate"));

        return namedParameters.isEmpty() ? null : namedParameters;
    }

    private void addNamedParameter(Map<String, Object> namedParameters, String key, @Nullable Object rawValue)
    {
        if (key == null || namedParameters.containsKey(key) || rawValue == null || rawValue == JSONObject.NULL)
            return;

        String value = rawValue.toString().trim();
        if (!value.isEmpty())
            namedParameters.put(key, value);
    }

    private JSONObject toJsonObject(Map<String, Object> namedParameters)
    {
        JSONObject paramsJson = new JSONObject();
        for (Map.Entry<String, Object> entry : namedParameters.entrySet())
            paramsJson.put(entry.getKey(), entry.getValue());
        return paramsJson;
    }

    private Map<String, Map<String, String>> loadBaselineTsv(Path dir, String tsvFileName,
                                                              List<String> columnNames, List<String> pkColumns)
    {
        // Path traversal protection
        if (tsvFileName.contains("..") || tsvFileName.contains("/") || tsvFileName.contains("\\"))
            throw new IllegalArgumentException("Invalid TSV file name");

        Path tsvFile = dir.resolve(tsvFileName);
        if (!tsvFile.normalize().startsWith(dir.normalize()))
            throw new IllegalArgumentException("Invalid TSV file name");

        if (!Files.exists(tsvFile))
            throw new IllegalArgumentException("Baseline TSV file not found: " + tsvFileName);

        // Always use PK-based keying (pkColumns already contains effective keys)
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(tsvFile, StandardCharsets.UTF_8))
        {
            String headerLine = reader.readLine();
            if (headerLine == null)
                return rows;

            String[] headers = parseTsvFields(headerLine);

            String line;
            while ((line = reader.readLine()) != null)
            {
                String[] values = parseTsvFields(line);
                Map<String, String> rowData = new LinkedHashMap<>();

                for (int i = 0; i < headers.length && i < values.length; i++)
                    rowData.put(headers[i], values[i]);

                String rowKey = buildPrimaryKey(rowData, pkColumns);
                rows.put(rowKey, rowData);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read baseline TSV: " + e.getMessage(), e);
        }

        return rows;
    }

    private JSONObject loadMetadataFile(Path dir, String metaFileName)
    {
        if (metaFileName.contains("..") || metaFileName.contains("/") || metaFileName.contains("\\"))
            throw new IllegalArgumentException("Invalid metadata file name");

        if (!metaFileName.endsWith(".meta.json"))
            throw new IllegalArgumentException("Metadata file must have .meta.json extension");

        Path metaFile = dir.resolve(metaFileName);
        if (!metaFile.normalize().startsWith(dir.normalize()))
            throw new IllegalArgumentException("Invalid metadata file name");

        if (!Files.exists(metaFile))
            throw new IllegalArgumentException("Metadata file not found: " + metaFileName);

        try
        {
            String content = Files.readString(metaFile, StandardCharsets.UTF_8);
            JSONObject meta = new JSONObject(content);

            if (!meta.has("schemaName") || !meta.has("queryName") || !meta.has("tsvFileName"))
                throw new IllegalArgumentException("Metadata file missing required fields");

            return meta;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to read metadata file: " + e.getMessage(), e);
        }
    }

    private void saveDiffReport(Container container, String schemaName, String queryName, JSONObject report)
    {
        try
        {
            Path dir = getSchemaCompareDir(container);
            Files.createDirectories(dir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            String safeSchema = sanitizeFileName(schemaName);
            String safeQuery = sanitizeFileName(queryName);
            Path file = dir.resolve("query-diff-" + safeSchema + "-" + safeQuery + "-" + timestamp + ".json");
            Files.writeString(file, report.toString(2), StandardCharsets.UTF_8);
            LOG.info("Saved diff report to {}", file);
        }
        catch (Exception e)
        {
            LOG.warn("Failed to save diff report: {}", e.getMessage());
        }
    }

    private Path getSchemaCompareDir(Container container)
    {
        FileContentService fcs = FileContentService.get();
        if (fcs == null)
            throw new IllegalStateException("FileContentService is not available");

        Path fileRoot = fcs.getFileRootPath(container);
        if (fileRoot == null)
            throw new IllegalStateException("No file root configured for this container");

        return fileRoot.resolve("schemaCompare");
    }

    private String detectDatabaseType()
    {
        return DbScope.getLabKeyScope().getSqlDialect().isPostgreSQL() ? "pgsql" : "mssql";
    }

    private static List<String> getEffectivePkColumns(List<String> pkColumns, List<String> columnNames)
    {
        if (!pkColumns.isEmpty())
            return pkColumns;
        // Use first 3 columns as synthetic PK when no real PK exists
        return columnNames.subList(0, Math.min(3, columnNames.size()));
    }

    private static String buildPrimaryKey(Map<String, String> row, List<String> pkColumns)
    {
        if (pkColumns.size() == 1)
            return row.getOrDefault(pkColumns.get(0), "");

        return pkColumns.stream()
            .map(col -> row.getOrDefault(col, ""))
            .collect(Collectors.joining("|"));
    }

    private static Object displayValue(String val)
    {
        if (val == null || CanonicalQueryDataService.NULL_SENTINEL.equals(val))
            return JSONObject.NULL;
        return val;
    }

    private static String sanitizeFileName(String name)
    {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Parse a TSV line into fields, respecting RFC-4180 double-quote escaping
     * produced by {@link TSVMapWriter} / {@link org.labkey.api.data.TSVWriter}.
     */
    private static String[] parseTsvFields(String line)
    {
        List<String> fields = new ArrayList<>();
        int i = 0;
        int len = line.length();

        while (i <= len)
        {
            if (i == len)
            {
                fields.add("");
                break;
            }

            if (line.charAt(i) == '"')
            {
                // Quoted field
                StringBuilder sb = new StringBuilder();
                i++; // skip opening quote
                while (i < len)
                {
                    char ch = line.charAt(i);
                    if (ch == '"')
                    {
                        if (i + 1 < len && line.charAt(i + 1) == '"')
                        {
                            sb.append('"');
                            i += 2;
                        }
                        else
                        {
                            i++; // skip closing quote
                            break;
                        }
                    }
                    else
                    {
                        sb.append(ch);
                        i++;
                    }
                }
                fields.add(sb.toString());
                if (i < len && line.charAt(i) == '\t')
                    i++;
                else if (i >= len)
                    break;
            }
            else
            {
                // Unquoted field
                int start = i;
                while (i < len && line.charAt(i) != '\t')
                    i++;
                fields.add(line.substring(start, i));
                if (i < len)
                    i++;
                else
                    break;
            }
        }

        return fields.toArray(new String[0]);
    }
}
