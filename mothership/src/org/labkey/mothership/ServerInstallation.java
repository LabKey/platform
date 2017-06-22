/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

/**
 * User: jeckels
 * Date: Apr 21, 2006
 */
public class ServerInstallation
{
    private int _serverInstallationId;
    private String _serverInstallationGUID;
    private String _note;
    private String _container;
    private String _systemDescription;
    private String _logoLink;
    private String _organizationName;
    private String _systemShortName;
    private String _serverIP;
    private String _serverHostName;
    private Boolean _usedInstaller; // The Windows installer was used to install LabKey, Tomcat, Postgres.
    private Boolean _ignoreExceptions;

    public String getSystemDescription()
    {
        return _systemDescription;
    }

    public void setSystemDescription(String systemDescription)
    {
        _systemDescription = systemDescription;
    }

    public String getLogoLink()
    {
        return _logoLink;
    }

    public void setLogoLink(String logoLink)
    {
        _logoLink = logoLink;
    }

    public String getOrganizationName()
    {
        return _organizationName;
    }

    public void setOrganizationName(String organizationName)
    {
        _organizationName = organizationName;
    }

    public String getSystemShortName()
    {
        return _systemShortName;
    }

    public void setSystemShortName(String systemShortName)
    {
        _systemShortName = systemShortName;
    }

    public int getServerInstallationId()
    {
        return _serverInstallationId;
    }

    public void setServerInstallationId(int serverInstallationId)
    {
        _serverInstallationId = serverInstallationId;
    }

    public String getServerInstallationGUID()
    {
        return _serverInstallationGUID;
    }

    public void setServerInstallationGUID(String serverInstallationGUID)
    {
        _serverInstallationGUID = serverInstallationGUID;
    }

    public String getNote()
    {
        return _note;
    }

    public void setNote(String note)
    {
        _note = note;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getServerIP()
    {
        return _serverIP;
    }

    public void setServerIP(String serverIP)
    {
        _serverIP = serverIP;
    }

    public String getServerHostName()
    {
        return _serverHostName;
    }

    public void setServerHostName(String serverHostName)
    {
        _serverHostName = serverHostName;
    }

    public Boolean getUsedInstaller()
    {
        return _usedInstaller;
    }

    public void setUsedInstaller(Boolean usedInstaller)
    {
        _usedInstaller = usedInstaller;
    }

    public Boolean getIgnoreExceptions()
    {
        return _ignoreExceptions;
    }

    public void setIgnoreExceptions(Boolean ignoreExceptions)
    {
        _ignoreExceptions = ignoreExceptions;
    }
}
