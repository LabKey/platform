package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryAction;

public class NewQueryForm extends QueryForm
{
    public String ff_newQueryName;
    public String ff_baseTableName;
    public QueryAction ff_redirect = QueryAction.sourceQuery;

    public void setFf_newQueryName(String name)
    {
        ff_newQueryName = name;
    }

    public void setFf_baseTableName(String name)
    {
        ff_baseTableName = name;
    }

    public void setFf_redirect(String redirect)
    {
        if (redirect != null)
            ff_redirect = QueryAction.valueOf(redirect);
    }
}
