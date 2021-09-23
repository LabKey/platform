package org.labkey.api.cloud;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.Collection;


/**
 * Interface describing the configuration needed to Watch a Cloud based Notification Queue
 */
public interface CloudWatcherJob
{
    int getRowId();
    Collection<WatchEvent<Path>> poll();
}


