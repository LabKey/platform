package org.labkey.api.cloud;

import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.writer.VirtualFile;

public interface CloudArchiveImporterSupport
{
    FolderImportContext getImportContext();
    VirtualFile getDataDirectory();
    String getOriginalFilename();
}
