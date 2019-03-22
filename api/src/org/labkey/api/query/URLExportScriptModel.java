/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * User: bbimber
 * Date: 2/5/12
 * Time: 8:39 PM
 */
public class URLExportScriptModel extends ExportScriptModel
{
    private final QueryView _view;

    public URLExportScriptModel(QueryView view)
    {
        super(view);
        _view = view;
    }

    @Override
    public String getFilters()
    {
        List<String> expressions = super.getFilterExpressions();
        if (expressions != null && expressions.size() > 0)
            return "&" + StringUtils.join(expressions, "&");

        return "";
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "query." + FieldKey.fromString(name) + "~" + operator.getPreferredUrlKey() + "=" + value;
    }

    public String getColumns()
    {
        StringBuilder ret = new StringBuilder();
        String sep = "";
        for (DisplayColumn dc : getQueryView().getDisplayColumns())
        {
            if (dc.isQueryColumn())
            {
                ret.append(sep);
                ret.append(dc.getColumnInfo().getName());
                sep = ",";
            }
        }

        return ret.toString().replace(",$", "");
    }

    private String getURL()
    {
        if (_view.getQueryDef().isTemporary())
            return "This is a temporary query that does not support stable URLs";

        ActionURL url = DetailsURL.fromString("/query/executeQuery.view").getActionURL();
        url.addParameter("schemaName", getSchemaName());
        url.addParameter("query.queryName", _view.getTable().getPublicName());
        url.setContainer(_view.getContainer());

        if (hasContainerFilter())
            url.addParameter("query" + DataRegion.CONTAINER_FILTER_NAME, getContainerFilterTypeName());

        if (null != getViewName())
            url.addParameter("query.viewName", getViewName());

        url.addParameter("query.columns", getColumns());

        if (hasQueryParameters())
            getQueryParameters().forEach((key, value) -> url.addParameter("query.param." + key, value));

        if (hasSort())
            url.addParameter("query.sort", getSort());

        return AppProps.getInstance().getBaseServerUrl() + url.toString() + getFilters();
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("The following URL can be used to reload the query, preserving any filters, sorts, or custom sets of columns:\n\n");
        sb.append(getURL());

        return sb.toString();
    }
}
