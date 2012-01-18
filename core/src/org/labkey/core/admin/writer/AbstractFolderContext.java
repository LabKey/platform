package org.labkey.core.admin.writer;

import org.labkey.api.admin.FolderContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;
import org.labkey.folder.xml.FolderDocument;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public abstract class AbstractFolderContext implements FolderContext
{
    private final User _user;
    private final Container _c;
    private final Logger _logger;
    private transient FolderDocument _folderDoc;

    protected AbstractFolderContext(User user, Container c, FolderDocument folderDoc, Logger logger)
    {
        _user = user;
        _c = c;
        _logger = logger;
        setFolderDocument(folderDoc);
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _c;
    }

    // Folder node -- interesting to any top-level writer that needs to set info into folder.xml
    public FolderDocument.Folder getFolderXml()
    {
        return getFolderDocument().getFolder();
    }

    public Logger getLogger()
    {
        return _logger;
    }

    protected synchronized FolderDocument getFolderDocument()
    {
        return _folderDoc;
    }

    protected final synchronized void setFolderDocument(FolderDocument folderDoc)
    {
        _folderDoc = folderDoc;
    }
}
