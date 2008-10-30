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
package org.labkey.api.module;

/**
 * User: adam
 * Date: Oct 27, 2008
 * Time: 11:43:08 AM
 */
public class ResourceFinder
{
    String _name;
    String _sourcePath;

    public ResourceFinder(String name, String sourcePath)
    {
        _name = name;
        _sourcePath = sourcePath;
    }

    public ResourceFinder(Module module)
    {
        this(module.getName(), module.getSourcePath());
    }

    public String getName()
    {
        return _name;
    }

    public String getSourcePath()
    {
        return _sourcePath;
    }

    // TODO: module.properties should include, and Modules should publish, both SourcePath and BuildPath -- use that here
    public String getBuildPath()
    {
        return ModuleLoader.getServletContext().getRealPath("../../modules/" + getName().toLowerCase());
    }
}
