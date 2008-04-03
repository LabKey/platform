package org.labkey.mothership;

import java.util.Date;

/**
 * User: jeckels
 * Date: Apr 20, 2006
 */
public class ExceptionReport
{
    private int _exceptionReportId;
    private int _exceptionStackTraceId;
    private Date _created;
    private String _installGUID;
    private String _url;
    private String _username;
    private String _browser;
    private int _serverSessionId;
    private String _referrerURL;
    private String _pageflowName;
    private String _pageflowAction;
    private String _sqlState;


    public String getUsername()
    {
        return _username;
    }

    public int getExceptionReportId()
    {
        return _exceptionReportId;
    }

    public void setExceptionReportId(int exceptionReportId)
    {
        _exceptionReportId = exceptionReportId;
    }

    public int getExceptionStackTraceId()
    {
        return _exceptionStackTraceId;
    }

    public void setExceptionStackTraceId(int exceptionStackTraceId)
    {
        _exceptionStackTraceId = exceptionStackTraceId;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }


    public void setInstallGUID(String installGUID)
    {
        _installGUID = installGUID;
    }

    public String getInstallGUID()
    {
        return _installGUID;
    }

    public String getUrl()
    {
        return _url;
    }

    public void setURL(String URL)
    {
        _url = URL;
    }

    public void setUsernameform(String username)
    {
        _username = username;
    }

    public int getServerSessionId()
    {
        return _serverSessionId;
    }

    public void setServerSessionId(int serverSessionId)
    {
        _serverSessionId = serverSessionId;
    }

    public String getBrowser()
    {
        return _browser;
    }

    public void setBrowser(String browser)
    {
        _browser = browser;
    }

    public void setReferrerURL(String referrerURL)
    {
        _referrerURL = referrerURL;
    }

    public String getReferrerURL()
    {
        return _referrerURL;
    }

    public String getPageflowAction()
    {
        return _pageflowAction;
    }

    public void setPageflowAction(String pageflowAction)
    {
        _pageflowAction = pageflowAction;
    }

    public String getPageflowName()
    {
        return _pageflowName;
    }

    public void setPageflowName(String pageflowName)
    {
        _pageflowName = pageflowName;
    }

    public void setSQLState(String sqlState)
    {
        _sqlState = sqlState;
    }

    public String getSqlState()
    {
        return _sqlState;
    }
}
