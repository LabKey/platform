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
import org.labkey.api.study.Location;

import java.util.Map;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:38 AM
 */
public class LocationImpl extends AbstractStudyEntity<LocationImpl> implements Location
{
    private int _rowId; // INT IDENTITY(1,1),
    private Integer _externalId; // INT,
    private Integer _ldmsLabCode; // INT,
    private String _labwareLabCode; // NVARCHAR(20),
    private String _labUploadCode; // NVARCHAR(2),
    private Boolean _sal = Boolean.FALSE; // Bit,
    private Boolean _clinic = Boolean.FALSE; // Bit,
    private Boolean _repository = Boolean.FALSE; // Bit,
    private Boolean _endpoint = Boolean.FALSE; // Bit,
    private String _label;
    private String _description;
    private String _streetAddress;
    private String _city;
    private String _governingDistrict;
    private String _country;
    private String _postalArea;

    public LocationImpl()
    {
    }

    public LocationImpl(Container container, String label)
    {
        super(container);
        _label = label;
    }

    public LocationImpl(Container container, Map<String, Object> map)
    {
        super(container);
        setEntityId((String)map.get("EntityId"));
        _rowId = (int)map.get("RowId");
        _label  = (String)map.get("Label");
        _externalId  = (Integer)map.get("ExternalId");
        _ldmsLabCode  = (Integer)map.get("LdmsLabCode");
        _labwareLabCode  = (String)map.get("LabwareLabCode");
        _labUploadCode  = (String)map.get("LabUploadCode");
        _repository  = (Boolean)map.get("Repository");
        _clinic  = (Boolean)map.get("Clinic");
        _sal  = (Boolean)map.get("SAL");
        _endpoint  = (Boolean)map.get("Endpoint");
        _description  = (String)map.get("Description");
        _streetAddress  = (String)map.get("StreetAddress");
        _city  = (String)map.get("City");
        _governingDistrict  = (String)map.get("GoverningDistrict");
        _country  = (String)map.get("Country");
        _postalArea = (String)map.get("PostalArea");
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public Boolean isEndpoint()
    {
        return _endpoint;
    }

    public void setEndpoint(Boolean endpoint)
    {
        verifyMutability();
        this._endpoint = endpoint != null ? endpoint : Boolean.FALSE;
    }

    public Boolean isRepository()
    {
        return _repository;
    }

    public void setRepository(Boolean repository)
    {
        verifyMutability();
        _repository = repository != null ? repository : Boolean.FALSE;
    }

    public Boolean isSal()
    {
        return _sal;
    }

    public void setSal(Boolean sal)
    {
        verifyMutability();
        _sal = sal != null ? sal : Boolean.FALSE;
    }

    public Boolean isClinic()
    {
        return _clinic;
    }

    public void setClinic(Boolean clinic)
    {
        verifyMutability();
        _clinic = clinic != null ? clinic : Boolean.FALSE;
    }

    public String getLabUploadCode()
    {
        return _labUploadCode;
    }

    public void setLabUploadCode(String labUploadCode)
    {
        verifyMutability();
        _labUploadCode = labUploadCode;
    }

    public String getLabwareLabCode()
    {
        return _labwareLabCode;
    }

    public void setLabwareLabCode(String labwareLabCode)
    {
        verifyMutability();
        _labwareLabCode = labwareLabCode;
    }

    public Integer getLdmsLabCode()
    {
        return _ldmsLabCode;
    }

    public void setLdmsLabCode(Integer ldmsLabCode)
    {
        verifyMutability();
        _ldmsLabCode = ldmsLabCode;
    }

    public Integer getExternalId()
    {
        return _externalId;
    }

    public void setExternalId(Integer externalId)
    {
        verifyMutability();
        _externalId = externalId;
    }

    public String getDisplayName()
    {
        String label = getLabel();
        if (label == null)
        {
            if (getLdmsLabCode() != null)
                return "LDMS " + getLdmsLabCode() + " (Unlabeled)";
            else if (getLabwareLabCode() != null)
                return "Labware " + getLabwareLabCode() + " (Unlabeled)";
            else
                return "(Unlabeled)";
        }
        else
        {
            String type = getTypeString();
            if (type.length() == 0)
                return label;
            else
                return label + " (" + type + ")";
        }
    }

    public String getTypeString()
    {
        StringBuilder typeString = new StringBuilder();
        if (isEndpoint().booleanValue())
            typeString.append("Endpoint Lab");
        if (isRepository().booleanValue())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Repository");
        }
        if (isSal().booleanValue())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Site Affiliated Lab");
        }
        if (isClinic().booleanValue())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Clinic");
        }
        return typeString.toString();
    }

    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        verifyMutability();
        _description = description;
    }

    public String getStreetAddress()
    {
        return _streetAddress;
    }

    public void setStreetAddress(String streetAddress)
    {
        verifyMutability();
        _streetAddress = streetAddress;
    }

    public String getCity()
    {
        return _city;
    }

    public void setCity(String city)
    {
        verifyMutability();
        _city = city;
    }

    public String getGoverningDistrict()
    {
        return _governingDistrict;
    }

    public void setGoverningDistrict(String governingDistrict)
    {
        verifyMutability();
        _governingDistrict = governingDistrict;
    }

    public String getCountry()
    {
        return _country;
    }

    public void setCountry(String country)
    {
        verifyMutability();
        _country = country;
    }

    public String getPostalArea()
    {
        return _postalArea;
    }

    public void setPostalArea(String postalArea)
    {
        verifyMutability();
        _postalArea = postalArea;
    }
}
