/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.model.ParticipantMapper;
import org.labkey.api.study.writer.SimpleStudyExportContext;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.xml.StudyDocument;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:43:07 PM
 */
public class StudyExportContext extends SimpleStudyExportContext
{
    private final List<DatasetDefinition> _datasets = new LinkedList<>();
    private final Set<Integer> _datasetIds = new HashSet<>();

    private Consumer<StudyDocument.Study> _studyXmlModifier = study -> {};  // By default, make no changes to exported study XML

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, PHI.NotPHI, new ParticipantMapper(study, false, false), false, logger);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, List<DatasetDefinition> initDatasets, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, PHI.NotPHI, new ParticipantMapper(study, false, false), false, logger);
        setDatasets(initDatasets);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, PHI phiLevel, ParticipantMapper participantMapper, boolean maskClinic, LoggerGetter logger)
    {
        super(user, c, getStudyDocument(), dataTypes, phiLevel, participantMapper, maskClinic, logger, null);

        if (_datasets.size() == 0)
            initializeDatasets(study);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, PHI phiLevel, ParticipantMapper participantMapper, boolean maskClinic, List<DatasetDefinition> initDatasets, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, phiLevel, participantMapper, maskClinic, logger);
        setDatasets(initDatasets);
    }

    private static StudyDocument getStudyDocument()
    {
        StudyDocument doc = StudyDocument.Factory.newInstance();
        doc.addNewStudy();
        return doc;
    }

    private void initializeDatasets(StudyImpl study)
    {
        boolean includeStudyData = getDataTypes().contains(StudyArchiveDataTypes.STUDY_DATASETS_DATA) || getDataTypes().contains(StudyArchiveDataTypes.STUDY_DATASETS_DEFINITIONS);
        boolean includeAssayData = getDataTypes().contains(StudyArchiveDataTypes.ASSAY_DATASET_DATA) || getDataTypes().contains(StudyArchiveDataTypes.ASSAY_DATASET_DEFINITIONS);
        boolean includeSampleTypeData = getDataTypes().contains(StudyArchiveDataTypes.SAMPLE_TYPE_DATASET_DATA) || getDataTypes().contains(StudyArchiveDataTypes.SAMPLE_TYPE_DATASET_DEFINITIONS);
        boolean includeAllDatasetData = getDataTypes().contains(StudyArchiveDataTypes.DATASET_DATA);

        for (DatasetDefinition dataset : study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER))
        {
            Dataset.PublishSource publishSource = dataset.getPublishSource();
            boolean isStudyDataset = !dataset.isPublishedData();
            boolean isAssayDataset = publishSource == Dataset.PublishSource.Assay;
            boolean isSampleTypeDataset = publishSource == Dataset.PublishSource.SampleType;

            if (includeAllDatasetData || ((isStudyDataset && includeStudyData) || (isAssayDataset && includeAssayData) || (isSampleTypeDataset && includeSampleTypeData)))
            {
                _datasets.add(dataset);
                _datasetIds.add(dataset.getDatasetId());
            }
        }
    }

    public boolean isExportedDataset(Integer datasetId)
    {
        return _datasetIds.contains(datasetId);
    }

    public List<DatasetDefinition> getDatasets()
    {
        return _datasets;
    }

    public Set<Integer> getDatasetIds()
    {
        return _datasetIds;
    }

    public void setDatasets(List<DatasetDefinition> datasets)
    {
        _datasets.clear();
        _datasetIds.clear();
        for (DatasetDefinition dataset : datasets)
        {
            _datasets.add(dataset);
            _datasetIds.add(dataset.getDatasetId());
        }
    }

    public Consumer<StudyDocument.Study> getStudyXmlModifier()
    {
        return _studyXmlModifier;
    }

    public void setStudyXmlModifier(Consumer<StudyDocument.Study> studyXmlModifier)
    {
        _studyXmlModifier = studyXmlModifier;
    }
}
