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
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleSchemaTreeVisitor;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.logging.LogHelper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core logic for schema comparison: capturing baseline snapshots and comparing against baselines.
 * Supports multi-threaded execution via a bounded thread pool for parallel query processing.
 */
public class SchemaCompareService
{
    private static final Logger LOG = LogHelper.getLogger(SchemaCompareService.class, "Schema comparison operations");
    static final int DEFAULT_CHECKSUM_ROW_LIMIT = 100;
    static final int DEFAULT_CONCURRENCY = 20;
    private static final int MAX_CONCURRENCY = 20;
    static final int DEFAULT_QUERY_TIMEOUT = 120; // seconds
    private final CanonicalQueryDataService _canonicalQueryDataService = new CanonicalQueryDataService();

    // Result records for thread-safe collection
    private record QueryCaptureResult(String schemaName, String queryName, JSONObject result) {}
    private record QueryCompareResult(String schemaName, String queryName, JSONObject result, String status) {}
    private record CaptureTask(String schemaName, String queryName, Callable<QueryCaptureResult> callable) {}
    private record CompareTask(String schemaName, String queryName, Callable<QueryCompareResult> callable) {}
    private record ChecksumColumnRef(String key, String name) {}
    private record ChecksumColumnSelection(ColumnInfo column, String checksumKey) {}

    // ---- Capture ----

    /**
     * Capture a full baseline snapshot of all schemas/queries in the container.
     * If schemaFilter is non-null, only capture that one schema (case-insensitive).
     * If queryFilter is non-null, only capture that one query within the filtered schema.
     */
    public JSONObject captureBaseline(User user, Container container, boolean skipChecksums, int concurrency, int queryTimeout, int checksumRowLimit,
                                      @Nullable String schemaFilter, @Nullable String queryFilter)
    {
        concurrency = clampConcurrency(concurrency);
        boolean debugChecksums = schemaFilter != null && queryFilter != null;

        JSONObject baseline = new JSONObject();

        // Metadata
        JSONObject metadata = new JSONObject();
        metadata.put("capturedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        metadata.put("baseUrl", AppProps.getInstance().getBaseServerUrl());
        metadata.put("containerPath", container.getPath());
        metadata.put("database", detectDatabaseType());
        metadata.put("skipChecksums", skipChecksums);
        metadata.put("concurrency", concurrency);
        metadata.put("queryTimeout", queryTimeout);
        metadata.put("checksumRowLimit", checksumRowLimit);
        if (schemaFilter != null)
            metadata.put("schema", schemaFilter);
        if (queryFilter != null)
            metadata.put("query", queryFilter);
        baseline.put("metadata", metadata);

        // Enumerate all schemas and build flat task list
        List<SchemaInfo> schemas = enumerateSchemas(user, container);
        LOG.info("Schema compare capture: found {} schemas in {} (concurrency={})", schemas.size(), container.getPath(), concurrency);

        // Pre-create schema entries and collect all query tasks
        JSONObject schemasJson = new JSONObject();
        JSONArray errorsJson = new JSONArray();
        List<CaptureTask> tasks = new ArrayList<>();
        AtomicInteger captureProgress = new AtomicInteger(0);
        int[] totalHolder = new int[1];
        Map<String, Integer> perSchemaQueryCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (SchemaInfo schemaInfo : schemas)
        {
            UserSchema schema = schemaInfo.schema;
            String schemaName = schemaInfo.fullyQualifiedName;

            // Apply schema filter
            if (schemaFilter != null && !schemaFilter.equalsIgnoreCase(schemaName))
                continue;

            schemasJson.put(schemaName, new JSONObject().put("queries", new JSONObject()));

            Set<String> allQueryNames = new LinkedHashSet<>();
            try
            {
                allQueryNames.addAll(schema.getTableNames());
            }
            catch (Exception e)
            {
                LOG.warn("Failed to get table names for schema {}, retrying: {}", schemaName, e.getMessage());
                try
                {
                    allQueryNames.addAll(schema.getTableNames());
                }
                catch (Exception e2)
                {
                    LOG.error("Failed to get table names for schema {} on retry: {}", schemaName, e2.getMessage());
                    errorsJson.put(makeError(schemaName, null, "Failed to get tables: " + e2.getMessage()));
                    // Fall through to getQueryDefs() instead of skipping the entire schema
                }
            }

            Set<String> userDefinedQueryNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            try
            {
                Map<String, QueryDefinition> queryDefs = schema.getQueryDefs();
                if (queryDefs != null)
                {
                    allQueryNames.addAll(queryDefs.keySet());
                    userDefinedQueryNames.addAll(queryDefs.keySet());
                }
            }
            catch (Exception e)
            {
                LOG.warn("Failed to get query defs for schema {}, retrying: {}", schemaName, e.getMessage());
                try
                {
                    Map<String, QueryDefinition> queryDefs = schema.getQueryDefs();
                    if (queryDefs != null)
                    {
                        allQueryNames.addAll(queryDefs.keySet());
                        userDefinedQueryNames.addAll(queryDefs.keySet());
                    }
                }
                catch (Exception e2)
                {
                    LOG.error("Failed to get query defs for schema {} on retry: {}", schemaName, e2.getMessage());
                    errorsJson.put(makeError(schemaName, null, "Failed to get custom queries: " + e2.getMessage()));
                }
            }

            int schemaQueryCount = 0;
            User schemaUser = schemaInfo.user;
            Container schemaContainer = schemaInfo.container;
            for (String queryName : allQueryNames)
            {
                // Apply query filter
                if (queryFilter != null && !queryFilter.equalsIgnoreCase(queryName))
                    continue;

                tasks.add(new CaptureTask(schemaName, queryName, () ->
                {
                    int n = captureProgress.incrementAndGet();
                    LOG.info("Capturing {}.{} ({}/{})", schemaName, queryName, n, totalHolder[0]);
                    // Resolve a fresh UserSchema per task to avoid thread-safety issues
                    UserSchema threadSchema = resolveSchemaForThread(schemaUser, schemaContainer, schemaName);
                    JSONObject result = captureQuery(threadSchema, schemaName, queryName, userDefinedQueryNames, skipChecksums, queryTimeout, checksumRowLimit, debugChecksums);
                    return new QueryCaptureResult(schemaName, queryName, result);
                }));
                schemaQueryCount++;
            }
            perSchemaQueryCounts.put(schemaName, schemaQueryCount);
        }

        totalHolder[0] = tasks.size();

        LOG.info("Total capture tasks: {}", tasks.size());
        if (LOG.isDebugEnabled())
        {
            perSchemaQueryCounts.forEach((name, count) ->
                LOG.debug("  Schema {}: {} queries", name, count));
        }

        // Execute tasks with thread pool
        executeCaptureTasks(tasks, schemasJson, errorsJson, concurrency, queryTimeout);

        baseline.put("schemas", schemasJson);
        baseline.put("errors", errorsJson);

        return baseline;
    }

    private void executeCaptureTasks(List<CaptureTask> tasks,
                                     JSONObject schemasJson, JSONArray errorsJson, int concurrency, int queryTimeout)
    {
        if (tasks.isEmpty())
            return;

        Object envState = QueryService.get().cloneEnvironment();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try
        {
            // Submit tasks and track their identity alongside the future
            record FutureWithIdentity(Future<QueryCaptureResult> future, String schemaName, String queryName) {}
            List<FutureWithIdentity> futuresWithId = new ArrayList<>(tasks.size());

            for (CaptureTask task : tasks)
                futuresWithId.add(new FutureWithIdentity(
                    executor.submit(() ->
                    {
                        QueryService.get().copyEnvironment(envState);
                        try
                        {
                            return task.callable.call();
                        }
                        finally
                        {
                            QueryService.get().clearEnvironment();
                        }
                    }),
                    task.schemaName, task.queryName));

            for (FutureWithIdentity fwi : futuresWithId)
            {
                try
                {
                    QueryCaptureResult qcr = queryTimeout > 0
                        ? fwi.future.get(queryTimeout, TimeUnit.SECONDS)
                        : fwi.future.get();
                    schemasJson.getJSONObject(qcr.schemaName).getJSONObject("queries")
                        .put(qcr.queryName, qcr.result);

                    String error = qcr.result.optString("error", null);
                    if (error != null)
                        errorsJson.put(makeError(qcr.schemaName, qcr.queryName, error));
                }
                catch (TimeoutException e)
                {
                    fwi.future.cancel(true);
                    LOG.warn("Query capture task timed out after {}s: {}.{}", queryTimeout, fwi.schemaName, fwi.queryName);
                    errorsJson.put(makeError(fwi.schemaName, fwi.queryName, "Query timed out after " + queryTimeout + "s"));
                }
                catch (CancellationException e)
                {
                    LOG.warn("Query capture task was cancelled: {}.{}", fwi.schemaName, fwi.queryName);
                }
                catch (ExecutionException e)
                {
                    LOG.error("Query capture task failed for {}.{}: {}", fwi.schemaName, fwi.queryName, e.getCause().getMessage());
                    errorsJson.put(makeError(fwi.schemaName, fwi.queryName, "Task failed: " + e.getCause().getMessage()));
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Capture interrupted", e);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private record TableResolution(@Nullable TableInfo tableInfo, boolean isUserDefined, boolean isHidden, @Nullable String error) {}

    private TableResolution resolveTableInfo(UserSchema schema, String queryName, Set<String> userDefinedQueryNames)
    {
        try
        {
            QueryDefinition qdef = schema.getQueryDefForTable(queryName);
            boolean isUserDefined = userDefinedQueryNames.contains(queryName);
            boolean isHidden = false;

            if (qdef != null)
            {
                isHidden = qdef.isHidden();

                List<QueryException> qerrors = new ArrayList<>();
                TableInfo tableInfo = qdef.getTable(schema, qerrors, true);
                if (!qerrors.isEmpty())
                    return new TableResolution(null, isUserDefined, isHidden,
                        "Query parse error: " + qerrors.get(0).getMessage());

                if (tableInfo == null)
                    return new TableResolution(null, isUserDefined, isHidden, "Table not found");

                return new TableResolution(tableInfo, isUserDefined, isHidden, null);
            }
            else
            {
                TableInfo tableInfo = schema.getTable(queryName);
                if (tableInfo == null)
                    return new TableResolution(null, isUserDefined, false, "Table not found");

                return new TableResolution(tableInfo, isUserDefined, false, null);
            }
        }
        catch (Exception e)
        {
            return new TableResolution(null, userDefinedQueryNames.contains(queryName), false, "Failed to get table: " + e.getMessage());
        }
    }

    private JSONObject captureQuery(UserSchema schema, String schemaName, String queryName, Set<String> userDefinedQueryNames, boolean skipChecksums, int queryTimeout, int checksumRowLimit, boolean debugChecksums)
    {
        JSONObject result = new JSONObject();
        result.put("isUserDefined", false);
        result.put("hidden", false);
        result.put("rowCount", JSONObject.NULL);
        result.put("columns", new JSONArray());
        result.put("checksum", JSONObject.NULL);
        result.put("checksumMethod", JSONObject.NULL);
        result.put("error", JSONObject.NULL);

        TableResolution resolution = resolveTableInfo(schema, queryName, userDefinedQueryNames);
        result.put("isUserDefined", resolution.isUserDefined);
        result.put("hidden", resolution.isHidden);

        if (resolution.error != null)
        {
            result.put("error", resolution.error);
            return result;
        }

        TableInfo tableInfo = resolution.tableInfo;

        // Detect named parameters and build default values for date params
        Map<String, Object> paramValues = null;
        Collection<QueryService.ParameterDecl> params = tableInfo.getNamedParameters();
        if (!params.isEmpty())
        {
            JSONArray paramsJson = new JSONArray();
            for (QueryService.ParameterDecl param : params)
            {
                JSONObject p = new JSONObject();
                p.put("name", param.getName());
                p.put("type", param.getJdbcType().name());
                p.put("required", param.isRequired());
                paramsJson.put(p);
            }
            result.put("parameters", paramsJson);

            // Check if all params are date types — if so, supply defaults
            List<QueryService.ParameterDecl> dateParams = new ArrayList<>();
            boolean allDate = true;
            for (QueryService.ParameterDecl param : params)
            {
                JdbcType t = param.getJdbcType();
                if (t == JdbcType.DATE || t == JdbcType.TIMESTAMP)
                    dateParams.add(param);
                else
                    allDate = false;
            }

            if (allDate && dateParams.size() >= 1 && dateParams.size() <= 2)
            {
                paramValues = new HashMap<>();
                if (dateParams.size() == 1)
                {
                    paramValues.put(dateParams.get(0).getName(), "2026-01-01");
                }
                else
                {
                    paramValues.put(dateParams.get(0).getName(), "2025-12-01");
                    paramValues.put(dateParams.get(1).getName(), "2026-01-01");
                }
                result.put("defaultDateParams", true);
            }
            else
            {
                result.put("skippedReason", "requires parameters");
                return result;
            }
        }

        // Row count
        try
        {
            TableSelector rowCountSelector = new TableSelector(tableInfo);
            if (queryTimeout > 0)
                rowCountSelector.setQueryTimeout(queryTimeout);
            if (paramValues != null)
                rowCountSelector.setNamedParameters(paramValues);
            long rowCount = rowCountSelector.getRowCount();
            result.put("rowCount", rowCount);
        }
        catch (Exception e)
        {
            result.put("error", "Row count failed: " + e.getMessage());
            return result;
        }

        // Column metadata — exclude only calculated columns with non-deterministic functions
        List<ColumnInfo> checksumColumns = getChecksumColumnsForCapture(tableInfo);
        JSONArray columnsJson = getColumnMetadata(checksumColumns);
        result.put("columns", columnsJson);
        result.put("checksumColumns", toJsonArray(getChecksumColumnNames(checksumColumns)));
        result.put("checksumColumnKeys", getChecksumColumnKeys(checksumColumns));

        // Check for non-deterministic functions in the query's FROM SQL
        boolean queryHasNonDeterministicFromSql = queryFromSqlContainsNonDeterministicFunction(tableInfo);
        if (queryHasNonDeterministicFromSql)
            result.put("checksumSkippedReason", "query contains non-deterministic function");

        // Checksum
        long rowCount = result.getLong("rowCount");
        if (!skipChecksums && !queryHasNonDeterministicFromSql && rowCount > 0)
        {
            try
            {
                long rowsToHash = Math.min(rowCount, checksumRowLimit);
                result.put("checksumMethod", "first " + rowsToHash + " of " + rowCount + " rows");

                String checksum = computeChecksum(tableInfo, createChecksumSelections(checksumColumns), paramValues, queryTimeout, checksumRowLimit, schemaName, queryName, debugChecksums);
                result.put("checksum", checksum);
            }
            catch (Exception e)
            {
                result.put("error", "Checksum failed: " + e.getMessage());
            }
        }

        return result;
    }

    private JSONArray getColumnMetadata(List<ColumnInfo> columns)
    {
        JSONArray columnsArr = new JSONArray();
        for (ColumnInfo col : columns)
        {
            JSONObject colJson = new JSONObject();
            colJson.put("name", col.getName());
            colJson.put("jsonType", col.getJdbcType().json);
            colJson.put("sqlType", col.getSqlTypeName());
            colJson.put("isKeyField", col.isKeyField());
            columnsArr.put(colJson);
        }
        return columnsArr;
    }

    private JSONObject compareColumnMetadata(JSONArray baselineColumns, JSONArray liveColumns)
    {
        Map<String, JSONObject> baselineMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < baselineColumns.length(); i++)
        {
            JSONObject col = baselineColumns.getJSONObject(i);
            baselineMap.put(col.getString("name"), col);
        }

        Map<String, JSONObject> liveMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < liveColumns.length(); i++)
        {
            JSONObject col = liveColumns.getJSONObject(i);
            liveMap.put(col.getString("name"), col);
        }

        JSONArray missingColumns = new JSONArray();
        JSONArray newColumns = new JSONArray();
        JSONArray typeChanges = new JSONArray();
        JSONArray keyFieldChanges = new JSONArray();

        for (String name : baselineMap.keySet())
        {
            if (!liveMap.containsKey(name))
                missingColumns.put(name);
        }

        for (String name : liveMap.keySet())
        {
            if (!baselineMap.containsKey(name))
                newColumns.put(name);
        }

        for (String name : baselineMap.keySet())
        {
            if (!liveMap.containsKey(name))
                continue;

            JSONObject baselineCol = baselineMap.get(name);
            JSONObject liveCol = liveMap.get(name);

            String baselineType = baselineCol.optString("jsonType", "");
            String liveType = liveCol.optString("jsonType", "");
            if (!baselineType.equals(liveType))
            {
                JSONObject change = new JSONObject();
                change.put("name", name);
                change.put("baseline", baselineType);
                change.put("live", liveType);
                typeChanges.put(change);
            }

            boolean baselineKey = baselineCol.optBoolean("isKeyField", false);
            boolean liveKey = liveCol.optBoolean("isKeyField", false);
            if (baselineKey != liveKey)
            {
                JSONObject change = new JSONObject();
                change.put("name", name);
                change.put("baseline", baselineKey);
                change.put("live", liveKey);
                keyFieldChanges.put(change);
            }
        }

        boolean match = missingColumns.isEmpty() && newColumns.isEmpty() && typeChanges.isEmpty() && keyFieldChanges.isEmpty();

        JSONObject result = new JSONObject();
        result.put("match", match);
        result.put("missingColumns", missingColumns);
        result.put("newColumns", newColumns);
        result.put("typeChanges", typeChanges);
        result.put("keyFieldChanges", keyFieldChanges);
        return result;
    }

    private List<ColumnInfo> getChecksumColumnsForCapture(TableInfo tableInfo)
    {
        return _canonicalQueryDataService.getCanonicalColumns(tableInfo);
    }

    private JSONArray getChecksumColumnKeys(List<ColumnInfo> columns)
    {
        JSONArray array = new JSONArray();
        for (ColumnInfo column : columns)
        {
            JSONObject json = new JSONObject();
            json.put("key", getChecksumColumnKey(column));
            json.put("name", getChecksumColumnName(column));
            array.put(json);
        }
        return array;
    }

    private JSONArray getChecksumColumnKeysFromSelections(List<ChecksumColumnSelection> selections)
    {
        JSONArray array = new JSONArray();
        for (ChecksumColumnSelection selection : selections)
        {
            JSONObject json = new JSONObject();
            json.put("key", selection.checksumKey());
            json.put("name", getChecksumColumnName(selection.column()));
            array.put(json);
        }
        return array;
    }

    private List<ChecksumColumnRef> getChecksumColumnRefs(@Nullable JSONArray baselineChecksumKeys, @Nullable JSONArray baselineChecksumCols)
    {
        if (baselineChecksumKeys != null && !baselineChecksumKeys.isEmpty())
        {
            List<ChecksumColumnRef> refs = new ArrayList<>(baselineChecksumKeys.length());
            for (int i = 0; i < baselineChecksumKeys.length(); i++)
            {
                JSONObject ref = baselineChecksumKeys.getJSONObject(i);
                refs.add(new ChecksumColumnRef(
                    normalizeChecksumIdentifier(ref.getString("key")),
                    normalizeChecksumIdentifier(ref.optString("name", ref.getString("key")))));
            }
            return refs;
        }

        if (baselineChecksumCols == null || baselineChecksumCols.isEmpty())
            return List.of();

        List<ChecksumColumnRef> refs = new ArrayList<>(baselineChecksumCols.length());
        for (int i = 0; i < baselineChecksumCols.length(); i++)
        {
            String name = normalizeChecksumIdentifier(baselineChecksumCols.getString(i));
            refs.add(new ChecksumColumnRef(name, name));
        }
        return refs;
    }

    private List<ChecksumColumnSelection> createChecksumSelections(List<ColumnInfo> columns)
    {
        return columns.stream()
            .map(column -> new ChecksumColumnSelection(column, getChecksumColumnKey(column)))
            .collect(Collectors.toList());
    }

    private List<ChecksumColumnSelection> getChecksumColumnsForCompare(TableInfo tableInfo,
                                                                       @Nullable JSONArray baselineChecksumKeys,
                                                                       @Nullable JSONArray baselineChecksumCols)
    {
        List<ChecksumColumnRef> baselineRefs = getChecksumColumnRefs(baselineChecksumKeys, baselineChecksumCols);
        if (baselineRefs.isEmpty())
            return createChecksumSelections(getChecksumColumnsForCapture(tableInfo));

        Map<String, ColumnInfo> liveColumnsByKey = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, ColumnInfo> liveColumnsByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ColumnInfo col : _canonicalQueryDataService.getCanonicalColumns(tableInfo))
        {
            liveColumnsByKey.put(getChecksumColumnKey(col), col);
            liveColumnsByName.put(getChecksumColumnName(col), col);
        }

        List<ChecksumColumnSelection> selections = new ArrayList<>(baselineRefs.size());
        List<String> missingColumns = new ArrayList<>();
        for (ChecksumColumnRef baselineRef : baselineRefs)
        {
            ColumnInfo liveColumn = liveColumnsByKey.get(baselineRef.key());
            if (liveColumn == null)
                liveColumn = liveColumnsByName.get(baselineRef.name());

            if (liveColumn == null)
                missingColumns.add(baselineRef.key());
            else
                selections.add(new ChecksumColumnSelection(liveColumn, baselineRef.key()));
        }

        if (!missingColumns.isEmpty())
            throw new IllegalStateException("Baseline checksum columns are missing from live query: " + String.join(", ", missingColumns));

        return selections;
    }

    private String getChecksumColumnKey(ColumnInfo column)
    {
        FieldKey fieldKey = column.getFieldKey();
        return normalizeChecksumIdentifier(fieldKey != null ? fieldKey.toString() : column.getName());
    }

    private List<String> getChecksumColumnNames(List<ColumnInfo> columns)
    {
        return columns.stream()
            .map(this::getChecksumColumnName)
            .collect(Collectors.toList());
    }

    private String getChecksumColumnName(ColumnInfo column)
    {
        return normalizeChecksumIdentifier(column.getName());
    }

    private String normalizeChecksumIdentifier(String value)
    {
        return value.toLowerCase(Locale.ROOT);
    }

    private JSONArray toJsonArray(List<String> values)
    {
        JSONArray array = new JSONArray();
        for (String value : values)
            array.put(value);
        return array;
    }

    private String computeChecksum(TableInfo tableInfo, List<ChecksumColumnSelection> checksumColumns, @Nullable Map<String, Object> paramValues, int queryTimeout, int checksumRowLimit, String schemaName, String queryName, boolean debugChecksums)
    {
        if (checksumColumns.isEmpty())
            throw new IllegalStateException("No checksum columns available");

        Map<String, JdbcType> columnTypes = new HashMap<>();
        List<ColumnInfo> selectorColumns = new ArrayList<>(checksumColumns.size());
        for (ChecksumColumnSelection selection : checksumColumns)
        {
            columnTypes.put(selection.checksumKey(), selection.column().getJdbcType());
            selectorColumns.add(selection.column());
        }

        // Order-independent: hash each row individually, XOR all hashes together.
        // This eliminates false mismatches caused by different row ordering between databases.
        byte[] combinedHash = new byte[32]; // SHA-256 = 32 bytes

        // Debug logging for specific query to diagnose checksum mismatches
        boolean debugThisQuery = debugChecksums;
        List<String> debugRows = debugThisQuery ? new ArrayList<>() : null;
        int[] debugRowCount = {0};

        TableSelector selector = _canonicalQueryDataService.createSelector(
            tableInfo, selectorColumns, checksumRowLimit, checksumRowLimit > 0, paramValues, queryTimeout);


        selector.forEachMap(row ->
        {
            try
            {
                // Re-key the row to the captured checksum identifiers so capture and compare
                // hash the same logical columns even if selector output names differ.
                Map<String, Object> filteredRow = new LinkedHashMap<>();
                for (ChecksumColumnSelection selection : checksumColumns)
                    filteredRow.put(selection.checksumKey(), getRowValue(row, selection.column()));

                MessageDigest rowDigest = MessageDigest.getInstance("SHA-256");
                List<Map.Entry<String, Object>> normalized = _canonicalQueryDataService.normalizeEntries(filteredRow, columnTypes);
                String rowStr = normalizedEntriesToJson(normalized);
                if (debugThisQuery)
                {
                    debugRowCount[0]++;
                    if (debugRows.size() < 10)
                        debugRows.add(rowStr);
                }
                byte[] rowHash = rowDigest.digest(rowStr.getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < 32; i++)
                    combinedHash[i] ^= rowHash[i];
            }
            catch (NoSuchAlgorithmException e)
            {
                throw new RuntimeException("SHA-256 not available", e);
            }
        });

        if (debugThisQuery)
        {
            LOG.info("DEBUG checksum for {}.{}: {} rows", schemaName, queryName, debugRowCount[0]);
            for (int i = 0; i < debugRows.size(); i++)
                LOG.info("  row[{}]: {}", i, debugRows.get(i));
        }

        return "sha256-xor:" + bytesToHex(combinedHash);
    }

    private @Nullable Object getRowValue(Map<String, Object> row, ColumnInfo column)
    {
        List<String> candidateKeys = new ArrayList<>(3);
        candidateKeys.add(column.getName());
        candidateKeys.add(getChecksumColumnKey(column));
        if (column.getAlias() != null)
            candidateKeys.add(column.getAlias().getId());

        for (String candidateKey : candidateKeys)
        {
            if (row.containsKey(candidateKey))
                return row.get(candidateKey);
        }

        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            for (String candidateKey : candidateKeys)
            {
                if (entry.getKey().equalsIgnoreCase(candidateKey))
                    return entry.getValue();
            }
        }

        return null;
    }

    // ---- Compare ----

    /**
     * Compare the live instance against a previously captured baseline.
     * If schemaFilter is non-null, only compare that one schema (case-insensitive).
     * If queryFilter is non-null, only compare that one query within the filtered schema.
     */
    public JSONObject compareAgainstBaseline(User user, Container container, JSONObject baseline,
                                             boolean skipChecksums,
                                             @Nullable Set<String> expectedMissing,
                                             @Nullable Set<String> expectedDiffSchemas,
                                             int concurrency,
                                             int queryTimeout,
                                             int checksumRowLimit,
                                             @Nullable String schemaFilter,
                                             @Nullable String queryFilter)
    {
        concurrency = clampConcurrency(concurrency);
        boolean debugChecksums = schemaFilter != null && queryFilter != null;
        long startTime = System.currentTimeMillis();
        if (expectedMissing == null) expectedMissing = Set.of();
        if (expectedDiffSchemas == null) expectedDiffSchemas = Set.of();

        JSONObject report = new JSONObject();

        // Metadata
        JSONObject meta = new JSONObject();
        meta.put("baselineDatabase", baseline.optJSONObject("metadata") != null ?
            baseline.getJSONObject("metadata").optString("database", "unknown") : "unknown");
        meta.put("baselineCapturedAt", baseline.optJSONObject("metadata") != null ?
            baseline.getJSONObject("metadata").optString("capturedAt", "unknown") : "unknown");
        meta.put("liveBaseUrl", AppProps.getInstance().getBaseServerUrl());
        meta.put("liveContainerPath", container.getPath());
        meta.put("skipChecksums", skipChecksums);
        meta.put("concurrency", concurrency);
        meta.put("queryTimeout", queryTimeout);
        meta.put("checksumRowLimit", checksumRowLimit);
        if (schemaFilter != null)
            meta.put("schema", schemaFilter);
        if (queryFilter != null)
            meta.put("query", queryFilter);
        report.put("metadata", meta);

        // Summary counters
        JSONObject summary = new JSONObject();
        summary.put("schemasInBaseline", 0);
        summary.put("schemasMatched", 0);
        summary.put("schemasMissing", 0);
        summary.put("schemasNew", 0);
        summary.put("queriesCompared", 0);
        summary.put("queriesMatched", 0);
        summary.put("queriesNewInLive", 0);
        summary.put("rowCountMismatches", 0);
        summary.put("checksumMismatches", 0);
        summary.put("queriesSkipped", 0);
        summary.put("metadataMismatches", 0);
        summary.put("errors", 0);
        summary.put("errorsConsistent", 0);
        summary.put("errorsChanged", 0);
        summary.put("errorsResolved", 0);
        report.put("summary", summary);

        // Detail arrays
        JSONArray missingSchemas = new JSONArray();
        JSONArray newSchemas = new JSONArray();
        JSONArray newQueries = new JSONArray();
        JSONArray rowCountMismatches = new JSONArray();
        JSONArray checksumMismatches = new JSONArray();
        JSONArray metadataMismatches = new JSONArray();
        JSONArray queryErrors = new JSONArray();
        JSONArray resolvedErrors = new JSONArray();
        JSONArray consistentErrors = new JSONArray();
        JSONArray skippedQueries = new JSONArray();
        JSONObject details = new JSONObject();

        // Enumerate live schemas
        List<SchemaInfo> liveSchemaInfos = enumerateSchemas(user, container);
        Set<String> liveSchemaNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, UserSchema> liveSchemaMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SchemaInfo si : liveSchemaInfos)
        {
            // Apply schema filter to live schemas
            if (schemaFilter != null && !schemaFilter.equalsIgnoreCase(si.fullyQualifiedName))
                continue;
            liveSchemaNames.add(si.fullyQualifiedName);
            liveSchemaMap.put(si.fullyQualifiedName, si.schema);
        }

        // Get baseline schema names
        JSONObject baselineSchemas = baseline.optJSONObject("schemas");
        if (baselineSchemas == null)
            baselineSchemas = new JSONObject();

        Set<String> baselineSchemaNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        baselineSchemaNames.addAll(baselineSchemas.keySet());

        // Apply schema filter to baseline schemas
        if (schemaFilter != null)
            baselineSchemaNames.removeIf(name -> !schemaFilter.equalsIgnoreCase(name));

        summary.put("schemasInBaseline", baselineSchemaNames.size());

        // Schema set comparison
        Set<String> matchingSchemaNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String name : baselineSchemaNames)
        {
            if (liveSchemaNames.contains(name))
                matchingSchemaNames.add(name);
            else
            {
                JSONObject missing = new JSONObject();
                missing.put("name", name);
                missing.put("expected", expectedMissing.contains(name));
                missingSchemas.put(missing);
            }
        }
        for (String name : liveSchemaNames)
        {
            if (!baselineSchemaNames.contains(name))
                newSchemas.put(name);
        }

        summary.put("schemasMatched", matchingSchemaNames.size());
        summary.put("schemasMissing", missingSchemas.length());
        summary.put("schemasNew", newSchemas.length());

        // Build flat task list for query comparison across all matching schemas
        List<CompareTask> tasks = new ArrayList<>();
        AtomicInteger compareProgress = new AtomicInteger(0);
        int[] totalHolder = new int[1];

        for (String schemaName : matchingSchemaNames)
        {
            UserSchema liveSchema = liveSchemaMap.get(schemaName);
            JSONObject baselineSchema = baselineSchemas.getJSONObject(schemaName);
            JSONObject baselineQueries = baselineSchema.optJSONObject("queries");
            if (baselineQueries == null)
                baselineQueries = new JSONObject();

            // Get live query names
            Set<String> liveQueryNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            try
            {
                liveQueryNames.addAll(liveSchema.getTableNames());
            }
            catch (Exception e)
            {
                LOG.warn("Failed to get table names for schema {} during compare, retrying: {}", schemaName, e.getMessage());
                try
                {
                    liveQueryNames.addAll(liveSchema.getTableNames());
                }
                catch (Exception e2)
                {
                    LOG.error("Failed to get table names for schema {} on retry: {}", schemaName, e2.getMessage());
                    queryErrors.put(makeError(schemaName, null, "Failed to get tables: " + e2.getMessage()));
                    summary.put("errors", summary.getInt("errors") + 1);
                    // Fall through to getQueryDefs() instead of skipping the entire schema
                }
            }

            Set<String> liveUserDefinedQueryNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            try
            {
                Map<String, QueryDefinition> qDefs = liveSchema.getQueryDefs();
                if (qDefs != null)
                {
                    liveQueryNames.addAll(qDefs.keySet());
                    liveUserDefinedQueryNames.addAll(qDefs.keySet());
                }
            }
            catch (Exception e)
            {
                LOG.warn("Failed to get query defs for schema {} during compare, retrying: {}", schemaName, e.getMessage());
                try
                {
                    Map<String, QueryDefinition> qDefs = liveSchema.getQueryDefs();
                    if (qDefs != null)
                    {
                        liveQueryNames.addAll(qDefs.keySet());
                        liveUserDefinedQueryNames.addAll(qDefs.keySet());
                    }
                }
                catch (Exception e2)
                {
                    LOG.error("Failed to get query defs for schema {} on retry: {}", schemaName, e2.getMessage());
                    queryErrors.put(makeError(schemaName, null, "Failed to get custom queries: " + e2.getMessage()));
                    summary.put("errors", summary.getInt("errors") + 1);
                }
            }

            Set<String> baselineQueryNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            baselineQueryNames.addAll(baselineQueries.keySet());

            // Apply query filter
            if (queryFilter != null)
            {
                liveQueryNames.removeIf(name -> !queryFilter.equalsIgnoreCase(name));
                baselineQueryNames.removeIf(name -> !queryFilter.equalsIgnoreCase(name));
            }

            // Record missing and new queries (not parallelized — fast)
            for (String qName : baselineQueryNames)
            {
                if (liveQueryNames.contains(qName))
                {
                    // Build comparison task
                    boolean isExpectedDiffSchema = expectedDiffSchemas.contains(schemaName);
                    JSONObject baselineData = baselineQueries.getJSONObject(qName);
                    tasks.add(new CompareTask(schemaName, qName, () ->
                    {
                        int n = compareProgress.incrementAndGet();
                        LOG.info("Comparing {}.{} ({}/{})", schemaName, qName, n, totalHolder[0]);
                        // Resolve a fresh UserSchema per task to avoid thread-safety issues
                        UserSchema threadSchema = resolveSchemaForThread(user, container, schemaName);
                        JSONObject compResult = compareQueryData(threadSchema, schemaName, qName, baselineData,
                            liveUserDefinedQueryNames, skipChecksums, isExpectedDiffSchema, queryTimeout, checksumRowLimit, debugChecksums);
                        return new QueryCompareResult(schemaName, qName, compResult,
                            compResult.optString("status", "ok"));
                    }));
                }
                else
                {
                    JSONObject missingQ = new JSONObject();
                    missingQ.put("schema", schemaName);
                    missingQ.put("query", qName);
                    missingQ.put("isUserDefined", baselineQueries.getJSONObject(qName).optBoolean("isUserDefined", false));
                    missingQ.put("status", "missing_from_live");
                    queryErrors.put(missingQ);
                    summary.put("errors", summary.getInt("errors") + 1);
                    summary.put("queriesCompared", summary.getInt("queriesCompared") + 1);
                    details.put(schemaName + "." + qName, missingQ);
                }
            }
            for (String qName : liveQueryNames)
            {
                if (!baselineQueryNames.contains(qName))
                {
                    boolean isNewQueryUserDefined = liveUserDefinedQueryNames.contains(qName);
                    JSONObject newQ = new JSONObject();
                    newQ.put("schema", schemaName);
                    newQ.put("query", qName);
                    newQ.put("isUserDefined", isNewQueryUserDefined);
                    newQ.put("status", "new_in_live");
                    newQueries.put(newQ);
                    summary.put("queriesNewInLive", summary.getInt("queriesNewInLive") + 1);
                    summary.put("queriesCompared", summary.getInt("queriesCompared") + 1);
                    details.put(schemaName + "." + qName, newQ);
                }
            }
        }

        totalHolder[0] = tasks.size();
        LOG.info("Total compare tasks: {} (concurrency={})", tasks.size(), concurrency);

        // Execute comparison tasks with thread pool
        executeCompareTasks(tasks, summary, details, rowCountMismatches, checksumMismatches, metadataMismatches, queryErrors, resolvedErrors, consistentErrors, skippedQueries, concurrency, queryTimeout);

        report.put("missingSchemas", missingSchemas);
        report.put("newSchemas", newSchemas);
        report.put("newQueries", newQueries);
        report.put("rowCountMismatches", rowCountMismatches);
        report.put("checksumMismatches", checksumMismatches);
        report.put("metadataMismatches", metadataMismatches);
        report.put("queryErrors", queryErrors);
        report.put("resolvedErrors", resolvedErrors);
        report.put("consistentErrors", consistentErrors);
        report.put("skippedQueries", skippedQueries);
        report.put("details", details);

        long elapsed = System.currentTimeMillis() - startTime;
        meta.put("comparedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.put("elapsedSeconds", elapsed / 1000.0);

        LOG.info("Schema comparison complete in {}s: {} compared, {} matched, {} row mismatches, {} checksum mismatches, {} metadata mismatches, {} errors, {} consistent errors, {} changed errors, {} resolved errors",
            elapsed / 1000.0,
            summary.getInt("queriesCompared"),
            summary.getInt("queriesMatched"),
            summary.getInt("rowCountMismatches"),
            summary.getInt("checksumMismatches"),
            summary.getInt("metadataMismatches"),
            summary.getInt("errors"),
            summary.getInt("errorsConsistent"),
            summary.getInt("errorsChanged"),
            summary.getInt("errorsResolved"));

        return report;
    }

    private void executeCompareTasks(List<CompareTask> tasks,
                                     JSONObject summary, JSONObject details,
                                     JSONArray rowCountMismatches, JSONArray checksumMismatches,
                                     JSONArray metadataMismatches,
                                     JSONArray queryErrors, JSONArray resolvedErrors, JSONArray consistentErrors,
                                     JSONArray skippedQueries,
                                     int concurrency, int queryTimeout)
    {
        if (tasks.isEmpty())
            return;

        Object envState = QueryService.get().cloneEnvironment();
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        try
        {
            record FutureWithIdentity(Future<QueryCompareResult> future, String schemaName, String queryName) {}
            List<FutureWithIdentity> futuresWithId = new ArrayList<>(tasks.size());

            for (CompareTask task : tasks)
                futuresWithId.add(new FutureWithIdentity(
                    executor.submit(() ->
                    {
                        QueryService.get().copyEnvironment(envState);
                        try
                        {
                            return task.callable.call();
                        }
                        finally
                        {
                            QueryService.get().clearEnvironment();
                        }
                    }),
                    task.schemaName, task.queryName));

            for (FutureWithIdentity fwi : futuresWithId)
            {
                try
                {
                    QueryCompareResult qcr = queryTimeout > 0
                        ? fwi.future.get(queryTimeout, TimeUnit.SECONDS)
                        : fwi.future.get();
                    summary.put("queriesCompared", summary.getInt("queriesCompared") + 1);
                    details.put(qcr.schemaName + "." + qcr.queryName, qcr.result);

                    switch (qcr.status)
                    {
                        case "ok" -> summary.put("queriesMatched", summary.getInt("queriesMatched") + 1);
                        case "row_count_mismatch", "row_count_mismatch (expected)" ->
                        {
                            summary.put("rowCountMismatches", summary.getInt("rowCountMismatches") + 1);
                            rowCountMismatches.put(qcr.result);
                        }
                        case "checksum_mismatch", "checksum_mismatch (expected)" ->
                        {
                            summary.put("checksumMismatches", summary.getInt("checksumMismatches") + 1);
                            checksumMismatches.put(qcr.result);
                        }
                        case "metadata_mismatch", "metadata_mismatch (expected)" ->
                        {
                            summary.put("metadataMismatches", summary.getInt("metadataMismatches") + 1);
                            metadataMismatches.put(qcr.result);
                            if (Boolean.FALSE.equals(qcr.result.opt("rowCountMatch")))
                            {
                                summary.put("rowCountMismatches", summary.getInt("rowCountMismatches") + 1);
                                rowCountMismatches.put(qcr.result);
                            }
                            else if (Boolean.FALSE.equals(qcr.result.opt("checksumMatch")))
                            {
                                summary.put("checksumMismatches", summary.getInt("checksumMismatches") + 1);
                                checksumMismatches.put(qcr.result);
                            }
                        }
                        case "skipped" ->
                        {
                            summary.put("queriesSkipped", summary.getInt("queriesSkipped") + 1);
                            skippedQueries.put(qcr.result);
                        }
                        case "error" ->
                        {
                            summary.put("errors", summary.getInt("errors") + 1);
                            queryErrors.put(qcr.result);
                        }
                        case "error_consistent" ->
                        {
                            summary.put("errorsConsistent", summary.getInt("errorsConsistent") + 1);
                            consistentErrors.put(qcr.result);
                        }
                        case "error_changed" ->
                        {
                            summary.put("errorsChanged", summary.getInt("errorsChanged") + 1);
                            queryErrors.put(qcr.result);
                        }
                        case "error_resolved" ->
                        {
                            summary.put("errorsResolved", summary.getInt("errorsResolved") + 1);
                            resolvedErrors.put(qcr.result);
                        }
                    }
                }
                catch (TimeoutException e)
                {
                    fwi.future.cancel(true);
                    summary.put("errors", summary.getInt("errors") + 1);
                    summary.put("queriesCompared", summary.getInt("queriesCompared") + 1);
                    LOG.warn("Query compare task timed out after {}s: {}.{}", queryTimeout, fwi.schemaName, fwi.queryName);
                    queryErrors.put(makeError(fwi.schemaName, fwi.queryName, "Query timed out after " + queryTimeout + "s"));
                }
                catch (CancellationException e)
                {
                    LOG.warn("Query compare task was cancelled: {}.{}", fwi.schemaName, fwi.queryName);
                    summary.put("errors", summary.getInt("errors") + 1);
                    summary.put("queriesCompared", summary.getInt("queriesCompared") + 1);
                }
                catch (ExecutionException e)
                {
                    LOG.error("Query compare task failed for {}.{}: {}", fwi.schemaName, fwi.queryName, e.getCause().getMessage());
                    summary.put("errors", summary.getInt("errors") + 1);
                    queryErrors.put(makeError(fwi.schemaName, fwi.queryName, "Task failed: " + e.getCause().getMessage()));
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Comparison interrupted", e);
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    private JSONObject compareQueryData(UserSchema schema, String schemaName, String queryName,
                                        JSONObject baselineData, Set<String> userDefinedQueryNames, boolean skipChecksums, boolean isExpectedDiff, int queryTimeout, int checksumRowLimit, boolean debugChecksums)
    {
        JSONObject result = new JSONObject();
        result.put("schema", schemaName);
        result.put("query", queryName);
        result.put("isUserDefined", baselineData.optBoolean("isUserDefined", false));
        result.put("baselineRowCount", baselineData.opt("rowCount"));
        result.put("liveRowCount", JSONObject.NULL);
        result.put("rowCountMatch", JSONObject.NULL);
        result.put("baselineChecksum", baselineData.opt("checksum"));
        result.put("liveChecksum", JSONObject.NULL);
        result.put("checksumMatch", JSONObject.NULL);
        result.put("checksumColumns", baselineData.optJSONArray("checksumColumns") != null ?
            baselineData.optJSONArray("checksumColumns") : new JSONArray());
        result.put("checksumColumnKeys", baselineData.optJSONArray("checksumColumnKeys") != null ?
            baselineData.optJSONArray("checksumColumnKeys") : new JSONArray());
        String baselineChecksumSkippedReason = baselineData.optString("checksumSkippedReason", null);
        if (baselineChecksumSkippedReason != null)
            result.put("checksumSkippedReason", baselineChecksumSkippedReason);
        result.put("status", "ok");
        result.put("error", JSONObject.NULL);

        // If baseline was skipped (non-date params), propagate skip
        String skippedReason = baselineData.optString("skippedReason", null);
        if (skippedReason != null)
        {
            result.put("status", "skipped");
            result.put("skippedReason", skippedReason);
            return result;
        }

        // Check if baseline had an error — if so, the same error during compare is expected
        Object baselineErrorObj = baselineData.opt("error");
        boolean baselineHadError = baselineErrorObj != null && baselineErrorObj != JSONObject.NULL;

        // Get TableInfo — use same resolution path as capture to ensure consistent column sets
        TableResolution resolution = resolveTableInfo(schema, queryName, userDefinedQueryNames);
        if (resolution.error != null)
        {
            String liveError = resolution.error;
            result.put("error", liveError);
            if (baselineHadError)
            {
                String baselineError = baselineErrorObj.toString();
                result.put("baselineError", baselineError);
                boolean match = baselineError.equals(liveError) || (isTimeoutError(baselineError) && isTimeoutError(liveError));
                result.put("status", match ? "error_consistent" : "error_changed");
            }
            else
            {
                result.put("status", "error");
            }
            return result;
        }
        TableInfo tableInfo = resolution.tableInfo;

        // Rebuild date parameter defaults if baseline used them
        Map<String, Object> paramValues = null;
        if (baselineData.optBoolean("defaultDateParams", false))
        {
            JSONArray baselineParams = baselineData.optJSONArray("parameters");
            if (baselineParams != null)
            {
                paramValues = new HashMap<>();
                if (baselineParams.length() == 1)
                {
                    paramValues.put(baselineParams.getJSONObject(0).getString("name"), "2026-01-01");
                }
                else if (baselineParams.length() == 2)
                {
                    paramValues.put(baselineParams.getJSONObject(0).getString("name"), "2025-12-01");
                    paramValues.put(baselineParams.getJSONObject(1).getString("name"), "2026-01-01");
                }
            }
        }

        // Column metadata comparison
        boolean hasMetadataMismatch = false;
        JSONArray baselineColumns = baselineData.optJSONArray("columns");
        if (baselineColumns != null && !baselineColumns.isEmpty())
        {
            JSONArray baselineChecksumKeys = baselineData.optJSONArray("checksumColumnKeys");
            JSONArray baselineChecksumCols = baselineData.optJSONArray("checksumColumns");
            List<ChecksumColumnSelection> liveChecksumColumns = getChecksumColumnsForCompare(tableInfo, baselineChecksumKeys, baselineChecksumCols);
            JSONArray liveColumns = getColumnMetadata(liveChecksumColumns.stream().map(ChecksumColumnSelection::column).collect(Collectors.toList()));

            JSONObject metadataDiff = compareColumnMetadata(baselineColumns, liveColumns);
            boolean metadataMatch = metadataDiff.getBoolean("match");
            result.put("metadataMatch", metadataMatch);

            if (!metadataMatch)
            {
                hasMetadataMismatch = true;
                result.put("metadataDiffs", metadataDiff);
                String status = "metadata_mismatch";
                if (isExpectedDiff) status += " (expected)";
                result.put("status", status);
                LOG.warn("METADATA MISMATCH {}.{}: {}", schemaName, queryName, metadataDiff);
            }
        }

        // Row count
        long liveRowCount;
        try
        {
            TableSelector rowCountSelector = new TableSelector(tableInfo);
            if (queryTimeout > 0)
                rowCountSelector.setQueryTimeout(queryTimeout);
            if (paramValues != null)
                rowCountSelector.setNamedParameters(paramValues);
            liveRowCount = rowCountSelector.getRowCount();
            result.put("liveRowCount", liveRowCount);
        }
        catch (Exception e)
        {
            String liveError = "Row count failed: " + e.getMessage();
            result.put("error", liveError);
            if (baselineHadError)
            {
                String baselineError = baselineErrorObj.toString();
                result.put("baselineError", baselineError);
                boolean match = baselineError.equals(liveError) || (isTimeoutError(baselineError) && isTimeoutError(liveError));
                result.put("status", match ? "error_consistent" : "error_changed");
            }
            else
            {
                result.put("status", "error");
            }
            return result;
        }

        // Compare row counts
        Object baselineRowCountObj = baselineData.opt("rowCount");
        if (baselineRowCountObj != null && baselineRowCountObj != JSONObject.NULL)
        {
            long baselineRowCount = baselineData.getLong("rowCount");
            boolean match = baselineRowCount == liveRowCount;
            result.put("rowCountMatch", match);

            if (!match)
            {
                if (!hasMetadataMismatch)
                {
                    String status = "row_count_mismatch";
                    if (isExpectedDiff) status += " (expected)";
                    result.put("status", status);
                }
                LOG.warn("ROW COUNT MISMATCH {}.{}: {} vs {} ({}{})",
                    schemaName, queryName, baselineRowCount, liveRowCount,
                    liveRowCount - baselineRowCount > 0 ? "+" : "",
                    liveRowCount - baselineRowCount);
                return result;
            }
        }

        // Checksum comparison
        boolean liveQueryHasNonDeterministicFromSql = queryFromSqlContainsNonDeterministicFunction(tableInfo);
        if (liveQueryHasNonDeterministicFromSql)
            result.put("checksumSkippedReason", "query contains non-deterministic function");

        Object baselineChecksum = baselineData.opt("checksum");
        if (skipChecksums || liveQueryHasNonDeterministicFromSql || baselineChecksum == null || baselineChecksum == JSONObject.NULL)
            return result;

        try
        {
            JSONArray baselineChecksumKeys = baselineData.optJSONArray("checksumColumnKeys");
            JSONArray baselineChecksumCols = baselineData.optJSONArray("checksumColumns");
            List<ChecksumColumnSelection> checksumColumns = getChecksumColumnsForCompare(tableInfo, baselineChecksumKeys, baselineChecksumCols);
            result.put("checksumColumns", toJsonArray(checksumColumns.stream().map(selection -> getChecksumColumnName(selection.column())).collect(Collectors.toList())));
            result.put("checksumColumnKeys", getChecksumColumnKeysFromSelections(checksumColumns));

            String liveChecksum = computeChecksum(tableInfo, checksumColumns, paramValues, queryTimeout, checksumRowLimit, schemaName, queryName, debugChecksums);
            result.put("liveChecksum", liveChecksum);

            String baselineChecksumStr = baselineChecksum.toString();

            // Detect incompatible checksum algorithms (old sha256: vs new sha256-xor:)
            boolean algorithmMismatch = (baselineChecksumStr.startsWith("sha256:") && liveChecksum.startsWith("sha256-xor:")) ||
                (baselineChecksumStr.startsWith("sha256-xor:") && liveChecksum.startsWith("sha256:"));

            if (algorithmMismatch)
            {
                result.put("checksumMatch", false);
                result.put("status", "skipped");
                result.put("skippedReason", "checksum algorithm mismatch (baseline uses " +
                    baselineChecksumStr.substring(0, baselineChecksumStr.indexOf(':')) +
                    ", live uses " + liveChecksum.substring(0, liveChecksum.indexOf(':')) +
                    ") — re-capture baseline required");
                return result;
            }

            boolean checksumMatch = baselineChecksumStr.equals(liveChecksum);
            result.put("checksumMatch", checksumMatch);

            if (!checksumMatch)
            {
                if (!hasMetadataMismatch)
                {
                    String status = "checksum_mismatch";
                    if (isExpectedDiff) status += " (expected)";
                    result.put("status", status);
                }
                LOG.warn("CHECKSUM MISMATCH {}.{}: rows match ({}) but data differs",
                    schemaName, queryName, liveRowCount);
            }
        }
        catch (Exception e)
        {
            String liveError = "Checksum comparison failed: " + e.getMessage();
            result.put("error", liveError);
            if (baselineHadError)
            {
                String baselineError = baselineErrorObj.toString();
                result.put("baselineError", baselineError);
                boolean match = baselineError.equals(liveError) || (isTimeoutError(baselineError) && isTimeoutError(liveError));
                result.put("status", match ? "error_consistent" : "error_changed");
            }
            else
            {
                result.put("status", "error");
            }
        }

        // Baseline had an error but live succeeded — the error is resolved
        if (baselineHadError && "ok".equals(result.opt("status")))
        {
            result.put("status", "error_resolved");
            result.put("baselineError", baselineErrorObj.toString());
        }

        return result;
    }

    private static boolean isTimeoutError(String error)
    {
        if (error == null) return false;
        return error.contains("timed out") || error.contains("canceling statement due to user request");
    }

    // ---- Schema enumeration ----

    private record SchemaInfo(String fullyQualifiedName, UserSchema schema, User user, Container container) {}

    private List<SchemaInfo> enumerateSchemas(User user, Container container)
    {
        List<SchemaInfo> schemas = new ArrayList<>();

        SimpleSchemaTreeVisitor<Void, Void> visitor = new SimpleSchemaTreeVisitor<>(true)
        {
            @Override
            public Void visitUserSchema(UserSchema schema, Path path, Void param)
            {
                schemas.add(new SchemaInfo(schema.getSchemaName(), schema, user, container));
                Collection<QuerySchema> children;
                try
                {
                    children = schema.getSchemas(true);
                }
                catch (Exception e)
                {
                    LOG.warn("Failed to get child schemas of {}, retrying: {}", schema.getSchemaName(), e.getMessage());
                    try
                    {
                        children = schema.getSchemas(true);
                    }
                    catch (Exception e2)
                    {
                        LOG.error("Failed to get child schemas of {} on retry: {}", schema.getSchemaName(), e2.getMessage());
                        return null;
                    }
                }
                visit(children, path, param);
                return null;
            }
        };

        QuerySchema rootSchema = DefaultSchema.get(user, container);
        visitor.visitTop(rootSchema.getSchemas(true), null);

        if (LOG.isDebugEnabled())
        {
            List<String> schemaNames = schemas.stream()
                .map(SchemaInfo::fullyQualifiedName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
            LOG.debug("Enumerated {} schemas: {}", schemaNames.size(), schemaNames);
        }

        return schemas;
    }

    // ---- Utilities ----

    /**
     * Resolve a fresh UserSchema instance for thread-safe concurrent access.
     * UserSchema caches resolved tables internally and is not safe for multi-threaded use,
     * so each thread pool task must get its own instance.
     */
    private static UserSchema resolveSchemaForThread(User user, Container container, String schemaName)
    {
        return QueryService.get().getUserSchema(user, container, schemaName);
    }

    private static int clampConcurrency(int concurrency)
    {
        return Math.max(1, Math.min(concurrency, MAX_CONCURRENCY));
    }

    private String detectDatabaseType()
    {
        return DbScope.getLabKeyScope().getSqlDialect().isPostgreSQL() ? "pgsql" : "mssql";
    }

    static String normalizedEntriesToJson(List<Map.Entry<String, Object>> entries)
    {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Map.Entry<String, Object> entry : entries)
        {
            if (!first) sb.append(", ");
            first = false;
            sb.append('[');
            sb.append(jsonEscape(entry.getKey()));
            sb.append(", ");
            sb.append(jsonValue(entry.getValue()));
            sb.append(']');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String jsonEscape(String s)
    {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String jsonValue(Object v)
    {
        if (v == null) return "null";
        if (v instanceof Boolean) return v.toString();
        if (v instanceof Number) return v.toString();
        if (v instanceof String) return jsonEscape((String) v);
        return jsonEscape(v.toString());
    }

    static boolean containsNonDeterministicFunction(ColumnInfo col)
    {
        String sql = col.getValueSql("_").getSQL().toLowerCase();
        return sql.contains("now()") || sql.contains("curdate()") || sql.contains("curtime()");
    }

    private static boolean queryFromSqlContainsNonDeterministicFunction(TableInfo tableInfo)
    {
        if (tableInfo.getSelectName() != null)
            return false; // Hard table — FROM SQL is just the table name

        String fromSql = tableInfo.getFromSQL("_").getSQL().toLowerCase();
        return fromSql.contains("now()") || fromSql.contains("curdate()") || fromSql.contains("curtime()");
    }

    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static JSONObject makeError(String schema, @Nullable String query, String error)
    {
        JSONObject err = new JSONObject();
        err.put("schema", schema);
        err.put("query", query != null ? query : JSONObject.NULL);
        err.put("error", error);
        return err;
    }
}
