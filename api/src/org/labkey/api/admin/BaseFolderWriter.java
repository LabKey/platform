package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;

import java.util.Set;

/**
 * User: cnathe
 * Date: Apr 13, 2012
 */
public class BaseFolderWriter implements FolderWriter
{
    @Override
    public boolean show(Container c)
    {
        return true;
    }

    @Override
    public boolean includeInType(AbstractFolderContext.ExportType type)
    {
        return AbstractFolderContext.ExportType.ALL == type;
    }

    @Override
    public Set<Writer> getChildren()
    {
        return null;
    }

    @Override
    public String getSelectionText()
    {
        return null;
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {}
}
