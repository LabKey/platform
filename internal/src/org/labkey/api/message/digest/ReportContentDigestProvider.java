package org.labkey.api.message.digest;

import org.labkey.api.query.ReportInfoProvider;

public interface ReportContentDigestProvider extends MessageDigest.Provider
{
    void addReportInfoProvider(ReportInfoProvider provider);
}
