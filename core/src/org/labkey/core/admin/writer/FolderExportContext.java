package org.labkey.core.admin.writer;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.folder.xml.FolderDocument;
import org.apache.log4j.Logger;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderExportContext extends AbstractFolderContext
{
    private final Set<String> _dataTypes;
    private boolean _locked = false;

    public FolderExportContext(User user, Container c, Set<String> dataTypes, Logger logger)
    {
        super(user, c, FolderXmlWriter.getFolderDocument(), logger);
        _dataTypes = dataTypes;
    }

    public void lockFolderDocument()
    {
        _locked = true;
    }

    @Override
    // Full folder doc -- only interesting to FolderXmlWriter
    public FolderDocument getFolderDocument()
    {
        if (_locked)
            throw new IllegalStateException("Can't access FolderDocument after folder.xml has been written");

        return super.getFolderDocument();
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }
}
