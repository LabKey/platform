package org.labkey.study.reports;

import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;

import java.io.PrintWriter;

/**
 * Copyright (C) 2004 Fred Hutchinson Cancer Research Center. All Rights Reserved.
 * User: migra
 * Date: Mar 7, 2006
 * Time: 3:00:54 PM
 */
public abstract class RedirectReport extends AbstractReport
{
    public static final String REDIRECT_URL = "redirectUrl";

    public HttpView renderReport(ViewContext viewContext)
    {
        return new RedirectView();
    }

    public String getParams()
    {
        return getDescriptor().getProperty(REDIRECT_URL);
    }

    public void setParams(String params)
    {
        getDescriptor().setProperty(REDIRECT_URL, params);
    }

    public void setUrl(ActionURL url)
    {
        setParams(url.toString());
    }

    public String getUrl(ViewContext c)
    {
        return getParams();
    }

    private class RedirectView extends HttpView
    {
        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            HttpView.throwRedirect(getUrl(getViewContext()));
        }
    }
}
