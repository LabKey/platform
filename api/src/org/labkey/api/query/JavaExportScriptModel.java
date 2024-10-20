/*
 * Copyright (c) 2015-2018 LabKey Corporation
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

import org.apache.commons.text.StringEscapeUtils;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.util.HelpTopic;

import java.util.Map.Entry;
import java.util.stream.Collectors;

public class JavaExportScriptModel extends ExportScriptModel
{
    public JavaExportScriptModel(QueryView view)
    {
        super(view);
    }

    @Override
    public String getFilters()
    {
        return getFilters("", "", "", "", "");
    }

    @Override
    protected String makeFilterExpression(String name, CompareType operator, String value)
    {
        // 40753: Lookup operator by programmatic name
        String filterOperator = "Filter.Operator.getOperator(" + quote(operator.name()) + ")";

        return "cmd.addFilter(" + quote(name) + ", " + quote(value) + ", " + filterOperator + ");\n";
    }

    private String getListOfColumns()
    {
        StringBuilder ret = new StringBuilder();
        appendListOperator(ret);
        ret.append(
            getQueryView().getDisplayColumns().stream()
                .filter(DisplayColumn::isQueryColumn)
                .map(dc -> quote(dc.getColumnInfo().getName()))
                .collect(Collectors.joining(", "))
        );
        ret.append(")");

        return ret.toString();
    }

    private StringBuilder getListOfSorts(String sortColumns)
    {
        StringBuilder sortParameters = new StringBuilder();
        appendListOperator(sortParameters);

        String sep = "";

        for (String sortColumn : new CsvSet(sortColumns))
        {
            sortParameters.append(sep);
            sortParameters.append("new Sort(");

            if (sortColumn.startsWith("-"))
            {
                sortParameters.append(quote(sortColumn.substring(1)));
                sortParameters.append(", Sort.Direction.DESCENDING");
            }
            else
            {
                sortParameters.append(quote(sortColumn));
            }

            sortParameters.append(")");
            sep = ", ";
        }

        sortParameters.append(")");

        return sortParameters;
    }

    private void appendListOperator(StringBuilder sb)
    {
        sb.append("List.of(");
    }

    @Override
    protected String quote(String value)
    {
        return "\"" + StringEscapeUtils.escapeJava(value) + "\"";
    }

    @Override
    public String getScriptExportText()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("/*\n").append("    Java code generated by ").append(getInstallationName()).append(" on ").append(getCreatedOn()).append("\n\n");
        sb.append("    This code makes use of the free, open-source LabKey Java Client API Library, which you can declare as a dependency or download.\n\n");
        sb.append("    See ").append(new HelpTopic("javaAPI").getHelpTopicHref(HelpTopic.Referrer.script)).append(" for more information.").append("\n*/\n\n");

        sb.append("Connection cn = new Connection(").append(quote(getBaseUrl())).append(", \"<email>\", \"<password>\");").append("\n");
        sb.append("SelectRowsCommand cmd = new SelectRowsCommand(").append(quote(getSchemaName())).append(", ").append(quote(getQueryName())).append(");\n");
        sb.append("cmd.setRequiredVersion(9.1);\n");

        if (null != getViewName())
            sb.append("cmd.setViewName(").append(quote(getViewName())).append(");\n");

        sb.append("cmd.setColumns(").append(getListOfColumns()).append(");\n");

        sb.append(getFilters());

        if (hasSort())
            sb.append("cmd.setSorts(").append(getListOfSorts(getSort())).append(");\n");

        if (hasContainerFilter())
            sb.append("cmd.setContainerFilter(ContainerFilter.").append(getContainerFilterTypeName()).append(");\n");

        if (hasQueryParameters())
        {
            sb.append("\nMap<String, String> parameters = new HashMap<>();\n");

            for (Entry<String, String> entry : getQueryParameters().entrySet())
            {
                sb.append("parameters.put(").append(quote(entry.getKey())).append(", ").append(quote(entry.getValue())).append(");\n");
            }

            sb.append("cmd.setQueryParameters(parameters);\n");
        }

        sb.append("\nSelectRowsResponse response = cmd.execute(cn, ")
            .append(quote(getFolderPath())).append(");\n")
            .append("System.out.println(\"Number of rows: \" + response.getRowCount());\n\n")
            .append("for (Map<String, Object> row : response.getRows())\n")
            .append("{\n").append("    System.out.println(row);\n").append("}\n");

        return sb.toString();
    }
}
