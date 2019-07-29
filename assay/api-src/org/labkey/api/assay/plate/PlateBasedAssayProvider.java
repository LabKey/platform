/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.assay.plate;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.assay.actions.PlateUploadForm;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.SampleMetadataInputFormat;

import java.io.File;

/**
 * User: jeckels
 * Date: Jan 15, 2009
 */
public interface PlateBasedAssayProvider extends AssayProvider
{
    String VIRUS_NAME_PROPERTY_NAME = "VirusName";

    void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template);
    PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol);
    File getSampleMetadataFile(Container container, int runId);
    @Nullable PlateReader getPlateReader(String readerName);
    SampleMetadataInputFormat[] getSupportedMetadataInputFormats();
    SampleMetadataInputFormat getMetadataInputFormat(ExpProtocol protocol);
    void setMetadataInputFormat(ExpProtocol protocol, SampleMetadataInputFormat format) throws ExperimentException;

    Domain getSampleWellGroupDomain(ExpProtocol protocol);
    PlateSamplePropertyHelper getSamplePropertyHelper(PlateUploadForm context, ParticipantVisitResolverType filterInputsForType);
}