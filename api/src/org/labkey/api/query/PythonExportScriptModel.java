/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.query;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Collectors;

public class PythonExportScriptModel extends ExportScriptModel
{
    private static final int INDENT_SPACES = 4;

    private static final Logger _log = LogManager.getLogger(PythonExportScriptModel.class);

    public PythonExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    @Nullable
    public String getFilters()
    {
        String indent = StringUtils.repeat(" ", INDENT_SPACES);

        return getFilters(null, "[", "\n" + indent + indent, ",", "\n" + indent + "]");
    }

    @Override
    protected String quote(String value)
    {
        // Use Java string literals escaping, since it's nearly identical to Python
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }

    // Our python client api will generate the query filter, so call the constructor
    @Override
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "labkey.query.QueryFilter(" + quote(name) + ", " + quote(value)
                + ", " + quote(operator.getPreferredUrlKey()) + ")";
    }

    /**
     * Generate the python Server Context object
     * Assumes generating server is export script target.
     */
    private String getPythonApiWrapper()
    {
        StringBuilder sb = new StringBuilder();

        try
        {
            URL baseUrl = new URL(getBaseUrl());
            sb.append("api = APIWrapper(");
            sb.append(quote(baseUrl.getAuthority())).append(", ");
            sb.append(quote(getFolderPath().substring(1))).append(", ");

            // 25082: The server may not have a context path
            if (baseUrl.getPath() != null && baseUrl.getPath().length() > 1)
                sb.append(quote(baseUrl.getPath().substring(1))).append(", ");

            sb.append("use_ssl=").append(baseUrl.getProtocol().equals("https") ? "True" : "False");
            sb.append(")\n");
        }
        catch (MalformedURLException e)
        {
            // Log error
            _log.error("Unable to retrieve server url to generate python client request context", e);

            // Add comment and example to export script
            return "# Unable to generate labkey request context. Example usage:\n" +
                "# api = APIWrapper(domain, container_path, context_path=None, use_ssl=True)\n";
        }

        return sb.toString();
    }

    private String getStandardScriptParameters()
    {
        StringBuilder params = new StringBuilder();
        String indent = StringUtils.repeat(" ", INDENT_SPACES);

        params.append(indent).append("schema_name=").append(quote(getSchemaName())).append(",\n");
        params.append(indent).append("query_name=").append(quote(getQueryName()));
        if (null != getViewName())
            params.append(",\n").append(indent).append("view_name=").append(quote(getViewName()));

        params.append(",\n").append(indent).append("columns=").append(quote(getColumns()));
        // Generate filter string
        String filters = getFilters();
        if (null != filters)
            params.append(",\n").append(indent).append("filter_array=").append(filters);

        // Generate Sort string
        if (hasSort())
            params.append(",\n").append(indent).append("sort=").append(quote(getSort()));

        // Generate ContainerFilter
        if (hasContainerFilter())
            params.append(",\n").append(indent).append("container_filter=").append(quote(getContainerFilterTypeName()));

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("parameters={");
            params.append(getQueryParameters().entrySet().stream()
                .map(e -> quote(e.getKey()) + ": " + quote(e.getValue()))
                .collect(Collectors.joining(", ")));
            params.append("}");
        }

        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("# This script targets the Python client API version 2.0.0 and later").append("\n");
        sb.append("import labkey").append("\n"); // for filters
        sb.append("from labkey.api_wrapper import APIWrapper").append("\n\n");

        //Generate server context object
        sb.append(getPythonApiWrapper()).append("\n");
        sb.append("my_results = api.query.select_rows(\n");

        //Generate query parameters
        sb.append(getStandardScriptParameters()).append("\n");
        sb.append(")\n");
        return sb.toString();
    }
}
