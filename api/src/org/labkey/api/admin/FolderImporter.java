package org.labkey.api.admin;

import org.labkey.api.pipeline.PipelineJobWarning;
import org.jetbrains.annotations.Nullable;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;
import java.util.Collection;


/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderImporter
{
    // Brief description of the types of objects this class imports
    String getDescription();
    void process(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception;
    @Nullable
    Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, File root) throws Exception;
}
