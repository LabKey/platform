package org.labkey.query.controllers;

import org.apache.struts.action.ActionMapping;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;

public class SourceForm extends QueryForm
{
    public String ff_queryText;
    public String ff_metadataText;
    public QueryAction ff_redirect = QueryAction.sourceQuery;

    public SourceForm()
    {
    }

    public SourceForm(ViewContext context)
    {
        setViewContext(context);
        setContainer(context.getContainer());
        setUser(context.getUser());
    }

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
    }

    public void setFf_queryText(String text)
    {
        ff_queryText = text;
    }

    public void setFf_metadataText(String text)
    {
        ff_metadataText = text;
    }
    public void setFf_redirect(String action)
    {
        ff_redirect = QueryAction.valueOf(action);
    }

    public ActionURL getForwardURL()
    {
        return getQueryDef().urlFor(ff_redirect);
    }
}
