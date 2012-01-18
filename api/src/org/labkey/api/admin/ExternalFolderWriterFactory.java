package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.writer.WriterFactory;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface ExternalFolderWriterFactory extends WriterFactory<Container, FolderContext>
{
    ExternalFolderWriter create();
}
