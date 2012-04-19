package org.labkey.core.admin.importer;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class SearchSettingsImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new SearchSettingsImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    public class SearchSettingsImporter implements  FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "full-text search settings";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            Container c = ctx.getContainer();
            if (ctx.getXml().isSetSearchable())
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());
                ContainerManager.updateSearchable(c, ctx.getXml().getSearchable(), ctx.getUser());
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }

        @Override
        public boolean supportsVirtualFile()
        {
            return true;
        }
    }
}
