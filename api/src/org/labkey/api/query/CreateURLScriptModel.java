/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * User: bbimber
 * Date: 2/5/12
 * Time: 8:39 PM
 */
public class CreateURLScriptModel extends ExportScriptModel
{
    private QueryView _view;

    public CreateURLScriptModel(QueryView view)
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

    public String getURL()
    {
        if (_view.getQueryDef().isTemporary())
            return "This is a temporary query that does not support stable URLs";

        ActionURL url = QueryService.get().urlFor(_view.getUser(), _view.getContainer(), QueryAction.executeQuery, _view.getSchema().getSchemaName(), _view.getTable().getPublicName());

        ContainerFilter containerFilter = getContainerFilter();
        if (null != containerFilter && null != containerFilter.getType())
            url.addParameter("query.containerFilterName", containerFilter.getType().name());

        if (null != getViewName())
            url.addParameter("query.viewName", getViewName());

        url.addParameter("query.columns", getColumns());

        String sort = getSort();
        if (!StringUtils.isEmpty(sort))
            url.addParameter("query.sort", sort);

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
