/*
 * Copyright (c) 2007 LabKey Corporation
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
import org.labkey.api.study.ParticipantVisit;

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
    private ExpMaterial _material;
    private Date _date;

    public ParticipantVisitImpl(String specimenID, String participantID, Double visitID, Date date)
    {
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
        return _material;
    }

    public void setMaterial(ExpMaterial material)
    {
        _material = material;
    }

    public Date getDate()
    {
        return _date;
    }

    public void setDate(Date date)
    {
        _date = date;
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
        if (_visitID != null ? !_visitID.equals(that._visitID) : that._visitID != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (_participantID != null ? _participantID.hashCode() : 0);
        result = 31 * result + (_visitID != null ? _visitID.hashCode() : 0);
        result = 31 * result + (_specimenID != null ? _specimenID.hashCode() : 0);
        result = 31 * result + (_date != null ? _date.hashCode() : 0);
        return result;
    }
}
