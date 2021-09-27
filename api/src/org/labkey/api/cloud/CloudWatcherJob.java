package org.labkey.api.cloud;

import java.io.IOException;
import java.util.Collection;


/**
 * Interface describing the configuration needed to Watch a Cloud based Notification Queue
 */
public interface CloudWatcherJob
{
    int getRowId();
    Collection<CloudNoticeEvent> poll() throws IOException;

    void deleteMessage(String messageId);
}


