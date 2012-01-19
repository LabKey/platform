package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.writer.Writer;
import org.labkey.folder.xml.FolderDocument;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface ExternalFolderWriter extends Writer<Container, ImportContext<FolderDocument.Folder>>
{
}
