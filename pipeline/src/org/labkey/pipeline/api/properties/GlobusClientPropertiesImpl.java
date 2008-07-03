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
import org.labkey.pipeline.xstream.PathMapper;

import java.util.Map;
import java.io.File;

/**
 * <code>GlobusClientPropertiesImpl</code> used for Spring configuration.
 */
public class GlobusClientPropertiesImpl implements PipelineJobService.GlobusClientProperties
{
    private String _globusJavaHome; // todo: make this unnecessary
    private String _globusLabkeyDir;
    private String _globusServer;
    private String _globusJobFactoryType;
    private String _globusQueue;
    private Map<String, String> _globusPathMapping;

    public String getGlobusJavaHome()
    {
        return _globusJavaHome;
    }

    public void setGlobusJavaHome(String globusJavaHome)
    {
        _globusJavaHome = globusJavaHome;
    }

    public String getGlobusLabkeyDir()
    {
        return _globusLabkeyDir;
    }

    public void setGlobusLabkeyDir(String globusLabkeyDir)
    {
        _globusLabkeyDir = globusLabkeyDir;
    }

    public String getGlobusServer()
    {
        return _globusServer;
    }

    public void setGlobusServer(String globusServer)
    {
        _globusServer = globusServer;
    }

    public String getGlobusJobFactoryType()
    {
        return _globusJobFactoryType;
    }

    public void setGlobusJobFactoryType(String globusJobFactoryType)
    {
        _globusJobFactoryType = globusJobFactoryType;
    }

    public String getGlobusQueue()
    {
        return _globusQueue;
    }

    public void setGlobusQueue(String globusQueue)
    {
        _globusQueue = globusQueue;
    }

    public Map<String, String> getGlobusPathMapping()
    {
        return _globusPathMapping;
    }

    public void setGlobusPathMapping(Map<String, String> globusPathMapping)
    {
        _globusPathMapping = globusPathMapping;
    }
}