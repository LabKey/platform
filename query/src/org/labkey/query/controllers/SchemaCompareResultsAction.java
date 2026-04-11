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
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.files.FileContentService;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.util.logging.LogHelper;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lists saved schema compare result files, or returns the content of a specific file.
 *
 * URL: /query-schemaCompareResults.api
 * Parameters:
 *   fileName (optional) - if set, returns the content of that file; otherwise lists all files
 */
@RequiresPermission(AdminPermission.class)
public class SchemaCompareResultsAction extends ReadOnlyApiAction<SchemaCompareResultsAction.Form>
{
    private static final Logger LOG = LogHelper.getLogger(SchemaCompareResultsAction.class, "Schema compare results action");

    @Override
    public ApiResponse execute(Form form, BindException errors)
    {
        FileContentService fcs = FileContentService.get();
        if (fcs == null)
            throw new ApiUsageException("FileContentService is not available");

        Path fileRoot = fcs.getFileRootPath(getContainer());
        if (fileRoot == null)
            throw new ApiUsageException("No file root configured for this container");

        Path dir = fileRoot.resolve("schemaCompare");

        if (form.getFileName() != null && !form.getFileName().isEmpty())
        {
            return getFileContent(dir, form.getFileName());
        }
        else
        {
            return listFiles(dir);
        }
    }

    private ApiResponse listFiles(Path dir)
    {
        JSONObject response = new JSONObject();
        JSONArray filesArray = new JSONArray();

        if (Files.isDirectory(dir))
        {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json"))
            {
                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());
                for (Path file : stream)
                {
                    String name = file.getFileName().toString();
                    if (!name.startsWith("schema-compare") && !name.startsWith("schema-baseline") && !name.startsWith("query-diff"))
                        continue;

                    JSONObject fileInfo = new JSONObject();
                    fileInfo.put("name", file.getFileName().toString());
                    fileInfo.put("size", Files.size(file));
                    Instant modified = Files.getLastModifiedTime(file).toInstant();
                    fileInfo.put("modified", fmt.format(modified));
                    filesArray.put(fileInfo);
                }
            }
            catch (IOException e)
            {
                LOG.warn("Failed to list schema compare results: {}", e.getMessage());
            }
        }

        response.put("files", filesArray);
        return new ApiSimpleResponse(response);
    }

    private ApiResponse getFileContent(Path dir, String fileName)
    {
        // Path traversal protection: only allow simple filenames
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\"))
            throw new ApiUsageException("Invalid file name");

        if (!fileName.endsWith(".json"))
            throw new ApiUsageException("Only .json files can be retrieved");

        Path file = dir.resolve(fileName);

        // Ensure resolved path is still inside the schemaCompare directory
        if (!file.normalize().startsWith(dir.normalize()))
            throw new ApiUsageException("Invalid file name");

        if (!Files.exists(file))
            throw new ApiUsageException("File not found: " + fileName);

        try
        {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            return new ApiSimpleResponse(json);
        }
        catch (IOException e)
        {
            throw new ApiUsageException("Failed to read file: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class Form
    {
        private String _fileName;

        public String getFileName()
        {
            return _fileName;
        }

        public void setFileName(String fileName)
        {
            _fileName = fileName;
        }
    }
}
