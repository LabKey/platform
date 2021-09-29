package org.labkey.api.cloud;

import java.io.IOException;
import java.util.Collection;
import java.util.function.BiConsumer;


/**
 * Interface describing the configuration needed to Watch a Cloud based Notification Queue
 */
public interface CloudWatcherJob
{
    int getRowId();
    void poll(BiConsumer<CloudNoticeEvent, Integer> eventProcessor);

    void deleteMessage(String messageId);
}


