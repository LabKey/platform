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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.CompareType;
import org.labkey.api.util.PageFlowUtil;

import java.util.stream.Collectors;

public class PerlExportScriptModel extends ExportScriptModel
{
    private int _indentSpaces = 0;

    public PerlExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    @Nullable
    public String getFilters()
    {
        String indent = StringUtils.repeat(" ", _indentSpaces);

        return getFilters(null, "[", "\n" + indent + indent, ",", "\n" + indent + "]");
    }

    @Override
    protected String quote(String value)
    {
        // JavaScript quoting is close
        return PageFlowUtil.jsString(value);
    }

    // Our Perl clientapi expects filters with operators in the middle
    @Override
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        return "[" + quote(name) + ", " + quote(operator.getPreferredUrlKey()) + ", " + quote(value) + "]";
    }

    // Produce Perl code block containing all the standard query parameters. Callers need to wrap this block in
    // curly braces (at a minimum) and modify/add parameters as appropriate.
    public String getStandardScriptParameters(int indentSpaces)
    {
        String indent = StringUtils.repeat(" ", indentSpaces);
        _indentSpaces = indentSpaces;
        StringBuilder params = new StringBuilder();
        //params.append(indent).append("requiredVersion => 9.1,\n");
        params.append(indent).append("-baseUrl => ").append(quote(getBaseUrl())).append(",\n");
        params.append(indent).append("-containerPath => ").append(quote(getFolderPath())).append(",\n");
        params.append(indent).append("-schemaName => ").append(quote(getSchemaName())).append(",\n");

        if (null != getViewName())
            params.append(indent).append("-viewName => ").append(quote(getViewName())).append(",\n");

        params.append(indent).append("-queryName => ").append(quote(getQueryName())).append(",\n");
        params.append(indent).append("-columns => ").append(quote(getColumns()));

        String filters = getFilters();

        if (null != filters)
            params.append(",\n").append(indent).append("-filterArray => ").append(filters);

        if (hasSort())
            params.append(",\n").append(indent).append("-sort => ").append(quote(getSort()));

        if (hasContainerFilter())
            params.append(",\n").append(indent).append("-containerFilterName => ").append(quote(getContainerFilterTypeName()));

        if (hasQueryParameters())
        {
            params.append(",\n").append(indent).append("-parameters => [\n");
            params.append(getQueryParameters().entrySet().stream()
                .map(e -> indent + indent + "[" + quote(e.getKey()) + ", " + quote(e.getValue()) + "]")
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
