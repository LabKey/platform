/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.labkey.api.study.importer.SimpleStudyImporter;
import org.labkey.api.study.importer.SimpleStudyImporterRegistry;
import org.labkey.api.study.writer.SimpleStudyWriter;
import org.labkey.api.study.writer.SimpleStudyWriterRegistry;
import org.labkey.study.importer.AssayScheduleImporter;
import org.labkey.study.importer.CohortImporter;
import org.labkey.study.importer.DatasetCohortAssigner;
import org.labkey.study.importer.DatasetDefinitionImporter;
import org.labkey.study.importer.InternalStudyImporter;
import org.labkey.study.importer.ParticipantCommentImporter;
import org.labkey.study.importer.ParticipantGroupImporter;
import org.labkey.study.importer.ProtocolDocumentImporter;
import org.labkey.study.importer.StudyQcStatesImporter;
import org.labkey.study.importer.StudyViewsImporter;
import org.labkey.study.importer.TopLevelStudyPropertiesImporter;
import org.labkey.study.importer.TreatmentDataImporter;
import org.labkey.study.importer.TreatmentVisitMapImporter;
import org.labkey.study.importer.VisitCohortAssigner;
import org.labkey.study.importer.VisitImporter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class StudySerializationRegistry
{
    private static final StudySerializationRegistry INSTANCE = new StudySerializationRegistry();

    private StudySerializationRegistry()
    {
    }

    public static StudySerializationRegistry get()
    {
        return INSTANCE;
    }

    // These writers are internal to study. They have access to study internals.
    public Collection<InternalStudyWriter> getInternalStudyWriters()
    {
        // New up the writers every time since these classes can be stateful
        return List.of(
            new AssayDatasetData(),
            new AssayDatasetDefinition(),
            new SampleTypeDatasetData(),
            new SampleTypeDatasetDefinition(),
            new StudyDatasetData(),
            new StudyDatasetDefinition(),
            new AssayScheduleWriter(),
            new CohortWriter(),
            new ParticipantCommentWriter(),
            new ParticipantGroupWriter(),
            new ProtocolDocumentWriter(),
            new TreatmentDataWriter(),
            new VisitMapWriter(),
            new StudyViewsWriter(),
            new StudyXmlWriter()  // Note: Must be the last study writer since it writes out the study.xml file (to which other writers contribute)
        );
    }

    // These writers are related to study and serialize into the /study subfolder of a folder archive, but are registered by other modules
    public Collection<SimpleStudyWriter> getSimpleStudyWriters()
    {
        return SimpleStudyWriterRegistry.getSimpleStudyWriters();
    }

    public Collection<InternalStudyImporter> getInternalStudyImporters()
    {
        return Arrays.asList(
            new AssayScheduleImporter(),
            new CohortImporter(),
            new DatasetDefinitionImporter(),
            new DatasetCohortAssigner(),
            new ParticipantCommentImporter(),
            new ParticipantGroupImporter(),
            new ProtocolDocumentImporter(),
            new StudyQcStatesImporter(),
            new TreatmentDataImporter(),
            new TreatmentVisitMapImporter(),
            new VisitImporter(),
            new VisitCohortAssigner(),
            new StudyViewsImporter(),
            new TopLevelStudyPropertiesImporter()
        );
    }

    public Collection<SimpleStudyImporter> getSimpleStudyImporters()
    {
        return SimpleStudyImporterRegistry.getSimpleStudyImporters();
    }
}
