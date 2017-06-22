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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User: bbimber
 * Date: 2/5/12
 * Time: 8:39 PM
 */
public class PerlExportScriptModel extends ExportScriptModel
{
    private int _indentSpaces = 0;

    public PerlExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Nullable
    public String getFilters()
    {
        String indent = StringUtils.repeat(" ", _indentSpaces);

        List<String> filterExprs = getFilterExpressions();
        if (null == filterExprs || filterExprs.size() == 0)
            return null;

        StringBuilder ret = new StringBuilder("[");
        String sep = "\n";
        for (String filterExpr : filterExprs)
        {
            ret.append(sep).append(indent).append(indent);
            ret.append(filterExpr);
            sep = ",\n";
        }
        ret.append("\n").append(indent).append("]");
        return ret.toString();
    }

    // Our Perl clientapi expects filters with operators in the middle
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "[" + PageFlowUtil.jsString(name) + ", "
                + operator.getPreferredUrlKey() + ", '" + PageFlowUtil.jsString(value) + "']";
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
        return ret.toString();
    }

    // Produce Perl code block containing all the standard query parameters.  Callers need to wrap this block in
    // curly braces (at a minimum) and modify/add parameters as appropriate.
    public String getStandardScriptParameters(int indentSpaces)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        _indentSpaces = indentSpaces;
        StringBuilder params = new StringBuilder();
        //params.append(indent).append("requiredVersion => 9.1,\n");
        params.append(indent).append("-baseUrl => ").append(PageFlowUtil.jsString(getBaseUrl())).append(",\n");
        params.append(indent).append("-containerPath => ").append(PageFlowUtil.jsString(getFolderPath())).append(",\n");
        params.append(indent).append("-schemaName => ").append(PageFlowUtil.jsString(getSchemaName())).append(",\n");

        if (null != getViewName())
            params.append(indent).append("-viewName => ").append(PageFlowUtil.jsString(getViewName())).append(",\n");

        params.append(indent).append("-queryName => ").append(PageFlowUtil.jsString(getQueryName())).append(",\n");
        params.append(indent).append("-columns => ").append(PageFlowUtil.jsString(getColumns()));  // TODO: Inconsistent with R and SAS, which don't include view columns

        String filters = getFilters();

        if (null != filters)
            params.append(",\n").append(indent).append("-filterArray => ").append(filters);

        if (hasSort())
            params.append(",\n").append(indent).append("-sort => ").append(PageFlowUtil.jsString(getSort()));

        if (hasContainerFilter())
            params.append(",\n").append(indent).append("-containerFilterName => '").append(getContainerFilterTypeName()).append("'");

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("-parameters => [\n");
            params.append(getQueryParameters().entrySet().stream()
                .map(e -> indent + indent + "['" + e.getKey() + "', '" + e.getValue() + "']")
                .collect(Collectors.joining(",\n")));
            params.append("\n").append(indent).append("]");
        }

        return params.toString();
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();
        String indent = StringUtils.repeat(" ", 4);

        sb.append("use LabKey::Query;").append("\n\n");

        sb.append("my $results = LabKey::Query::selectRows(\n");
        sb.append(getStandardScriptParameters(4)).append("\n");
        sb.append(");\n");
        sb.append("\n");

        sb.append("#output the results in tab-delimited format").append("\n");
        sb.append("my @fields;").append("\n");
        sb.append("foreach my $field (@{$results->{metaData}->{fields}}){").append("\n");
        sb.append(indent).append("push(@fields, $field->{name});").append("\n");
        sb.append("}").append("\n");

        sb.append("print join(\"\\t\", @fields) . \"\\n\";").append("\n");

        sb.append("\n");
        sb.append("foreach my $row (@{$results->{rows}}){").append("\n");
        sb.append(indent).append("my @line;").append("\n");
        sb.append(indent).append("foreach (@fields){").append("\n");
        sb.append(indent).append(indent).append("if ($row->{$_}){").append("\n");
        sb.append(indent).append(indent).append(indent).append("push(@line, $row->{$_}{'value'});").append("\n");
        sb.append(indent).append(indent).append("}").append("\n");
        sb.append(indent).append(indent).append("else {").append("\n");
        sb.append(indent).append(indent).append(indent).append("push(@line, \"\");").append("\n");
        sb.append(indent).append(indent).append("}").append("\n");
        sb.append(indent).append("}").append("\n");
        sb.append(indent).append("print join(\"\\t\", @line);").append("\n");
        sb.append(indent).append("print \"\\n\";").append("\n");
        sb.append("}").append("\n");

        return sb.toString();
    }
}
