/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.pipeline.api.properties;

import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.GlobusSettingsImpl;
import org.labkey.api.pipeline.file.PathMapper;

/**
 * <code>GlobusClientPropertiesImpl</code> used for Spring configuration.
 */
public class GlobusClientPropertiesImpl extends GlobusSettingsImpl implements PipelineJobService.GlobusClientProperties
{
    private String _javaHome; // todo: make this unnecessary
    private String _labKeyDir;
    private String _globusServer;
    private String _jobFactoryType;
    private PathMapper _pathMapper;

    public String getJavaHome()
    {
        return _javaHome;
    }

    public void setJavaHome(String globusJavaHome)
    {
        _javaHome = globusJavaHome;
    }

    public String getLabKeyDir()
    {
        return _labKeyDir;
    }

    public void setLabKeyDir(String globusLabkeyDir)
    {
        _labKeyDir = globusLabkeyDir;
    }

    public String getGlobusServer()
    {
        return _globusServer;
    }

    public void setGlobusServer(String globusServer)
    {
        _globusServer = globusServer;
    }

    public String getJobFactoryType()
    {
        return _jobFactoryType;
    }

    public void setJobFactoryType(String globusJobFactoryType)
    {
        _jobFactoryType = globusJobFactoryType;
    }

    public PathMapper getPathMapper()
    {
        return _pathMapper;
    }

    public void setPathMapper(PathMapper pathMapper)
    {
        _pathMapper = pathMapper;
    }
}