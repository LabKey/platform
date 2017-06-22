/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.stream.Collectors;

/*
* User: Dave
* Date: Apr 2, 2009
* Time: 12:44:51 PM
*/
public class JavaScriptExportScriptModel extends ExportScriptModel
{
    public JavaScriptExportScriptModel(QueryView view)
    {
        super(view);
    }

    public String getFilters()
    {
        List<String> filterExprs = getFilterExpressions();
        if (null == filterExprs || filterExprs.size() == 0)
            return "null";
        
        StringBuilder ret = new StringBuilder("[");
        String sep = "";
        for (String filterExpr : filterExprs)
        {
            ret.append(sep);
            ret.append(filterExpr);
            sep = ",";
        }
        ret.append("]");
        return ret.toString();
    }

    public String getJSONFilters()
    {
        // Returns filters in a JSON format for use with the GetData API.
        // Most of this code was taken from ExportScriptModel.getFilterExpressions()
        JSONArray filters = new JSONArray();
        QueryView view = getQueryView();
        SimpleFilter filter = new SimpleFilter(view.getSettings().getSortFilterURL(), view.getDataRegionName());

        for (SimpleFilter.FilterClause clause : filter.getClauses())
        {
            JSONObject filterObj = new JSONObject();
            List<String> fieldKey = clause.getFieldKeys().get(0).getParts();
            String value = getFilterValue(clause, clause.getParamVals());
            CompareType operator;

            //two kinds of clauses can be used on URLs: CompareClause and MultiValuedFilterClause
            if (clause instanceof CompareType.CompareClause)
                operator = ((CompareType.CompareClause)clause).getCompareType();
            else if (clause instanceof SimpleFilter.ContainsOneOfClause)
                operator = clause.isNegated() ? CompareType.CONTAINS_NONE_OF : CompareType.CONTAINS_ONE_OF;
            else if (clause instanceof SimpleFilter.InClause)
                operator = clause.isNegated() ? CompareType.NOT_IN : CompareType.IN;
            else
                operator = CompareType.EQUAL;

            filterObj.put("value", value);
            filterObj.put("type", operator.getScriptName());
            filterObj.put("fieldKey", fieldKey);
            filters.put(filterObj);
        }

        return filters.toString();
    }

    public String getJSONColumns()
    {
        // Returns columns in a JSON format for use with the GetData API.
        JSONArray jsonCols = new JSONArray();

        for (DisplayColumn dc : getQueryView().getDisplayColumns())
        {
            if (dc.isQueryColumn())
            {
                jsonCols.put(dc.getDisplayColumnInfo().getFieldKey().getParts());
            }
        }

        return jsonCols.toString();
    }

    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "LABKEY.Filter.create(" +PageFlowUtil.jsString(name) + ", "
                + PageFlowUtil.jsString(value) + ", LABKEY.Filter.Types." + operator.getScriptName() + ")";
    }

    private String getColumns()
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
        return ret.toString();
    }

    // Produce javascript code block containing all the standard query parameters. Callers need to wrap this block in
    // curly braces (at a minimum) and modify/add parameters as appropriate.
    public String getStandardJavaScriptParameters(int indentSpaces, boolean includeStandardCallbacks)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        StringBuilder params = new StringBuilder();
        params.append(indent).append("requiredVersion: 9.1,\n");
        params.append(indent).append("schemaName: ").append(PageFlowUtil.jsString(getSchemaName())).append(",\n");

        if (null != getViewName())
            params.append(indent).append("viewName: ").append(PageFlowUtil.jsString(getViewName())).append(",\n");

        params.append(indent).append("queryName: ").append(PageFlowUtil.jsString(getQueryName())).append(",\n");
        params.append(indent).append("columns: ").append(PageFlowUtil.jsString(getColumns())).append(",\n");  // TODO: Inconsistent with R and SAS, which don't include view columns
        params.append(indent).append("filterArray: ").append(getFilters());

        if (hasSort())
            params.append(",\n").append(indent).append("sort: ").append(PageFlowUtil.jsString(getSort()));

        if (hasContainerFilter())
            params.append(",\n").append(indent).append("containerFilter: ").append(PageFlowUtil.jsString(getContainerFilterTypeName()));

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("parameters: {")
                .append(getQueryParameters().entrySet().stream()
                    .map(e -> "'" + e.getKey() + "': '" + e.getValue() + "'")
                    .collect(Collectors.joining(", ")))
                .append("}");
        }

        if (includeStandardCallbacks)
        {
            params.append(",\n");
            params.append(indent).append("success: onSuccess,\n");
            params.append(indent).append("failure: onError");
        }

        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        String indent = StringUtils.repeat(" ", 4);

        StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"text/javascript\">").append("\n");
        sb.append("\n");
        sb.append("LABKEY.Query.selectRows({").append("\n");
        sb.append(getStandardJavaScriptParameters(4, true)).append("\n");
        sb.append("});").append("\n");
        sb.append("\n");
        sb.append("function onSuccess(results) {\n");
        sb.append(indent).append("var data = '';\n");
        sb.append(indent).append("var length = Math.min(10, results.rows.length);").append("\n");
        sb.append("\n");
        sb.append(indent).append("// Display first 10 rows in a popup dialog").append("\n");
        sb.append(indent).append("for (var idxRow = 0; idxRow < length; idxRow++) {").append("\n");
        sb.append(indent).append(indent).append("var row = results.rows[idxRow];").append("\n");
        sb.append("\n");
        sb.append(indent).append(indent).append("for (var col in row) {").append("\n");
        sb.append(indent).append(indent).append(indent).append("data = data + row[col].value + ' ';").append("\n");
        sb.append(indent).append(indent).append("}").append("\n");
        sb.append("\n");

        sb.append(indent).append(indent).append("data = data + '\\n';").append("\n");
        sb.append(indent).append("}").append("\n");
        sb.append("\n");
        sb.append(indent).append("alert(data);").append("\n");
        sb.append("}").append("\n");
        sb.append("\n");
        sb.append("function onError(errorInfo) {").append("\n");
        sb.append(indent).append("alert(errorInfo.exception);").append("\n");
        sb.append("}").append("\n");
        sb.append("\n");
        sb.append("</script>");

        return sb.toString();
    }
}
