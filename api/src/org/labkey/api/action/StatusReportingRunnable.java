package org.labkey.api.action;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
* User: adam
* Date: Jun 26, 2010
* Time: 8:37:36 PM
*/
public interface StatusReportingRunnable extends Runnable
{
    public boolean isRunning();
    public Collection<String> getStatus(@Nullable Integer offset);
}
