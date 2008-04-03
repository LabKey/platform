package org.labkey.query.controllers;

import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryDefinition;
import org.apache.struts.action.ActionMapping;

import javax.servlet.http.HttpServletRequest;

public class PropertiesForm extends QueryForm
{
    public String ff_description;
    public boolean ff_inheritable;
    public boolean ff_hidden;

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
        QueryDefinition queryDef = getQueryDef();
        if (queryDef != null)
        {
            ff_description = queryDef.getDescription();
            ff_inheritable = queryDef.canInherit();
            ff_hidden = queryDef.isHidden();
        }
    }

    public void setFf_inheritable(boolean b)
    {
        ff_inheritable = b;
    }

    public void setFf_description(String description)
    {
        ff_description = description;
    }

    public void setFf_hidden(boolean b)
    {
        ff_hidden = b;
    }
}
