/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderImporterFactory;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.study.*;
import org.labkey.study.importer.AssayScheduleImporter;
import org.labkey.study.importer.CohortImporter;
import org.labkey.study.importer.DatasetDefinitionImporter;
import org.labkey.study.importer.InternalStudyImporter;
import org.labkey.study.importer.ParticipantCommentImporter;
import org.labkey.study.importer.ParticipantGroupImporter;
import org.labkey.study.importer.ProtocolDocumentImporter;
import org.labkey.study.importer.QcStatesImporter;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenSettingsImporter;
import org.labkey.study.importer.StudyViewsImporter;
import org.labkey.study.importer.TreatmentDataImporter;
import org.labkey.study.importer.ViewCategoryImporter;
import org.labkey.study.importer.VisitImporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class StudySerializationRegistryImpl implements StudySerializationRegistry
{
    private static final StudySerializationRegistryImpl INSTANCE = new StudySerializationRegistryImpl();
    private static final Collection<FolderImporterFactory> IMPORTER_FACTORIES = new CopyOnWriteArrayList<>();

    private StudySerializationRegistryImpl()
    {
    }

    public static StudySerializationRegistryImpl get()
    {
        return INSTANCE;
    }

    // These importers are defined and registered by other modules.  They have no knowledge of study internals, other
    // than being able to read elements from study.xml.
    public Collection<FolderImporter> getRegisteredStudyImporters()
    {
        // New up the writers every time since these classes can be stateful
        Collection<FolderImporter> importers = new LinkedList<>();

        for (FolderImporterFactory factory : IMPORTER_FACTORIES)
            importers.add(factory.create());

        return importers;
    }

    public void addImportFactory(FolderImporterFactory importerFactory)
    {
        IMPORTER_FACTORIES.add(importerFactory);
    }

    // These writers are internal to study.  They have access to study internals.
    public Collection<InternalStudyWriter> getInternalStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return Arrays.asList(
            new AssayDatasetWriter(),
            new AssayScheduleWriter(),
            new ViewCategoryWriter(),
            new CohortWriter(),
            new DatasetWriter(),
            new DatasetDataWriter(),
            new ParticipantCommentWriter(),
            new ParticipantGroupWriter(),
            new ProtocolDocumentWriter(),
            new QcStateWriter(),
            new SpecimenSettingsWriter(),
            new SpecimenArchiveWriter(),
            new TreatmentDataWriter(),
            new VisitMapWriter(),
            new StudyViewsWriter(),
            new StudyXmlWriter()  // Note: Must be the last study writer since it writes out the study.xml file (to which other writers contribute)
        );
    }

    // These importers are internal to study and should match one-to-one with the InternalStudyWriters above
    public Collection<InternalStudyImporter> getInternalStudyImporters()
    {
        return Arrays.asList(
            new AssayScheduleImporter(),
            new ViewCategoryImporter(),
            new CohortImporter(),
            new DatasetDefinitionImporter(),
            // TODO Dataset Data
            new ParticipantCommentImporter(),
            new ParticipantGroupImporter(),
            new ProtocolDocumentImporter(),
            new QcStatesImporter(),
            new SpecimenSettingsImporter(),
            // TODO Specimens
            new TreatmentDataImporter(),
            new VisitImporter(),
            new StudyViewsImporter()

            // TODO what about VisitCohortAssigner, TreatmentVisitMapImporter, SpecimenSchemaImporter, DatasetCohortAssigner?
        );
    }
}
