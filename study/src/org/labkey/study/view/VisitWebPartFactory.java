/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.study.view;

import org.labkey.api.view.ClientAPIWebPartFactory;

/**
 * User: jgarms
 * Date: Jul 29, 2008
 * Time: 3:06:30 PM
 */
public class VisitWebPartFactory extends ClientAPIWebPartFactory
{
    private static final String DEFAULT_CONTENT =
        "<script type=\"text/javascript\">\n" +
            "    function failureHandler(responseObj)\n" +
            "    {\n" +
            "        var html = \"Could not read visits: \" + responseObj.exception;\n" +
            "        var visitResultsDiv = document.getElementById(\"visitResults\");\n" +
            "        visitResultsDiv.innerHTML = html;\n" +
            "    }\n" +
            "\n" +
            "    function successHandler(responseObj)\n" +
            "    {\n" +
            "        var html = \"<table class='labkey-data-region'><tr>\";\n" +
            "        var fields = responseObj.metaData.fields;\n" +
            "        for (i=0;i<fields.length;i++)\n" +
            "        {\n" +
            "            html = html + \"<th>\" + fields[i].name + \"</th>\";\n" +
            "        }\n" +
            "        html = html + \"</tr>\";\n" +
            "\n" +
            "        var rows = responseObj.rows;\n" +
            "        for (i=0;i<rows.length;i++)\n" +
            "        {\n" +
            "            html = html + \"<tr>\";\n" +
            "\n" +
            "            for (j=0;j<fields.length;j++)\n" +
            "            {\n" +
            "                var row = rows[i];\n" +
            "                var field = fields[j].name;\n" +
            "                var data = row[field];\n" +
            "                if (data == null)\n" +
            "                    data = \"\";\n" +
            "                html = html + \"<td>\" + data + \"</td>\";\n" +
            "            }\n" +
            "\n" +
            "            html = html + \"</tr>\";\n" +
            "        }\n" +
            "\n" +
            "        html = html + \"</table>\";\n" +
            "\n" +
            "        var visitResultsDiv = document.getElementById(\"visitResults\");\n" +
            "        visitResultsDiv.innerHTML = html;\n" +
            "        \n" +
            "    }\n" +
            "\n" +
            "    LABKEY.Query.selectRows(\n" +
            "    {\n" +
            "        schemaName: 'study',\n" +
            "        queryName: 'Visit',\n" +
            "        successCallback: successHandler,\n" +
            "        errorCallback: failureHandler\n" +
            "    });\n" +
            "</script>\n" +
            "<div id='visitResults'>Loading...</div>";

    public VisitWebPartFactory()
    {
        super("Visits");
    }

    protected String getDefaultContent()
    {
        return DEFAULT_CONTENT;
    }
}
