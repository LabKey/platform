package org.labkey.query.controllers;

import org.labkey.api.query.*;

public class DesignForm extends QueryForm
{
    public String ff_designXML;
    public boolean ff_dirty;
    public QueryAction ff_redirect = QueryAction.designQuery;
    public void setFf_designXML(String value)
    {
        ff_designXML = value;
    }
    public void setFf_dirty(boolean value)
    {
        ff_dirty = value;
    }
    public void setFf_redirect(String value)
    {
        ff_redirect = QueryAction.valueOf(value);
    }

    public String getDefaultTab()
    {
        return getViewContext().getRequest().getParameter(QueryParam.defaultTab.toString());
    }

}
