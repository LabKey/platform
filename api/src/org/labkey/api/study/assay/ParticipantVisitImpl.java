/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.util.DateUtil;

import java.util.Date;

/**
 * User: brittp
* Date: Oct 2, 2007
* Time: 4:33:30 PM
*/
public class ParticipantVisitImpl implements ParticipantVisit
{
    private final String _participantID;
    private final Double _visitID;
    private final String _specimenID;
    private Integer _cohortID;
    private final Container _runContainer;
    private final Container _studyContainer;
    private ExpMaterial _material;
    private boolean _attemptedMaterialResolution;
    private Date _date;

    public ParticipantVisitImpl(String specimenID, String participantID, Double visitID, Date date, Container runContainer, Container studyContainer)
    {
        _specimenID = specimenID;
        _participantID = participantID;
        _visitID = visitID;
        _date = date;
        _runContainer = runContainer;
        _studyContainer = studyContainer;
    }

    @Override
    public String getParticipantID()
    {
        return _participantID;
    }

    @Override
    public Double getVisitID()
    {
        return _visitID;
    }

    @Override
    public String getSpecimenID()
    {
        return _specimenID;
    }

    public Container getRunContainer()
    {
        return _runContainer;
    }

    @Override
    public Container getStudyContainer()
    {
        return _studyContainer;
    }

    /** Prevent generated LSIDs from getting too long - see issue 25929 */
    private String truncate(@NotNull String s, int maxLength)
    {
        return s.length() < maxLength ? s : s.substring(0, maxLength - 1);
    }

    @Override @Nullable
    public ExpMaterial getMaterial(boolean createIfNeeded)
    {
        if (_material == null && !_attemptedMaterialResolution)
        {
            StringBuilder name = new StringBuilder();
            if (_participantID != null)
            {
                name.append("Participant-");
                name.append(truncate(_participantID, 40));
            }
            if (_visitID != null)
            {
                if (name.length() > 0)
                {
                    name.append(".");
                }
                name.append("Visit-");
                name.append(_visitID);
            }
            if (_date != null)
            {
                if (name.length() > 0)
                {
                    name.append(".");
                }
                name.append("Date-");
                name.append(DateUtil.formatDateISO8601(_date)); // Use fixed format since we use it in LSIDs
            }
            if (_specimenID != null)
            {
                if (name.length() > 0)
                {
                    name.append(".");
                }
                name.append("SpecimenID-");
                name.append(truncate(_specimenID, 40));
            }

            if (name.length() == 0)
            {
                name.append("Unknown");
            }

            // the study couldn't find a good material, so we'll have to mock one up
            String lsid = new Lsid(ASSAY_RUN_MATERIAL_NAMESPACE, "Folder-" + _runContainer.getRowId(), name.toString()).toString();
            _material = ExperimentService.get().getExpMaterial(lsid);
            _attemptedMaterialResolution = true;
            // Issue 41364 - don't create a material if it doesn't already exist for most assays
            if (_material == null && createIfNeeded)
            {
                _material = ExperimentService.get().createExpMaterial(_runContainer, lsid, name.toString());
                _material.save(null);
            }
        }
        return _material;
    }

    @Override
    public Date getDate()
    {
        return _date;
    }

    public void setDate(Date date)
    {
        _date = date;
    }

    @Override
    public Integer getCohortID()
    {
        return _cohortID;
    }

    public void setCohortID(Integer cohortID)
    {
        _cohortID = cohortID;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParticipantVisitImpl that = (ParticipantVisitImpl) o;

        if (_date != null ? !_date.equals(that._date) : that._date != null) return false;
        if (_participantID != null ? !_participantID.equals(that._participantID) : that._participantID != null)
            return false;
        if (_specimenID != null ? !_specimenID.equals(that._specimenID) : that._specimenID != null) return false;
        if (_cohortID != null ? !_cohortID.equals(that._cohortID) : that._cohortID != null) return false;
        if (_visitID != null ? !_visitID.equals(that._visitID) : that._visitID != null) return false;
        if (_runContainer != null ? !_runContainer.equals(that._runContainer) : that._runContainer != null) return false;
        return _studyContainer != null ? _studyContainer.equals(that._studyContainer) : that._studyContainer == null;
    }

    public int hashCode()
    {
        int result;
        result = (_participantID != null ? _participantID.hashCode() : 0);
        result = 31 * result + (_visitID != null ? _visitID.hashCode() : 0);
        result = 31 * result + (_specimenID != null ? _specimenID.hashCode() : 0);
        result = 31 * result + (_cohortID != null ? _cohortID.hashCode() : 0);
        result = 31 * result + (_date != null ? _date.hashCode() : 0);
        result = 31 * result + (_runContainer != null ? _runContainer.hashCode() : 0);
        result = 31 * result + (_studyContainer != null ? _studyContainer.hashCode() : 0);
        return result;
    }
}
