package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.writer.WriterFactory;
import org.labkey.folder.xml.FolderDocument;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface ExternalFolderWriterFactory extends WriterFactory<Container, ImportContext<FolderDocument.Folder>>
{
    ExternalFolderWriter create();
}
