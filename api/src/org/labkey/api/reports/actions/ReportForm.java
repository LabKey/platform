package org.labkey.api.reports.actions;

import org.labkey.api.view.ViewForm;
import org.springframework.validation.BindException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 30, 2008
 */
public class ReportForm extends ViewForm
{
    private String _tabId;
    protected BindException _errors;

    public String getTabId()
    {
        return _tabId;
    }

    public void setTabId(String tabId)
    {
        _tabId = tabId;
    }

    public BindException getErrors()
    {
        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }
}
