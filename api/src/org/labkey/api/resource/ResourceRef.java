package org.labkey.api.resource;

import org.apache.log4j.Logger;
import org.labkey.api.util.HeartBeatThread;

import java.util.HashSet;

/**
 * Base-class for resource references. This class provides
 * some basic functionality, such as tracking if this instance is stale compared
 * to the resource and it's dependencies and tries to not hit the file-system
 * too frequently by using the HeartBeatThread.
 */
public class ResourceRef
{
    protected static final Logger _log = Logger.getLogger(ResourceRef.class);

    protected Resource _resource;
    protected long _version;
    private int _counter;

    HashSet<ResourceRef> _dependencies;

    public ResourceRef(Resource resource)
    {
        _resource = resource;
        _version = resource.getVersionStamp();
        _counter = HeartBeatThread.counter;
    }

    public Resource getResource()
    {
        return _resource;
    }

    public void updateVersionStamp()
    {
        _version = _resource.getVersionStamp();
    }

    public boolean isStale()
    {
        int counter = HeartBeatThread.counter;
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
            _dependencies = new HashSet<ResourceRef>();
        _dependencies.add(dependency);
    }
}

