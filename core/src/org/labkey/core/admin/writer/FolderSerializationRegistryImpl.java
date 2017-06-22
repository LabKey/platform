/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.core.admin.writer;

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: cnathe
 * Date: Jan 18, 2012
 */
public class FolderSerializationRegistryImpl implements FolderSerializationRegistry
{
    private static final FolderSerializationRegistryImpl INSTANCE = new FolderSerializationRegistryImpl();
    private static final Collection<FolderWriterFactory> WRITER_FACTORIES = new CopyOnWriteArrayList<>();
    private static final Collection<FolderImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<>();

    private FolderSerializationRegistryImpl()
    {
    }

    public static FolderSerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers can be defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to write elements into folder.xml.
    public Collection<FolderWriter> getRegisteredFolderWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderWriter> writers = new LinkedList<>();

        for (FolderWriterFactory factory : WRITER_FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    // These importers can be defined and registered by other modules.  They have no knowledge of folder internals, other
    // than being able to read elements from folder.xml.
    //
    public Collection<FolderImporter> getRegisteredFolderImporters()
    {
        // New up the importers every time since these classes can be stateful
        Collection<FolderImporter> importers = new LinkedList<>();

        for (FolderImporterFactory factory : getSortedFactories())
            importers.add(factory.create());

        return importers;
    }

    private List<FolderImporterFactory> getSortedFactories()
    {
        List<FolderImporterFactory> factories = new ArrayList<>();
        factories.addAll(IMPORTER_FACTORIES);

        // sort the factories by priority in ascending order
        factories.sort(Comparator.comparingInt(FolderImporterFactory::getPriority));

        return factories;
    }

    @Override
    public void addFactories(FolderWriterFactory writerFactory, FolderImporterFactory importerFactory)
    {
        WRITER_FACTORIES.add(writerFactory);
        IMPORTER_FACTORIES.add(importerFactory);
    }

    @Override
    public void addImportFactory(FolderImporterFactory importerFactory)
    {
        IMPORTER_FACTORIES.add(importerFactory);
    }
}
