package org.labkey.pipeline.importer;

import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineDirectory;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.module.Module;
import org.labkey.pipeline.PipelineController;

import java.io.File;
import java.io.FileFilter;

/**
 * User: cnathe
 * Date: Jan 19, 2012
 */
public class FolderImportProvider extends PipelineProvider
{
    public FolderImportProvider(Module owningModule)
    {
        super("FolderImport", owningModule);
    }

    @Override
    public void updateFileProperties(ViewContext context, PipeRoot pr, PipelineDirectory directory, boolean includeAll)
    {
        // Only admins can import folders
        if (!context.getContainer().hasPermission(context.getUser(), AdminPermission.class))
            return;

        String actionId = createActionId(PipelineController.ImportFolderFromPipelineAction.class, null);
        addAction(actionId, PipelineController.ImportFolderFromPipelineAction.class, "Import Folder", directory, directory.listFiles(new FolderImportFilter()), false, false, includeAll);
    }

    public static File logForInputFile(File f)
    {
        return new File(FileUtil.makeFileNameWithTimestamp(f.getPath(), "log"));
    }    

    private static class FolderImportFilter implements FileFilter
    {
        public boolean accept(File file)
        {
            return file.getName().endsWith("folder.xml") || file.getName().endsWith(".folder.zip");
        }
    }
}
