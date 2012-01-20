package org.labkey.api.admin;

import org.apache.xmlbeans.XmlObject;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;


/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderImporter<DocumentRoot extends XmlObject>
{
    /** Brief description of the types of objects this class imports */
    String getDescription();
    void process(ImportContext<DocumentRoot> ctx, File root) throws Exception;
    @Nullable
    Collection<PipelineJobWarning> postProcess(ImportContext<DocumentRoot> ctx, File root) throws Exception;
}
