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
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.JsonInputLimit;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

/**
 * Compares the live instance against a previously captured baseline snapshot.
 * Detects missing schemas, missing queries, row count mismatches, and checksum mismatches.
 *
 * URL: /query-schemaCompare.api (POST)
 * Body:
 *   {
 *     "baseline": { captured baseline JSON },
 *     "skipChecksums": false,
 *     "expectedMissing": ["schema1", "schema2"],
 *     "expectedDiffSchemas": ["auditLog"]
 *   }
 *
 * The baseline can also be posted directly at the top level (without the "baseline" wrapper).
 */
@RequiresPermission(AdminPermission.class)
@JsonInputLimit(50_000_000) // 50MB for large baselines
public class SchemaCompareAction extends MutatingApiAction<SimpleApiJsonForm>
{
    private static final Logger LOG = LogHelper.getLogger(SchemaCompareAction.class, "Schema compare action");

    @Override
    public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
    {
        JSONObject json = form.getJsonObject();
        if (json == null)
            throw new ApiUsageException("Request body must contain baseline JSON");

        // Support loading baseline from a saved file by name
        String baselineFileName = json.optString("baselineFileName", null);
        JSONObject baseline;
        if (baselineFileName != null && !baselineFileName.isEmpty())
        {
            baseline = loadBaselineFromFile(getContainer(), baselineFileName);
        }
        else
        {
            // Allow posting baseline directly or wrapped in a "baseline" key
            baseline = json.optJSONObject("baseline");
            if (baseline == null)
            {
                // Check if this looks like a baseline (has "schemas" and "metadata")
                if (json.has("schemas") && json.has("metadata"))
                    baseline = json;
                else
                    throw new ApiUsageException("Request body must contain a baseline JSON with 'schemas' and 'metadata' fields, " +
                        "either at the top level or under a 'baseline' key, or provide 'baselineFileName'");
            }
        }

        boolean skipChecksums = json.optBoolean("skipChecksums", false);
        int concurrency = json.optInt("concurrency", SchemaCompareService.DEFAULT_CONCURRENCY);
        int queryTimeout = json.optInt("queryTimeout", SchemaCompareService.DEFAULT_QUERY_TIMEOUT);
        int checksumRowLimit = json.optInt("checksumRowLimit", SchemaCompareService.DEFAULT_CHECKSUM_ROW_LIMIT);
        Set<String> expectedMissing = parseStringSet(json, "expectedMissing");
        Set<String> expectedDiffSchemas = parseStringSet(json, "expectedDiffSchemas");

        // Schema/query filters
        String schemaFilter = json.optString("schema", null);
        if (schemaFilter != null && schemaFilter.trim().isEmpty()) schemaFilter = null;
        else if (schemaFilter != null) schemaFilter = schemaFilter.trim();
        String queryFilter = json.optString("query", null);
        if (queryFilter != null && queryFilter.trim().isEmpty()) queryFilter = null;
        else if (queryFilter != null) queryFilter = queryFilter.trim();

        if (queryFilter != null && schemaFilter == null)
            throw new ApiUsageException("'query' filter requires 'schema' to also be specified");

        int baselineSchemaCount = baseline.optJSONObject("schemas") != null ? baseline.getJSONObject("schemas").length() : 0;
        String baselineCapturedAt = baseline.optJSONObject("metadata") != null ?
            baseline.getJSONObject("metadata").optString("capturedAt", "unknown") : "unknown";

        LOG.info("Starting schema comparison for container '{}' (user={}, skipChecksums={}, concurrency={}, queryTimeout={}, checksumRowLimit={}, expectedMissing={}, expectedDiffSchemas={}, baselineSchemas={}, baselineCapturedAt={}, schema={}, query={})",
            getContainer().getPath(), getUser().getEmail(), skipChecksums, concurrency, queryTimeout, checksumRowLimit,
            expectedMissing.size(), expectedDiffSchemas.size(), baselineSchemaCount, baselineCapturedAt, schemaFilter, queryFilter);

        SchemaCompareService service = new SchemaCompareService();
        JSONObject report = service.compareAgainstBaseline(
            getUser(), getContainer(), baseline,
            skipChecksums, expectedMissing, expectedDiffSchemas, concurrency, queryTimeout, checksumRowLimit,
            schemaFilter, queryFilter);

        JSONObject summary = report.optJSONObject("summary");
        if (summary != null)
        {
            LOG.info("Schema comparison complete: {} queries compared, {} matched, {} row count mismatches, {} checksum mismatches, {} metadata mismatches, {} errors",
                summary.optInt("queriesCompared"), summary.optInt("queriesMatched"),
                summary.optInt("rowCountMismatches"), summary.optInt("checksumMismatches"),
                summary.optInt("metadataMismatches"), summary.optInt("errors"));
        }

        saveResultToFile(getContainer(), "schema-compare", report);

        return new ApiSimpleResponse(report);
    }

    private JSONObject loadBaselineFromFile(Container container, String fileName)
    {
        // Path traversal protection
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))
            throw new ApiUsageException("Invalid baseline file name");

        if (!fileName.endsWith(".json"))
            throw new ApiUsageException("Baseline file must have .json extension");

        FileContentService fcs = FileContentService.get();
        if (fcs == null)
            throw new ApiUsageException("FileContentService is not available");

        Path fileRoot = fcs.getFileRootPath(container);
        if (fileRoot == null)
            throw new ApiUsageException("No file root configured for this container");

        Path dir = fileRoot.resolve("schemaCompare");
        Path file = dir.resolve(fileName);

        // Ensure resolved path stays within the schemaCompare directory
        if (!file.normalize().startsWith(dir.normalize()))
            throw new ApiUsageException("Invalid baseline file name");

        if (!Files.exists(file))
            throw new ApiUsageException("Baseline file not found: " + fileName);

        try
        {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JSONObject baseline = new JSONObject(content);

            if (!baseline.has("schemas") || !baseline.has("metadata"))
                throw new ApiUsageException("Baseline file does not contain required 'schemas' and 'metadata' fields");

            return baseline;
        }
        catch (IOException e)
        {
            throw new ApiUsageException("Failed to read baseline file: " + e.getMessage());
        }
    }

    private void saveResultToFile(Container container, String prefix, JSONObject data)
    {
        try
        {
            FileContentService fcs = FileContentService.get();
            if (fcs == null) return;
            Path fileRoot = fcs.getFileRootPath(container);
            if (fileRoot == null) return;
            Path dir = fileRoot.resolve("schemaCompare");
            Files.createDirectories(dir);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            Path file = dir.resolve(prefix + "-" + timestamp + ".json");
            Files.writeString(file, data.toString(2), StandardCharsets.UTF_8);
            LOG.info("Saved schema compare result to {}", file);
        }
        catch (Exception e)
        {
            LOG.warn("Failed to save schema compare result to file: {}", e.getMessage());
        }
    }

    private Set<String> parseStringSet(JSONObject json, String key)
    {
        Set<String> result = new HashSet<>();
        JSONArray arr = json.optJSONArray(key);
        if (arr != null)
        {
            for (int i = 0; i < arr.length(); i++)
                result.add(arr.getString(i));
        }
        return result;
    }
}
