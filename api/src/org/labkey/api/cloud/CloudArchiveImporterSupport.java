package org.labkey.api.cloud;

import org.labkey.api.writer.VirtualFile;

public interface CloudArchiveImporterSupport
{
    VirtualFile getRoot();
    String getOriginalFilename();
}
