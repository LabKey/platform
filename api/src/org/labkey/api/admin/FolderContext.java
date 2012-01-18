package org.labkey.api.admin;

import org.labkey.api.writer.ContainerUser;
import org.labkey.folder.xml.FolderDocument;
import org.apache.log4j.Logger;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderContext extends ContainerUser
{
    public FolderDocument.Folder getFolderXml(); // TODO: throws?
    public Logger getLogger();
}
