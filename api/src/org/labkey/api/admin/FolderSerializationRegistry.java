package org.labkey.api.admin;

import java.util.Collection;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderSerializationRegistry
{
    public void addFactories(FolderWriterFactory writerFactory, FolderImporterFactory importerFactory);
    public Collection<FolderImporter> getRegisteredFolderImporters();
}