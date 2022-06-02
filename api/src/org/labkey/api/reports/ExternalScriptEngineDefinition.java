/*
 * Copyright (c) 2008-2018 LabKey Corporation
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
    enum Type
    {
        R,
        Perl,
        External,
        Docker,
    }

    Integer getRowId();
    String getName();
    String[] getExtensions();
    String getLanguageName();
    String getLanguageVersion();

    String getExePath();
    String getExeCommand();
    Type getType();

    /**
     * Determines the default engine for it's type
     */
    boolean isDefault();

    //
    // consider:  move these to RemoteScriptEngineDefinition?
    //
    String getMachine();
    int getPort();
    String getUser();
    String getPassword();
    PathMapper getPathMap();

    void setName(String name);
    void setExtensions(String[] extensions);
    void setLanguageName(String name);
    void setLanguageVersion(String version);

    void setExePath(String path);
    void setExeCommand(String cmd);

    String getOutputFileName();
    void setOutputFileName(String name);

    void setSandboxed(boolean sandboxed);
    boolean isSandboxed();

    void setEnabled(boolean enabled);
    boolean isEnabled();

    void setExternal(boolean external);
    boolean isExternal();

    void setRemote(boolean remote);
    boolean isRemote();

    void setDocker(boolean docker);
    boolean isDocker();

    void setDockerImageRowId(Integer rowId);
    Integer getDockerImageRowId();
    String getDockerImageConfig();
    void setDockerImageConfig(String dockerImageConfig);

    void setPandocEnabled(boolean pandocEnabled);
    boolean isPandocEnabled();

    //
    // consider:  move these to RemoteScriptEngineDefinition?
    //
    void setMachine(String name);
    void setPort(int port);
    void setUser(String user);
    void setPassword(String password);
    void setPathMapper(PathMapper pathMap);
}
