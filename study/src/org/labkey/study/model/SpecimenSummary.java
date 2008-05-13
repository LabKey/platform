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

package org.labkey.study.model;

import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * User: brittp
 * Date: Mar 2, 2007
 * Time: 11:36:17 AM
 */
public class SpecimenSummary
{
    private Container _container;
    private String _specimenNumber;
    private Integer _primaryType;
    private Integer _derivativeType;
    private String _ptid;
    private String _visitDescription;
    private Double _visitValue;
    private String _volumeUnits;
    private Integer _additiveTypeId;
    private Date _drawTimestamp;
    private Date _salReceiptDate;
    private String _classId;
    private String _protocolNumber;
    private String _subAdditiveDerivative;

    public SpecimenSummary(Specimen specimen)
    {
        _container = specimen.getContainer();
        _specimenNumber = specimen.getSpecimenNumber();
        _primaryType = specimen.getPrimaryTypeId();
        _derivativeType = specimen.getDerivativeTypeId();
        _ptid = specimen.getPtid();
        _visitDescription = specimen.getVisitDescription();
        _visitValue = specimen.getVisitValue();
        _volumeUnits = specimen.getVolumeUnits();
        _additiveTypeId = specimen.getAdditiveTypeId();
        _drawTimestamp = specimen.getDrawTimestamp();
        _salReceiptDate = specimen.getSalReceiptDate();
        _classId = specimen.getClassId();
        _protocolNumber = specimen.getProtocolNumber();
        _subAdditiveDerivative = specimen.getSubAdditiveDerivative();
    }

    public SpecimenSummary(Container container, Map<String, Object> dbRow)
    {
        _container = container;
        _specimenNumber = (String) dbRow.get("SpecimenNumber");
        _primaryType = (Integer) dbRow.get("PrimaryType");
        _derivativeType = (Integer) dbRow.get("DerivativeType");
        _ptid = (String) dbRow.get("ParticipantId");
        _visitDescription = (String) dbRow.get("VisitDescription");
        _visitValue = (Double) dbRow.get("Visit");
        _volumeUnits = (String) dbRow.get("VolumeUnits");
        _additiveTypeId = (Integer) dbRow.get("AdditiveType");
        _drawTimestamp = (Date) dbRow.get("DrawTimestamp");
        _salReceiptDate = (Date) dbRow.get("SalReceiptDate");
        _classId = (String) dbRow.get("ClassId");
        _protocolNumber = (String) dbRow.get("ProtocolNumber");
        _subAdditiveDerivative = (String) dbRow.get("SubAdditiveDerivative");
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpecimenSummary that = (SpecimenSummary) o;

        if (_additiveTypeId != null ? !_additiveTypeId.equals(that._additiveTypeId) : that._additiveTypeId != null)
            return false;
        if (_classId != null ? !_classId.equals(that._classId) : that._classId != null)
            return false;
        if (!_container.equals(that._container)) return false;
        if (_derivativeType != null ? !_derivativeType.equals(that._derivativeType) : that._derivativeType != null)
            return false;
        if (_drawTimestamp != null ? !_drawTimestamp.equals(that._drawTimestamp) : that._drawTimestamp != null)
            return false;
        if (_primaryType != null ? !_primaryType.equals(that._primaryType) : that._primaryType != null)
            return false;
        if (_protocolNumber != null ? !_protocolNumber.equals(that._protocolNumber) : that._protocolNumber != null)
            return false;
        if (_ptid != null ? !_ptid.equals(that._ptid) : that._ptid != null) return false;
        if (_salReceiptDate != null ? !_salReceiptDate.equals(that._salReceiptDate) : that._salReceiptDate != null)
            return false;
        if (!_specimenNumber.equals(that._specimenNumber)) return false;
        if (_subAdditiveDerivative != null ? !_subAdditiveDerivative.equals(that._subAdditiveDerivative) : that._subAdditiveDerivative != null)
            return false;
        if (_visitDescription != null ? !_visitDescription.equals(that._visitDescription) : that._visitDescription != null)
            return false;
        if (_visitValue != null ? !_visitValue.equals(that._visitValue) : that._visitValue != null)
            return false;
        if (_volumeUnits != null ? !_volumeUnits.equals(that._volumeUnits) : that._volumeUnits != null)
            return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _container.hashCode();
        result = 31 * result + _specimenNumber.hashCode();
        result = 31 * result + (_primaryType != null ? _primaryType.hashCode() : 0);
        result = 31 * result + (_derivativeType != null ? _derivativeType.hashCode() : 0);
        result = 31 * result + (_ptid != null ? _ptid.hashCode() : 0);
        result = 31 * result + (_visitDescription != null ? _visitDescription.hashCode() : 0);
        result = 31 * result + (_visitValue != null ? _visitValue.hashCode() : 0);
        result = 31 * result + (_volumeUnits != null ? _volumeUnits.hashCode() : 0);
        result = 31 * result + (_additiveTypeId != null ? _additiveTypeId.hashCode() : 0);
        result = 31 * result + (_drawTimestamp != null ? _drawTimestamp.hashCode() : 0);
        result = 31 * result + (_salReceiptDate != null ? _salReceiptDate.hashCode() : 0);
        result = 31 * result + (_classId != null ? _classId.hashCode() : 0);
        result = 31 * result + (_protocolNumber != null ? _protocolNumber.hashCode() : 0);
        result = 31 * result + (_subAdditiveDerivative != null ? _subAdditiveDerivative.hashCode() : 0);
        return result;
    }
}
