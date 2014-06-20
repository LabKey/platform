package org.labkey.api.message.digest;

import org.labkey.api.query.ReportInfoProvider;

public abstract class ReportContentDigestProvider implements MessageDigest.Provider
{
    static private ReportContentDigestProvider _instance;

    public abstract void addReportInfoProvider(ReportInfoProvider provider);

    static public ReportContentDigestProvider get()
    {
        if (null == _instance)
            throw new IllegalStateException("Service has not been set.");
        return _instance;
    }
    public static void set(ReportContentDigestProvider serviceImpl)
    {
        if (null != _instance)
            throw new IllegalStateException("Service has already been set.");
        _instance = serviceImpl;
    }

}
