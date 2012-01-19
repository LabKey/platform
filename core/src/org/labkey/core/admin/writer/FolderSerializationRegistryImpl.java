package org.labkey.core.admin.writer;

import org.labkey.api.admin.*;
import org.labkey.api.admin.FolderWriter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderSerializationRegistryImpl implements FolderSerializationRegistry
{
    private static final FolderSerializationRegistryImpl INSTANCE = new FolderSerializationRegistryImpl();
    private static final Collection<FolderWriterFactory> WRITER_FACTORIES = new CopyOnWriteArrayList<FolderWriterFactory>();
    private static final Collection<FolderImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<FolderImporterFactory>();

    private FolderSerializationRegistryImpl()
    {
    }

    public static FolderSerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers are defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to write elements into folder.xml.
    public Collection<FolderWriter> getRegisteredFolderWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderWriter> writers = new LinkedList<FolderWriter>();

        for (FolderWriterFactory factory : WRITER_FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    // These importers are defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to read elements from folder.xml.
    public Collection<FolderImporter> getRegisteredFolderImporters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderImporter> importers = new LinkedList<FolderImporter>();

        for (FolderImporterFactory factory : IMPORTER_FACTORIES)
            importers.add(factory.create());

        return importers;
    }

    public void addFactories(FolderWriterFactory writerFactory, FolderImporterFactory importerFactory)
    {
        WRITER_FACTORIES.add(writerFactory);
        IMPORTER_FACTORIES.add(importerFactory);
    }
}
