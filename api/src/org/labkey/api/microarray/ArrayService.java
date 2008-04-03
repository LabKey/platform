package org.labkey.api.microarray;

import org.apache.log4j.Logger;

public class ArrayService
{
    private static Service _serviceImpl = null;

    public interface Service
    {
        FeatureExtractionClient createFeatureExtractionClient(String server, String url, Logger instanceLogger);
    }

    public static void register(Service serviceImpl)
    {
        if (_serviceImpl != null)
            throw new IllegalStateException("Service has already been set.");
        _serviceImpl = serviceImpl;
    }

    public static Service get()
    {
        if (_serviceImpl == null)
            throw new IllegalStateException("Service has not been set.");
        return _serviceImpl;
    }
}