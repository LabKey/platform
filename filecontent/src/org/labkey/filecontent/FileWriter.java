package org.labkey.filecontent;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.files.FileContentService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Path;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.webdav.WebdavService;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

/**
 * Writes the content of the webdav file root to the archive.
 * Created by Josh on 11/1/2016.
 */
public class FileWriter extends BaseFolderWriter
{
    public static final String DIR_NAME = "files";

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new FileWriter();
        }
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.FILES;
    }

    @Override
    public boolean selectedByDefault(AbstractFolderContext.ExportType type)
    {
        // Files could be very large, so make them opt-in
        return false;
    }

    @Override
    public void write(Container container, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        WebdavService service = ServiceRegistry.get().getService(WebdavService.class);
        WebdavResource resource = service.lookup(new Path(WebdavService.getServletPath()).append(container.getParsedPath()).append(FileContentService.FILES_LINK));
        if (resource != null)
        {
            VirtualFile virtualRoot = vf.getDir(DIR_NAME);
            virtualRoot.saveWebdavTree(resource, ctx.getUser());
        }
    }
}
