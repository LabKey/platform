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
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Captures a baseline snapshot of all schemas, queries, row counts, column metadata,
 * and data checksums for the current container. Used for cross-database migration verification.
 *
 * URL: /query-schemaCompareCapture.api
 * Parameters:
 *   skipChecksums (boolean, default false) - skip data checksum computation for faster capture
 *   concurrency (int, default 6) - number of parallel threads for query processing (1-20)
 */
@RequiresPermission(AdminPermission.class)
public class SchemaCompareCaptureAction extends ReadOnlyApiAction<SchemaCompareCaptureAction.Form>
{
    private static final Logger LOG = LogHelper.getLogger(SchemaCompareCaptureAction.class, "Schema compare capture action");

    @Override
    public ApiResponse execute(Form form, BindException errors)
    {
        // Normalize empty strings to null
        String schemaFilter = form.getSchema() != null && !form.getSchema().trim().isEmpty() ? form.getSchema().trim() : null;
        String queryFilter = form.getQuery() != null && !form.getQuery().trim().isEmpty() ? form.getQuery().trim() : null;

        // Validate: query requires schema
        if (queryFilter != null && schemaFilter == null)
            throw new ApiUsageException("'query' filter requires 'schema' to also be specified");

        LOG.info("Starting schema compare capture for container '{}' (user={}, skipChecksums={}, concurrency={}, queryTimeout={}, checksumRowLimit={}, schema={}, query={})",
            getContainer().getPath(), getUser().getEmail(), form.isSkipChecksums(), form.getConcurrency(), form.getQueryTimeout(), form.getChecksumRowLimit(), schemaFilter, queryFilter);

        long startTime = System.currentTimeMillis();

        SchemaCompareService service = new SchemaCompareService();
        JSONObject baseline = service.captureBaseline(getUser(), getContainer(),
            form.isSkipChecksums(), form.getConcurrency(), form.getQueryTimeout(), form.getChecksumRowLimit(),
            schemaFilter, queryFilter);

        long elapsed = System.currentTimeMillis() - startTime;
        int schemaCount = baseline.optJSONObject("schemas") != null ? baseline.getJSONObject("schemas").length() : 0;
        int errorCount = baseline.optJSONArray("errors") != null ? baseline.getJSONArray("errors").length() : 0;

        LOG.info("Schema compare capture complete in {}s: {} schemas captured, {} errors",
            elapsed / 1000.0, schemaCount, errorCount);

        saveResultToFile(getContainer(), "schema-baseline", baseline);

        return new ApiSimpleResponse(baseline);
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

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Form
    {
        private boolean _skipChecksums = false;
        private int _concurrency = SchemaCompareService.DEFAULT_CONCURRENCY;
        private int _queryTimeout = SchemaCompareService.DEFAULT_QUERY_TIMEOUT;
        private int _checksumRowLimit = SchemaCompareService.DEFAULT_CHECKSUM_ROW_LIMIT;
        private String _schema;
        private String _query;

        public boolean isSkipChecksums()
        {
            return _skipChecksums;
        }

        public void setSkipChecksums(boolean skipChecksums)
        {
            _skipChecksums = skipChecksums;
        }

        public int getConcurrency()
        {
            return _concurrency;
        }

        public void setConcurrency(int concurrency)
        {
            _concurrency = concurrency;
        }

        public int getQueryTimeout()
        {
            return _queryTimeout;
        }

        public void setQueryTimeout(int queryTimeout)
        {
            _queryTimeout = queryTimeout;
        }

        public int getChecksumRowLimit()
        {
            return _checksumRowLimit;
        }

        public void setChecksumRowLimit(int checksumRowLimit)
        {
            _checksumRowLimit = checksumRowLimit;
        }

        public String getSchema()
        {
            return _schema;
        }

        public void setSchema(String schema)
        {
            _schema = schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }
    }
}
