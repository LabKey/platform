/*
 * Copyright (c) 2006-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    private int _softwareReleaseId;
    private String _databaseProductVersion;
    private String _databaseProductName;
    private String _databaseDriverVersion;
    private String _databaseDriverName;
    private String _runtimeOS;
    private String _javaVersion;

    private Boolean _enterprisePipelineEnabled;

    private Integer _userCount;
    private Integer _activeUserCount;
    private Integer _projectCount;
    private Integer _containerCount;
    private Integer _heapSize;
    private String _administratorEmail;

    private String _servletContainer;
    private String _distribution;
    private String _usageReportingLevel;
    private String _exceptionReportingLevel;
    private String _jsonMetrics;

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

    public int getSoftwareReleaseId()
    {
        return _softwareReleaseId;
    }

    public void setSoftwareReleaseId(int softwareReleaseId)
    {
        _softwareReleaseId = softwareReleaseId;
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

    public Boolean isEnterprisePipelineEnabled()
    {
        return _enterprisePipelineEnabled;
    }

    public void setEnterprisePipelineEnabled(Boolean enterprisePipelineEnabled)
    {
        _enterprisePipelineEnabled = enterprisePipelineEnabled;
    }

    public Integer getHeapSize()
    {
        return _heapSize;
    }

    public void setHeapSize(Integer heapSize)
    {
        _heapSize = heapSize;
    }

    public String getServletContainer()
    {
        return _servletContainer;
    }

    public void setServletContainer(String servletContainer)
    {
        _servletContainer = servletContainer;
    }

    public String getDistribution()
    {
        return _distribution;
    }

    public void setDistribution(String distribution)
    {
        _distribution = distribution;
    }

    public String getUsageReportingLevel()
    {
        return _usageReportingLevel;
    }

    public void setUsageReportingLevel(String usageReportingLevel)
    {
        _usageReportingLevel = usageReportingLevel;
    }

    public String getExceptionReportingLevel()
    {
        return _exceptionReportingLevel;
    }

    public void setExceptionReportingLevel(String exceptionReportingLevel)
    {
        _exceptionReportingLevel = exceptionReportingLevel;
    }

    public String getJsonMetrics()
    {
        return _jsonMetrics;
    }

    public void setJsonMetrics(String jsonMetrics)
    {
        _jsonMetrics = jsonMetrics;
    }
}
