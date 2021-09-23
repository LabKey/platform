package org.labkey.api.cloud;

import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

public interface CloudWatcherConfig
{
    String SQS_URL_KEY = "SQSUrl";
    String ACCOUNT_NAME_KEY = "CloudAccount";

    Map<String, Object> getCustomConfig();
    boolean isEnabled();
    Date getLastChecked();
    Container lookupContainer();
    int getRowId();
    String getFilePattern();
    Map<String, Object> getConfigMap(); //TODO may not need
}
