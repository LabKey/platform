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
package org.labkey.api.assay.dilution;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.nab.view.RunDetailOptions;
import org.labkey.api.data.Container;
import org.labkey.api.util.DateUtil;

import java.util.Date;

/**
* User: brittp
* Date: Jun 26, 2009
* Time: 10:22:06 AM
*/
public class DilutionMaterialKey
{
    private Container _container;
    private String _specimenId;
    private String _participantId;
    private Double _visitId;
    private Date _date;
    private String _virusName;

    public DilutionMaterialKey(Container container, String specimenId, String participantId, Double visitId, Date date, String virusName)
    {
        _container = container;
        _specimenId = specimenId;
        _participantId = participantId;
        _visitId = visitId;
        _date = date;
        _virusName = virusName;
    }

    private void appendAndSeparate(StringBuilder builder, String append)
    {
        if (append != null)
        {
            if (builder.length() > 0)
                builder.append(", ");
            builder.append(append);
        }
    }

    public String getDisplayString(@Nullable RunDetailOptions.DataIdentifier identifier)
    {
        if (identifier != null)
        {
            switch (identifier)
            {
                case Specimen:
                    return _specimenId;
                case ParticipantVisit:
                    return _participantId + ", Vst " + _visitId;
                case ParticipantDate:
                    if (_date != null)
                        return _participantId + ", " + DateUtil.formatDate(_container, _date);
                    break;
                case SpecimenParticipantVisit:
                    return _specimenId + ", " + _participantId + ", Vst " + _visitId;

                case LongFormat:
                    return getDisplayString(true);
                case DefaultFormat:
                    return getDisplayString(false);
            }
        }
        return getDisplayString(false);
    }

    private String getDisplayString(boolean longForm)
    {
        if (longForm)
        {
            StringBuilder builder = new StringBuilder();
            appendAndSeparate(builder, _specimenId);
            appendAndSeparate(builder, _participantId);
            if (_visitId == null && _date != null)
            {
                if (_date.getHours() == 0 && _date.getMinutes() == 0 && _date.getSeconds() == 0)
                    appendAndSeparate(builder, DateUtil.formatDate(_container, _date));
                else
                    appendAndSeparate(builder, DateUtil.formatDateTime(_container, _date));
            }
            else if (_visitId != null)
                appendAndSeparate(builder, "Vst " + _visitId);
            if (_virusName != null)
                appendAndSeparate(builder, _virusName);
            return builder.toString();
        }
        else
        {
            if (_specimenId != null)
                return _specimenId;
            else if (_visitId == null && _date != null)
                return _participantId + ", " + DateUtil.formatDate(_container, _date);
            else
                return _participantId + ", Vst " + _visitId;
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DilutionMaterialKey that = (DilutionMaterialKey) o;

        if (_date != null ? !_date.equals(that._date) : that._date != null) return false;
        if (_participantId != null ? !_participantId.equals(that._participantId) : that._participantId != null)
            return false;
        if (_specimenId != null ? !_specimenId.equalsIgnoreCase(that._specimenId) : that._specimenId != null) return false;
        if (_visitId != null ? !_visitId.equals(that._visitId) : that._visitId != null) return false;
        if (_virusName != null ? !_virusName.equalsIgnoreCase(that._virusName) : that._virusName != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _specimenId != null ? _specimenId.toLowerCase().hashCode() : 0;
        result = 31 * result + (_participantId != null ? _participantId.hashCode() : 0);
        result = 31 * result + (_visitId != null ? _visitId.hashCode() : 0);
        result = 31 * result + (_date != null ? _date.hashCode() : 0);
        result = 31 * result + (_virusName != null ? _virusName.hashCode() : 0);
        return result;
    }
}
