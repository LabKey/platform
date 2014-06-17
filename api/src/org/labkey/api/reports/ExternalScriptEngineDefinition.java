/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.api.reports;

import org.labkey.api.pipeline.file.PathMapper;

/*
* User: Karl Lum
* Date: Dec 2, 2008
* Time: 4:19:14 PM
*/
public interface ExternalScriptEngineDefinition
{
    public String getName();
    public String[] getExtensions();
    public String getLanguageName();
    public String getLanguageVersion();

    public String getExePath();
    public String getExeCommand();

    //
    // consider:  move these to RemoteScriptEngineDefinition?
    //
    public String getMachine();
    public int getPort();
    public String getUser();
    public String getPassword();
    public PathMapper getPathMap();

    public void setName(String name);
    public void setExtensions(String[] extensions);
    public void setLanguageName(String name);
    public void setLanguageVersion(String version);

    public void setExePath(String path);
    public void setExeCommand(String cmd);

    public String getOutputFileName();
    public void setOutputFileName(String name);

    public void setEnabled(boolean enabled);
    public boolean isEnabled();

    public void setExternal(boolean external);
    public boolean isExternal();

    public void setRemote(boolean remote);
    public boolean isRemote();

    //
    // consider:  move these to RemoteScriptEngineDefinition?
    //
    public void setMachine(String name);
    public void setPort(int port);
    public void setUser(String user);
    public void setPassword(String password);
    public void setPathMap(PathMapper pathMap);
}
