package org.labkey.query.controllers;

import org.labkey.api.view.ViewForm;

public class InternalNewViewForm extends ViewForm
{
    public String ff_schemaName;
    public String ff_queryName;
    public String ff_viewName;
    public boolean ff_share;
    public boolean ff_inherit;

    public void setFf_schemaName(String name)
    {
        ff_schemaName = name;
    }

    public void setFf_queryName(String name)
    {
        ff_queryName = name;
    }

    public void setFf_viewName(String name)
    {
        ff_viewName = name;
    }

    public void setFf_share(boolean share)
    {
        ff_share = share;
    }

    public void setFf_inherit(boolean inherit)
    {
        ff_inherit = inherit;
    }
}
