package org.labkey.api.cloud;

import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * This interface is the minimal set of properties to setup a cloud watcher
 * NOTE: CustomConfig map should include properties for SQSUrl (target for receiving event Messages) and CloudAccount (LKS name for Cloud credentials to use)
 */
public interface CloudWatcherConfig
{
    String getLocation();
    Map<String, Object> getCustomConfig();
    boolean isEnabled();
    Date getLastChecked();
    Container lookupContainer();
    int getRowId();
    String getFilePattern();
    int getDelay();

    String getName();
}
