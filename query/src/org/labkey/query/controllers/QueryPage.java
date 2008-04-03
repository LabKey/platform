package org.labkey.query.controllers;

import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.QueryAction;
import org.labkey.api.view.ActionURL;

abstract public class QueryPage extends FormPage
{
    protected ActionURL urlFor(QueryAction action)
    {
        return new ActionURL("query", action.toString(), getContainer());
    }
}
