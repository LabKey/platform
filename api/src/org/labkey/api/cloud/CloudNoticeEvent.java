package org.labkey.api.cloud;

import java.nio.file.Path;

public interface CloudNoticeEvent
{
    /**
     * Id that can be used to track the event in the Cloud Provider UI
     * @return
     */
    String getEventId();
    Path getPath();
}
