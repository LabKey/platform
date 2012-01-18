package org.labkey.api.admin;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public interface FolderSerializationRegistry
{
    public void addFactories(ExternalFolderWriterFactory writerFactory, ExternalFolderImporterFactory importerFactory);
}