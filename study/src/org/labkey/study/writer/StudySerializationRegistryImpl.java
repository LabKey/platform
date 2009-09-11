/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.study.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudySerializationRegistryImpl implements StudySerializationRegistry
{
    private static final StudySerializationRegistryImpl INSTANCE = new StudySerializationRegistryImpl();
    private static final Collection<ExternalStudyWriterFactory> WRITER_FACTORIES = new CopyOnWriteArrayList<ExternalStudyWriterFactory>();
    private static final Collection<ExternalStudyImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<ExternalStudyImporterFactory>();

    private StudySerializationRegistryImpl()
    {
    }

    public static StudySerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These writers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to write elements into study.xml.
    public Collection<ExternalStudyWriter> getRegisteredStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<ExternalStudyWriter> writers = new LinkedList<ExternalStudyWriter>();

        for (ExternalStudyWriterFactory factory : WRITER_FACTORIES)
            writers.add(factory.create());

        return writers;
    }

    // These importers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to read elements from study.xml.
    public Collection<ExternalStudyImporter> getRegisteredStudyImporters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<ExternalStudyImporter> importers = new LinkedList<ExternalStudyImporter>();

        for (ExternalStudyImporterFactory factory : IMPORTER_FACTORIES)
            importers.add(factory.create());

        return importers;
    }

    public void addFactories(ExternalStudyWriterFactory writerFactory, ExternalStudyImporterFactory importerFactory)
    {
        WRITER_FACTORIES.add(writerFactory);
        IMPORTER_FACTORIES.add(importerFactory);
    }

    // These writers are internal to study.  They have access to study internals.
    public Collection<InternalStudyWriter> getInternalStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new VisitMapWriter(),
            new CohortWriter(),
            new QcStateWriter(),
            new DatasetWriter(),
            new AssayDatasetWriter(),
            new SpecimenArchiveWriter(),
            new StudyXmlWriter()  // Note: Must be the last study writer since it writes out the study.xml file (to which other writers contribute)
        );
    }
}