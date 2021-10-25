package org.labkey.api.cloud;

/**
 * Interface describing the configuration needed to Watch a Cloud based Notification Queue
 */
public interface CloudWatcherJob
{
    int getRowId();
    void poll();

    void deleteMessage(String messageId);
}


