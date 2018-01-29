/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.ParticipantMapper;
import org.labkey.study.model.Vial;
import org.labkey.study.model.StudyImpl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 16, 2009
 * Time: 2:43:07 PM
 */
public class StudyExportContext extends AbstractContext
{
    private final List<DatasetDefinition> _datasets = new LinkedList<>();
    private final Set<Integer> _datasetIds = new HashSet<>();
    private final PHI _phiLevel;
    private final boolean _maskClinic;
    private final ParticipantMapper _participantMapper;
    private Set<Integer> _visitIds = null;
    private List<String> _participants = new ArrayList<>();
    private List<Vial> _vials = null;

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, PHI.NotPHI, new ParticipantMapper(study, false, false), false, logger);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, Set<DatasetDefinition> initDatasets, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, PHI.NotPHI, new ParticipantMapper(study, false, false), false, logger);
        setDatasets(initDatasets);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, PHI phiLevel, ParticipantMapper participantMapper, boolean maskClinic, LoggerGetter logger)
    {
        super(user, c, StudyXmlWriter.getStudyDocument(), dataTypes, logger, null);
        _phiLevel = phiLevel;
        _participantMapper = participantMapper;
        _maskClinic = maskClinic;

        if (_datasets.size() == 0)
            initializeDatasets(study);
    }

    public StudyExportContext(StudyImpl study, User user, Container c, Set<String> dataTypes, PHI phiLevel, ParticipantMapper participantMapper, boolean maskClinic, Set<DatasetDefinition> initDatasets, LoggerGetter logger)
    {
        this(study, user, c, dataTypes, phiLevel, participantMapper, maskClinic, logger);
        setDatasets(initDatasets);
    }

    @Override
    public PHI getPhiLevel()
    {
        return _phiLevel;
    }

    @Override
    public boolean isShiftDates()
    {
        return getParticipantMapper().isShiftDates();
    }

    @Override
    public boolean isAlternateIds()
    {
        return getParticipantMapper().isAlternateIds();
    }

    @Override
    public boolean isMaskClinic()
    {
        return _maskClinic;
    }

    private void initializeDatasets(StudyImpl study)
    {
        boolean includeCRF = getDataTypes().contains(StudyArchiveDataTypes.CRF_DATASETS);
        boolean includeAssay = getDataTypes().contains(StudyArchiveDataTypes.ASSAY_DATASETS);
        boolean includeDatasetData = getDataTypes().contains(StudyArchiveDataTypes.DATASET_DATA);

        for (DatasetDefinition dataset : study.getDatasetsByType(Dataset.TYPE_STANDARD, Dataset.TYPE_PLACEHOLDER))
        {
            if (includeDatasetData || (!dataset.isAssayData() && includeCRF) || (dataset.isAssayData() && includeAssay))
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

    public void setDatasets(Set<DatasetDefinition> datasets)
    {
        _datasets.clear();
        _datasetIds.clear();
        for (DatasetDefinition dataset : datasets)
        {
            _datasets.add(dataset);
            _datasetIds.add(dataset.getDatasetId());
        }
    }

    public ParticipantMapper getParticipantMapper()
    {
        return _participantMapper;
    }

    public Set<Integer> getVisitIds()
    {
        return _visitIds;
    }

    public void setVisitIds(Set<Integer> visits)
    {
        _visitIds = visits;
    }

    public List<String> getParticipants()
    {
        return _participants;
    }

    public void setParticipants(List<String> participants)
    {
        _participants = participants;
    }

    public List<Vial> getVials()
    {
        return _vials;
    }

    public void setVials(List<Vial> vials)
    {
        _vials = vials;
    }
}
