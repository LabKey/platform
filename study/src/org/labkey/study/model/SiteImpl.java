/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.labkey.api.study.Site;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:38 AM
 */
public class SiteImpl extends AbstractStudyEntity<SiteImpl> implements Site
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

    public SiteImpl()
    {
    }

    public SiteImpl(Container container, String label)
    {
        super(container);
        _label = label;
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
}
