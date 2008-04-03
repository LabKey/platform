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
        _schema = schema;
        _urlExecuteQuery = urlExecuteQuery;
        _dataRegionName = dataRegionName;
        setFrame(FrameType.NONE);
    }

    protected void renderView(Object model, PrintWriter out) throws Exception
    {
        out.write("<table class=\"normal\">");
        Map<String, QueryDefinition> queryDefs = QueryService.get().getQueryDefs(_schema.getContainer(), _schema.getSchemaName());
        for (String queryName : _schema.getTableAndQueryNames(true))
        {
            ActionURL url;
            QueryDefinition queryDef = queryDefs.get(queryName);
            if (queryDef == null)
            {
                queryDef = _schema.getQueryDefForTable(queryName);
            }
            if (queryDef == null)
            {
                continue;
            }
            if (_urlExecuteQuery != null)
            {
                url = _urlExecuteQuery.clone();
                url.replaceParameter(_dataRegionName + "." + QueryParam.queryName, queryDef.getName());
            }
            else
            {
                url = _schema.urlFor(QueryAction.executeQuery, queryDef);
            }
            out.write("<tr><td>");
            out.write("<a href=\"");
            out.write(PageFlowUtil.filter(url));
            out.write("\">");
            out.write(PageFlowUtil.filter(queryName));
            out.write("</a>");
            out.write("</td>");
            out.write("<td>");
            if (queryDef.getDescription() != null)
            {
                out.write(PageFlowUtil.filter(queryDef.getDescription()));
            }
            out.write("</td>");
            out.write("</tr>");
        }
        out.write("</table>");

    }
}
