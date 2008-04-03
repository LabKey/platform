package org.labkey.core.admin.sql;

import org.labkey.api.jsp.JspBase;
import org.labkey.api.data.SqlScriptRunner;
import org.labkey.api.data.SqlScriptRunner.SqlScriptProvider;
import org.labkey.api.view.ActionURL;

import java.util.List;

/**
 * User: jeckels
 * Date: Apr 5, 2006
 */
public abstract class ShowRunningScriptsPage extends JspBase
{
    private List<SqlScriptRunner.SqlScript> _runningScripts;
    private ActionURL _waitForScriptsUrl;
    private SqlScriptProvider _provider;
    private ActionURL _currentUrl;

    public void setScripts(List<SqlScriptRunner.SqlScript> runningScripts)
    {
        _runningScripts = runningScripts;
    }

    public List<SqlScriptRunner.SqlScript> getScripts()
    {
        return _runningScripts;
    }

    public void setWaitForScriptsUrl(ActionURL waitForScriptsUrl)
    {
        _waitForScriptsUrl = waitForScriptsUrl;
    }

    public ActionURL getWaitForScriptsUrl()
    {
        return _waitForScriptsUrl;
    }

    public void setProvider(SqlScriptProvider provider)
    {
        _provider = provider;
    }

    public SqlScriptProvider getProvider()
    {
        return _provider;
    }

    public ActionURL getCurrentUrl()
    {
        return _currentUrl;
    }

    public void setCurrentUrl(ActionURL currentUrl)
    {
        _currentUrl = currentUrl;
    }
}
