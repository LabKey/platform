package org.labkey.api.cloud;

import java.nio.file.Path;

public interface CloudNoticeEvent
{
    String getEventId();
    Path getPath();
}
