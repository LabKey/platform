package org.labkey.core.admin.writer;

import org.labkey.api.admin.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderSerializationRegistryImpl implements FolderSerializationRegistry
{
    private static final FolderSerializationRegistryImpl INSTANCE = new FolderSerializationRegistryImpl();
    private static final Collection<ExternalFolderWriterFactory> WRITER_FACTORIES = new CopyOnWriteArrayList<ExternalFolderWriterFactory>();
    private static final Collection<ExternalFolderImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<ExternalFolderImporterFactory>();

    private FolderSerializationRegistryImpl()
    {
    }

    public static FolderSerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers are defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to write elements into folder.xml.
    public Collection<ExternalFolderWriter> getRegisteredFolderWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<ExternalFolderWriter> writers = new LinkedList<ExternalFolderWriter>();

        for (ExternalFolderWriterFactory factory : WRITER_FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    // These importers are defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to read elements from folder.xml.
    public Collection<ExternalFolderImporter> getRegisteredFolderImporters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<ExternalFolderImporter> importers = new LinkedList<ExternalFolderImporter>();

        for (ExternalFolderImporterFactory factory : IMPORTER_FACTORIES)
            importers.add(factory.create());

        return importers;
    }

    public void addFactories(ExternalFolderWriterFactory writerFactory, ExternalFolderImporterFactory importerFactory)
    {
        WRITER_FACTORIES.add(writerFactory);
        IMPORTER_FACTORIES.add(importerFactory);
    }

    public Collection<InternalFolderWriter> getInternalFolderWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new PageWriter(),
            new FolderXmlWriter()  // Note: Must be the last folder writer since it writes out the folder.xml file (to which other writers contribute)
        );
    }
}
