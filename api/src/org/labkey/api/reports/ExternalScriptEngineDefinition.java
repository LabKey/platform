/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
        Jupyter,
        Python,
    }

    /** Where a definition's engine runs; each definition has exactly one. */
    enum EngineKind
    {
        LOCAL,
        RSERVE,
        DOCKER,
        REMOTE,
    }

    Integer getRowId();
    String getName();
    String[] getExtensions();
    String getLanguageName();
    String getLanguageVersion();

    String getExePath();
    String getExeCommand();
    Type getType();

    /** Whether this is the site default for reports; site defaults are root-container mappings per context. */
    boolean isDefault();

    //
    // consider:  move these to RemoteScriptEngineDefinition?
    //
    String getMachine();
    int getPort();
    String getUser();
    String getPassword();
    String getFileExchange();
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

    EngineKind getKind();
    void setKind(EngineKind kind);

    /** Legacy input; an explicit kind wins over it. */
    void setRemote(boolean remote);
    /** True for every kind but LOCAL; Docker and Jupyter definitions have always set the remote flag too. */
    boolean isRemote();

    /** Legacy input; an explicit kind wins over it. */
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

    String getRemoteUrl();
}
