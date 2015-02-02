/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.study.model;

import org.labkey.api.data.Container;

import java.util.Date;

/**
 * User: brittp
 * Date: Feb 1, 2006
 * Time: 5:29:18 PM
 */
public class ParticipantDataset
{
    private String _lsid;
    private Container _container;
    private Double _sequenceNum;
    private Date _visitDate;
    private Integer _studyDatasetId;
    private String _studyParticipantId;

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public Integer getDatasetId()
    {
        return _studyDatasetId;
    }

    public void setDatasetId(Integer studyDatasetId)
    {
        _studyDatasetId = studyDatasetId;
    }

    public String getParticipantId()
    {
        return _studyParticipantId;
    }

    public void setParticipantId(String studyParticipantId)
    {
        _studyParticipantId = studyParticipantId;
    }

    public String getLsid()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Double getSequenceNum()
    {
        return _sequenceNum;
    }

    public void setSequenceNum(Double sequenceNum)
    {
        _sequenceNum = sequenceNum;
    }

    public Date getVisitDate()
    {
        return _visitDate;
    }

    public void setVisitDate(Date visitDate)
    {
        _visitDate = visitDate;
    }
}
