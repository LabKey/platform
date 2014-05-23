/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.api.resource;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.HeartBeat;

import java.util.HashSet;

/**
 * Base-class for resource references. This class provides
 * some basic functionality, such as tracking if this instance is stale compared
 * to the resource and its dependencies and tries to not hit the file-system
 * too frequently by using the HeartBeat.
 */
public class ResourceRef
{
    protected static final Logger _log = Logger.getLogger(ResourceRef.class);

    protected Resource _resource;
    protected long _version;
    private int _counter;

    HashSet<ResourceRef> _dependencies;

    public ResourceRef(@NotNull Resource resource)
    {
        _resource = resource;
        _version = resource.getVersionStamp();
        _counter = HeartBeat.counter;
    }

    public Resource getResource()
    {
        return _resource;
    }

    public void updateVersionStamp()
    {
        _version = _resource.getVersionStamp();
    }

    /**
     * Check to see if the underlying Resource or one of its dependencies has been modified.
     * Calling <code>isStale()</code> will reset an internal staleness counter.
     * @return
     */
    public boolean isStale()
    {
        int counter = HeartBeat.getCounter();
        if (_counter == counter)
            return false;
        _counter = counter;

        if (!_resource.exists() || _resource.getVersionStamp() != _version)
        {
            _log.debug("stale resource: " + _counter + " " + _resource.getPath());
            return true;
        }

        if (_dependencies != null)
        {
            for (ResourceRef ref : _dependencies)
                if (ref.isStale())
                    return true;
        }

        return false;
    }

    public void addDependency(ResourceRef dependency)
    {
        if (_dependencies == null)
            _dependencies = new HashSet<>();
        _dependencies.add(dependency);
    }
}

