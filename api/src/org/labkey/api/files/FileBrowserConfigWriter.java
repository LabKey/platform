package org.labkey.api.files;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

public class FileBrowserConfigWriter extends BaseFolderWriter
{
    public static final String FILE_BROWSER_SETTINGS = "File Browser Settings";
    private static final String DEFAULT_SETTINGS_FILE = "filebrowser_admin_config.xml";

    @Override
    public String getDataType()
    {
        return FILE_BROWSER_SETTINGS;
    }

    @Override
    public void write(Container container, FolderExportContext ctx, VirtualFile vf) throws Exception
    {
        FileContentService service = FileContentService.get();
        if (service == null)
            return;

        FolderDocument.Folder folderXml = ctx.getXml();
        FolderDocument.Folder.FileBrowserConfig fileBrowserConfig = folderXml.addNewFileBrowserConfig();
        fileBrowserConfig.setFile(DEFAULT_SETTINGS_FILE);
        FilesAdminOptions options = service.getAdminOptions(container);
        vf.saveXmlBean(DEFAULT_SETTINGS_FILE, options.getPipelineOptionsDocument());
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new FileBrowserConfigWriter();
        }
    }
}

