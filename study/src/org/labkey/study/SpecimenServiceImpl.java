/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.view.ActionURL;
import org.labkey.study.model.Specimen;

import java.sql.SQLException;
import java.util.Date;

/**
 * User: brittp
 * Date: Oct 2, 2007
 * Time: 3:38:28 PM
 */
public class SpecimenServiceImpl implements SpecimenService.Service
{
    private class StudyParticipantVisit implements ParticipantVisit
    {
        private Container _container;
        private String _participantID;
        private Double _visitID;
        private String _specimenID;
        private ExpMaterial _material;
        private Date _date;

        public StudyParticipantVisit(Container container, String specimenID, String participantID, Double visitID, Date date)
        {
            _container = container;
            _specimenID = specimenID;
            _participantID = participantID;
            _visitID = visitID;
            _date = date;
        }

        public String getParticipantID()
        {
            return _participantID;
        }

        public Double getVisitID()
        {
            return _visitID;
        }

        public String getSpecimenID()
        {
            return _specimenID;
        }

        public ExpMaterial getMaterial()
        {
            if (_material == null && _specimenID != null)
            {
                Lsid lsid = getSpecimenMaterialLsid(_container, _specimenID);
                _material = ExperimentService.get().getExpMaterial(lsid.toString());
            }
            return _material;
        }

        public Date getDate()
        {
            return _date;
        }

        public void setDate(Date date)
        {
            _date = date;
        }
    }

    public ParticipantVisit getSampleInfo(Container studyContainer, String sampleId) throws SQLException
    {
        Specimen[] matches = SampleManager.getInstance().getSpecimens(studyContainer, sampleId);
        if (matches.length > 0)
            return new StudyParticipantVisit(studyContainer, sampleId, matches[0].getPtid(), matches[0].getVisitValue(), matches[0].getDrawTimestamp());
        else
            return new StudyParticipantVisit(studyContainer, sampleId, null, null, null);
    }

    public ParticipantVisit getSampleInfo(Container studyContainer, String participantId, Date date) throws SQLException
    {
        if (null == studyContainer || null == StringUtils.trimToNull(participantId) || null == date)
            return new StudyParticipantVisit(studyContainer, null, participantId, null, date);
        
        Specimen[] matches = SampleManager.getInstance().getSpecimens(studyContainer, participantId, date);
        if (matches.length > 0)
            return new StudyParticipantVisit(studyContainer, matches[0].getGlobalUniqueId(), participantId, matches[0].getVisitValue(), matches[0].getDrawTimestamp());
        else
            return new StudyParticipantVisit(studyContainer, null, participantId, null, date);
    }

    public ParticipantVisit getSampleInfo(Container studyContainer, String participantId, Double visit) throws SQLException
    {
        if (null != studyContainer && null != StringUtils.trimToNull(participantId) && null != visit)
        {
            Specimen[] matches = SampleManager.getInstance().getSpecimens(studyContainer, participantId, visit);
            if (matches.length > 0)
                return new StudyParticipantVisit(studyContainer, matches[0].getGlobalUniqueId(), participantId, matches[0].getVisitValue(), matches[0].getDrawTimestamp());
        }
        return new StudyParticipantVisit(studyContainer, null, participantId, visit, null);
    }

    public String getCompletionURLBase(Container studyContainer, SpecimenService.CompletionType type)
    {
        ActionURL url = new ActionURL("Study-Samples", "autoComplete", studyContainer);
        url.addParameter("type", type.name());
        return url.getLocalURIString() + "&prefix=";
    }

    public Lsid getSpecimenMaterialLsid(Container studyContainer, String id)
    {
        return new Lsid("StudySpecimen", "Folder-" + studyContainer.getRowId(), id);
    }
}
