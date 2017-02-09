package org.labkey.filecontent;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractFolderImportFactory;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.files.FileContentService;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.writer.VirtualFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static org.labkey.filecontent.FileWriter.DIR_NAME;

/**
 * Imports into the webdav file root for the folder.
 * Created by Josh on 11/1/2016.
 */
public class FileImporter implements FolderImporter<XmlObject>
{
    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.FILES;
    }

    public String getDescription()
    {
        return getDataType().toLowerCase();
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext<XmlObject> ctx, VirtualFile root) throws Exception
    {
        VirtualFile filesVF = root.getDir(DIR_NAME);
        if (filesVF != null)
        {
            FileContentService service = ServiceRegistry.get().get(FileContentService.class);
            File rootFile = service.getFileRoot(ctx.getContainer(), FileContentService.ContentType.files);
            rootFile.mkdirs();
            if (rootFile.isDirectory())
            {
                copy(filesVF, rootFile);
            }
        }
    }

    private void copy(VirtualFile virtualFile, File realFile) throws IOException
    {
        for (String child : virtualFile.list())
        {
            File childFile = new File(realFile, child);
            try (InputStream in = virtualFile.getInputStream(child);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(childFile)))
            {
                FileUtil.copyData(in, out);
            }
        }

        for (String childDir : virtualFile.listDirs())
        {
            File realChildDir = new File(realFile, childDir);
            realChildDir.mkdir();
            if (!realChildDir.isDirectory())
            {
                throw new IOException("Failed to create directory " + realChildDir);
            }
            copy(virtualFile.getDir(childDir), realChildDir);
        }
    }

    @NotNull
    @Override
    public Collection<PipelineJobWarning> postProcess(ImportContext<XmlObject> ctx, VirtualFile root) throws Exception
    {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public Map<String, Boolean> getChildrenDataTypes(ImportContext ctx) throws ImportException
    {
        return null;
    }

    public static class Factory extends AbstractFolderImportFactory
    {
        public FolderImporter create()
        {
            return new FileImporter();
        }

        @Override
        public int getPriority()
        {
            return 60;
        }
    }

}
