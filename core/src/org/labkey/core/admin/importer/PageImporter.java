package org.labkey.core.admin.importer;

import org.labkey.api.admin.ExternalFolderImporter;
import org.labkey.api.admin.ExternalFolderImporterFactory;
import org.labkey.api.admin.FolderContext;
import org.labkey.api.pipeline.PipelineJobWarning;

import java.io.File;
import java.util.Collection;

/**
 * User: cnathe
 * Date: Jan 17, 2012
 */
public class PageImporter implements ExternalFolderImporter
{
    @Override
    public String getDescription()
    {
        return "pages";
    }

    @Override
    public void process(FolderContext ctx, File root) throws Exception
    {
        
    }

    @Override
    public Collection<PipelineJobWarning> postProcess(FolderContext ctx, File root) throws Exception
    {
        // TODO: is there anything that needs to be done here?
        return null;
    }

    public static class Factory implements ExternalFolderImporterFactory
    {
        public ExternalFolderImporter create()
        {
            return new PageImporter();
        }
    }
}
