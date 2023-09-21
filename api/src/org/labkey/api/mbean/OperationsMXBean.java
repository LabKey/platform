package org.labkey.api.mbean;

public interface OperationsMXBean
{
    /** @return number of minutes since an authenticated user last interacted with the server. null if nobody's logged in since the server started */
    Long getMinutesSinceMostRecentUserActivity();

    /** @return number of distinct authenticated users in the last 10 minutes */
    int getUserCountInLastTenMinutes();
    /** @return number of distinct authenticated users in the last hour */
    int getUserCountInLastHour();

    /** @return number of active, authenticated user HTTP sessions that are currently active */
    int getActiveUserSessionCount();

    /** @return number of site-wide warnings/errors that a site admin would see if they logged in */
    int getSiteWarningCount();

    /** @return count of jobs in the queue, either running or waiting */
    int getPipelineQueueSize();
}
