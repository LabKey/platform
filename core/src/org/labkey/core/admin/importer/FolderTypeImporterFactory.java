package org.labkey.core.admin.importer;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class FolderTypeImporterFactory implements FolderImporterFactory
{
    @Override
    public FolderImporter create()
    {
        return new FolderTypeImporter();
    }

    @Override
    public boolean isFinalImporter()
    {
        return false;
    }

    public class FolderTypeImporter implements  FolderImporter<FolderDocument.Folder>
    {
        @Override
        public String getDescription()
        {
            return "folder type and active modules";
        }

        @Override
        public void process(PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            Container c = ctx.getContainer();
            
            if (ctx.getXml().isSetFolderType())
            {
                if (null != job)
                    job.setStatus("IMPORT " + getDescription());
                ctx.getLogger().info("Loading " + getDescription());

                org.labkey.folder.xml.FolderType folderTypeXml = ctx.getXml().getFolderType();
                FolderType folderType = ModuleLoader.getInstance().getFolderType(folderTypeXml.getName());

                org.labkey.folder.xml.FolderType.Modules modulesXml = folderTypeXml.getModules();
                Set<Module> activeModules = new HashSet<Module>();
                for (String moduleName : modulesXml.getModuleNameArray())
                {
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    if (null != module)
                        activeModules.add(module);
                }

                c.setFolderType(folderType, activeModules);

                Module defaultModule = ModuleLoader.getInstance().getModule(folderTypeXml.getDefaultModule());
                if (null != defaultModule)
                {
                    c.setDefaultModule(defaultModule);
                }
                
                ctx.getLogger().info("Done importing " + getDescription());
            }
        }

        @Override
        public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile root) throws Exception
        {
            return null;
        }
    }
}
