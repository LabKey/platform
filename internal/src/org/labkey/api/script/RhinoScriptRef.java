/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.script;

import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleResourceResolver;
import org.labkey.api.resource.Resolver;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

/**
 * Reference to a top-level Rhino script, e.g., a script included in another script using require(). Rhino manages the
 * compilation and caching of these scripts itself, but asks our code if they're stale (e.g., changed or deleted during
 * the development process). This is the "validator" class that Rhino passes back to us so we can check staleness.
 *
 * Created by adam on 8/14/2016.
 */
class RhinoScriptRef
{
    private final Resource _resource;
    private final Module _module;
    private final long _lastModified;

    public RhinoScriptRef(Resource resource)
    {
        _resource = resource;

        Resolver resolver = resource.getResolver();
        assert resolver instanceof ModuleResourceResolver;
        _module = ((ModuleResourceResolver) resolver).getModule();
        _lastModified = resource.getLastModified();  // Stash the current last modified (getLastModified() goes directly to the file system every time)
    }

    public Resource getResource()
    {
        return _resource;
    }

    public Path getPath()
    {
        return _resource.getPath();
    }

    public Module getModule()
    {
        return _module;
    }

    public long getLastModified()
    {
        return _lastModified;
    }
}
