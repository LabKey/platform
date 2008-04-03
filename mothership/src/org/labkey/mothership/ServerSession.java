package org.labkey.mothership;

import java.util.Date;

/**
 * User: jeckels
 * Date: Apr 21, 2006
 */
public class ServerSession
{
    private int _serverSessionId;
    private String _serverSessionGUID;
    private int _serverInstallationId;
    private Date _earliestKnownTime;
    private Date _lastKnownTime;
    private String _container;

    private Integer _svnRevision;
    private String _databaseProductVersion;
    private String _databaseProductName;
    private String _databaseDriverVersion;
    private String _databaseDriverName;
    private String _runtimeOS;
    private String _javaVersion;

    private boolean _ldapEnabled;
    private boolean _enterprisePipelineEnabled;

    private Integer _userCount;
    private Integer _activeUserCount;
    private Integer _projectCount;
    private Integer _containerCount;
    private String _administratorEmail;

    public String getDatabaseProductVersion()
    {
        return _databaseProductVersion;
    }

    public String getDatabaseProductName()
    {
        return _databaseProductName;
    }

    public String getDatabaseDriverVersion()
    {
        return _databaseDriverVersion;
    }

    public String getRuntimeOS()
    {
        return _runtimeOS;
    }

    public String getDatabaseDriverName()
    {
        return _databaseDriverName;
    }

    public int getServerSessionId()
    {
        return _serverSessionId;
    }

    public void setServerSessionId(int serverSessionId)
    {
        _serverSessionId = serverSessionId;
    }

    public String getServerSessionGUID()
    {
        return _serverSessionGUID;
    }

    public void setServerSessionGUID(String serverSessionGUID)
    {
        _serverSessionGUID = serverSessionGUID;
    }

    public int getServerInstallationId()
    {
        return _serverInstallationId;
    }

    public void setServerInstallationId(int serverInstallationId)
    {
        _serverInstallationId = serverInstallationId;
    }

    public Date getEarliestKnownTime()
    {
        return _earliestKnownTime;
    }

    public void setEarliestKnownTime(Date earliestKnownTime)
    {
        _earliestKnownTime = earliestKnownTime;
    }

    public Date getLastKnownTime()
    {
        return _lastKnownTime;
    }

    public void setLastKnownTime(Date lastKnownTime)
    {
        _lastKnownTime = lastKnownTime;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public void setSvnRevision(Integer svnRevision)
    {
        _svnRevision = svnRevision;
    }

    public Integer getSvnRevision()
    {
        return _svnRevision;
    }

    public void setDatabaseProductVersion(String databaseProductVersion)
    {
        _databaseProductVersion = databaseProductVersion;
    }

    public void setDatabaseProductName(String databaseProductName)
    {
        _databaseProductName = databaseProductName;
    }

    public void setDatabaseDriverVersion(String databaseDriverVersion)
    {
        _databaseDriverVersion = databaseDriverVersion;
    }

    public void setDatabaseDriverName(String databaseDriverName)
    {
        _databaseDriverName = databaseDriverName;
    }

    public void setRuntimeOS(String runtimeOS)
    {
        _runtimeOS = runtimeOS;
    }

    public String getJavaVersion()
    {
        return _javaVersion;
    }

    public void setJavaVersion(String javaVersion)
    {
        _javaVersion = javaVersion;
    }

    public Integer getUserCount()
    {
        return _userCount;
    }

    public void setUserCount(Integer userCount)
    {
        _userCount = userCount;
    }

    public Integer getActiveUserCount()
    {
        return _activeUserCount;
    }

    public void setActiveUserCount(Integer activeUserCount)
    {
        _activeUserCount = activeUserCount;
    }

    public Integer getProjectCount()
    {
        return _projectCount;
    }

    public void setProjectCount(Integer projectCount)
    {
        _projectCount = projectCount;
    }

    public Integer getContainerCount()
    {
        return _containerCount;
    }

    public void setContainerCount(Integer containerCount)
    {
        _containerCount = containerCount;
    }

    public String getAdministratorEmail()
    {
        return _administratorEmail;
    }

    public void setAdministratorEmail(String administratorEmail)
    {
        _administratorEmail = administratorEmail;
    }

    public boolean isLdapEnabled()
    {
        return _ldapEnabled;
    }

    public void setLdapEnabled(boolean ldapEnabled)
    {
        _ldapEnabled = ldapEnabled;
    }

    public boolean isEnterprisePipelineEnabled()
    {
        return _enterprisePipelineEnabled;
    }

    public void setEnterprisePipelineEnabled(boolean enterprisePipelineEnabled)
    {
        _enterprisePipelineEnabled = enterprisePipelineEnabled;
    }
}
