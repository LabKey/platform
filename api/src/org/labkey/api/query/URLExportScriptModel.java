/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.ActionURL;

import java.net.URLEncoder;
import java.util.List;

public class URLExportScriptModel extends ExportScriptModel
{
    private final QueryView _view;

    public URLExportScriptModel(QueryView view)
    {
        super(view);
        _view = view;
    }

    @Override
    protected String quote(String value)
    {
        throw new IllegalStateException("Should not be called");
    }

    @Override
    public String getFilters()
    {
        List<String> expressions = getFilterExpressions();
        if (expressions != null && !expressions.isEmpty())
            return "&" + StringUtils.join(expressions, "&");

        return "";
    }

    @Override
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "query." + URLEncoder.encode(FieldKey.fromString(name).toString(), StringUtilsLabKey.DEFAULT_CHARSET) +
                "~" + URLEncoder.encode(operator.getPreferredUrlKey(), StringUtilsLabKey.DEFAULT_CHARSET) + "=" +
                (value == null ? "" : URLEncoder.encode(value, StringUtilsLabKey.DEFAULT_CHARSET));
    }

    private String getURL()
    {
        if (_view.getQueryDef().isTemporary())
            return "This is a temporary query that does not support stable URLs";

        ActionURL url = DetailsURL.fromString("/query/executeQuery.view").getActionURL();
        //noinspection ConstantConditions
        url.addParameter("schemaName", getSchemaName());
        url.addParameter("query.queryName", _view.getTable().getPublicName());
        url.setContainer(_view.getContainer());

        if (hasContainerFilter())
            url.addParameter("query" + DataRegion.CONTAINER_FILTER_NAME, getContainerFilterTypeName());

        if (null != getViewName())
            url.addParameter("query.viewName", getViewName());

        url.addParameter("query.columns", getColumns().replace(",$", ""));

        if (hasQueryParameters())
            getQueryParameters().forEach((key, value) -> url.addParameter("query.param." + key, value));

        if (hasSort())
            url.addParameter("query.sort", getSort());

        return AppProps.getInstance().getBaseServerUrl() + url + getFilters();
    }

    @Override
    public String getScriptExportText()
    {
        return "The following URL can be used to reload the query, preserving any filters, sorts, or custom sets of columns:\n\n" + getURL();
    }
}
