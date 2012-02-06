package org.labkey.api.query;

import org.labkey.api.view.JspView;
import org.labkey.api.view.WebPartView;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 2/5/12
 * Time: 8:37 PM
 */
public class PerlExportScriptFactory implements ExportScriptFactory
{
    public String getScriptType()
    {
        return "pl";
    }

    public String getMenuText()
    {
        return "Perl";
    }

    public WebPartView getView(QueryView queryView)
    {
        return new JspView<CreatePerlScriptModel>("/org/labkey/api/query/createPerlScript.jsp", new CreatePerlScriptModel(queryView));
    }
}