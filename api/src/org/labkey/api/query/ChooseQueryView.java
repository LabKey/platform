/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.view.WebPartView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;

import java.io.PrintWriter;
import java.util.Map;

public class ChooseQueryView extends WebPartView
{
    UserSchema _schema;
    ActionURL _urlExecuteQuery;
    String _dataRegionName;

    public ChooseQueryView(UserSchema schema, ActionURL urlExecuteQuery, String dataRegionName)
    {
        super(FrameType.NONE);
        _schema = schema;
        _urlExecuteQuery = urlExecuteQuery;
        _dataRegionName = dataRegionName;
    }

    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        out.write("<table>");
        Map<String, QueryDefinition> queryDefs = _schema.getQueryDefs();

        for (String queryName : _schema.getTableAndQueryNames(true))
        {
            ActionURL url;
            QueryDefinition queryDef = queryDefs.get(queryName);

            if (queryDef == null)
                queryDef = _schema.getQueryDefForTable(queryName);

            if (queryDef == null)
                continue;

            if (_urlExecuteQuery != null)
            {
                url = _urlExecuteQuery.clone();
                url.replaceParameter(_dataRegionName + "." + QueryParam.queryName, queryDef.getName());
            }
            else
            {
                url = _schema.urlFor(QueryAction.executeQuery, queryDef);
            }

            String queryTitle = queryDef.getName();
            if (null != queryDef.getTitle())
                queryTitle = queryDef.getTitle();

            out.write("<tr><td>");
            out.write("<a href=\"");
            out.write(PageFlowUtil.filter(url));
            out.write("\">");
            out.write(PageFlowUtil.filter(queryTitle));
            out.write("</a>");
            out.write("</td>");
            out.write("<td>");

            if (queryDef.getDescription() != null)
                out.write(PageFlowUtil.filter(queryDef.getDescription()));

            out.write("</td>");
            out.write("</tr>");
        }

        out.write("</table>");
    }
}
