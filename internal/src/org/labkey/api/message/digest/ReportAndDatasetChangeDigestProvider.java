package org.labkey.api.message.digest;

import org.labkey.api.query.NotificationInfoProvider;

public abstract class ReportAndDatasetChangeDigestProvider implements MessageDigest.Provider
{
    static private ReportAndDatasetChangeDigestProvider _instance;

    public abstract void addNotificationInfoProvider(NotificationInfoProvider provider);

    static public ReportAndDatasetChangeDigestProvider get()
    {
        if (null == _instance)
            throw new IllegalStateException("Service has not been set.");
        return _instance;
    }
    public static void set(ReportAndDatasetChangeDigestProvider serviceImpl)
    {
        if (null != _instance)
            throw new IllegalStateException("Service has already been set.");
        _instance = serviceImpl;
    }

}
