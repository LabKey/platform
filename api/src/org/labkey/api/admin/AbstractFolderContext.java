package org.labkey.api.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.AbstractImportContext;
import org.labkey.api.admin.ImportException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;
import org.labkey.folder.xml.FolderDocument;

import java.io.File;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public abstract class AbstractFolderContext extends AbstractImportContext<FolderDocument.Folder, FolderDocument>
{
    protected AbstractFolderContext(User user, Container c, FolderDocument folderDoc, Logger logger, @Nullable File root)
    {
        super(user, c, folderDoc, logger, root);
    }

    // Folder node -- interesting to any top-level writer that needs to set info into folder.xml
    public FolderDocument.Folder getXml() throws ImportException
    {
        return getDocument().getFolder();
    }
}
