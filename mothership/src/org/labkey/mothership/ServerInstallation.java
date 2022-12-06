/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
    private String _serverHostName;
    private Boolean _ignoreExceptions;

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

    public String getServerHostName()
    {
        return _serverHostName;
    }

    public void setServerHostName(String serverHostName)
    {
        _serverHostName = serverHostName;
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
