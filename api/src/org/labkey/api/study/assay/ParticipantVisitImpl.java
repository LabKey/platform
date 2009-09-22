/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.study.ParticipantVisit;
import org.labkey.api.data.Container;
import org.labkey.api.util.DateUtil;

import java.util.Date;

/**
 * User: brittp
* Date: Oct 2, 2007
* Time: 4:33:30 PM
*/
public class ParticipantVisitImpl implements ParticipantVisit
{
    private String _participantID;
    private Double _visitID;
    private String _specimenID;
    private Integer _cohortID;
    private Container _container;
    private ExpMaterial _material;
    private Date _date;

    /** Used for completely unspecified participant visit information */
    public ParticipantVisitImpl(Container runContainer)
    {
        this(null, null, null, null, runContainer);
    }

    public ParticipantVisitImpl(String specimenID, String participantID, Double visitID, Date date, Container runContainer)
    {
        _specimenID = specimenID;
        _participantID = participantID;
        _visitID = visitID;
        _date = date;
        _container = runContainer;
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
        if (_material == null)
        {
            StringBuilder name = new StringBuilder();
            if (_participantID != null)
            {
                name.append("Participant-");
                name.append(_participantID);
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
                name.append(DateUtil.formatDate(_date));
            }
            if (_specimenID != null)
            {
                if (name.length() > 0)
                {
                    name.append(".");
                }
                name.append("SpecimenID-");
                name.append(_specimenID);
            }

            if (name.length() == 0)
            {
                name.append("Unknown");
            }

            // the study couldn't find a good material, so we'll have to mock one up
            String lsid = new Lsid(ASSAY_RUN_MATERIAL_NAMESPACE, "Folder-" + _container.getRowId(), name.toString()).toString();
            _material = ExperimentService.get().getExpMaterial(lsid);
            if (_material == null)
            {
                _material = ExperimentService.get().createExpMaterial(_container, lsid, name.toString());
                _material.save(null);
            }
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
        return !(_visitID != null ? !_visitID.equals(that._visitID) : that._visitID != null);
    }

    public int hashCode()
    {
        int result;
        result = (_participantID != null ? _participantID.hashCode() : 0);
        result = 31 * result + (_visitID != null ? _visitID.hashCode() : 0);
        result = 31 * result + (_specimenID != null ? _specimenID.hashCode() : 0);
        result = 31 * result + (_cohortID != null ? _cohortID.hashCode() : 0);
        result = 31 * result + (_date != null ? _date.hashCode() : 0);
        return result;
    }
}
