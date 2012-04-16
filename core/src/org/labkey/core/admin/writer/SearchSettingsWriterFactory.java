package org.labkey.core.admin.writer;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

/**
 * User: cnathe
 * Date: Apr 10, 2012
 */
public class SearchSettingsWriterFactory implements FolderWriterFactory
{
    @Override
    public FolderWriter create()
    {
        return new SearchSettingsWriter();
    }

    public class SearchSettingsWriter extends BaseFolderWriter
    {
        @Override
        public String getSelectionText()
        {
            return "Full-text search settings";
        }

        @Override
        public void write(Container c, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
        {
            FolderDocument.Folder folderXml = ctx.getXml();
            folderXml.setSearchable(c.isSearchable());
        }
    }
}
