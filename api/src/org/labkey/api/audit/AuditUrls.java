package org.labkey.api.audit;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.Date;

public interface AuditUrls extends UrlProvider
{
    // Returns the audit view for the specified audit type with an optional filter for the date the records were created
    ActionURL getAuditLog(Container container, String eventType, @Nullable Date startDate, @Nullable Date endDate);
}
