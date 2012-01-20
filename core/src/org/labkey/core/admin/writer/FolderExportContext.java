package org.labkey.core.admin.writer;

import org.labkey.api.admin.AbstractFolderContext;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.apache.log4j.Logger;

import java.util.Set;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderExportContext extends AbstractFolderContext
{
    private final Set<String> _dataTypes;

    public FolderExportContext(User user, Container c, Set<String> dataTypes, Logger logger)
    {
        super(user, c, FolderXmlWriter.getFolderDocument(), logger, null);
        _dataTypes = dataTypes;
    }

    public Set<String> getDataTypes()
    {
        return _dataTypes;
    }
}
