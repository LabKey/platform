/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;

import java.util.Map;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:28:38 AM
 */
public class Site extends AbstractStudyEntity<Site>
{
    private int _rowId; // INT IDENTITY(1,1),
    private Integer _externalId; // INT,
    private Integer _ldmsLabCode; // INT,
    private String _labwareLabCode; // NVARCHAR(20),
    private String _labUploadCode; // NVARCHAR(2),
    private boolean _sal; // Bit,
    private boolean _clinic; // Bit,
    private boolean _repository; // Bit,
    private boolean endpoint; // Bit,
    private String _label;

    public Site()
    {
    }

    public Site(Map<String, ? extends Object> rsRowMap)
    {
        setContainer(ContainerManager.getForId((String) rsRowMap.get("Container")));
        setEndpoint(safeBooleanEntryConvert(rsRowMap, "endpoint"));
        setEntityId((String) rsRowMap.get("EntityId"));
        setLabel((String) rsRowMap.get("Label"));
        setLabUploadCode((String) rsRowMap.get("LabUploadCode"));
        setLabwareLabCode((String) rsRowMap.get("LabwareLabCode"));
        setLdmsLabCode((Integer) rsRowMap.get("LdmsLabCode"));
        setRepository(safeBooleanEntryConvert(rsRowMap, "repository"));
        setRowId((Integer) rsRowMap.get("RowId"));
        setSal(safeBooleanEntryConvert(rsRowMap, "sal"));
        setClinic(safeBooleanEntryConvert(rsRowMap, "clinic"));
        setExternalId((Integer) rsRowMap.get("ExternalId"));
    }

    private boolean safeBooleanEntryConvert(Map<String, ? extends Object> rsRowMap, String booleanColName)
    {
        return rsRowMap.containsKey(booleanColName) &&
                rsRowMap.get(booleanColName) != null &&
                (Boolean) rsRowMap.get(booleanColName);
    }

    public Site(Container container, String label)
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

    public boolean isEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(boolean endpoint)
    {
        verifyMutability();
        this.endpoint = endpoint;
    }

    public boolean isRepository()
    {
        return _repository;
    }

    public void setRepository(boolean repository)
    {
        verifyMutability();
        _repository = repository;
    }

    public boolean isSal()
    {
        return _sal;
    }

    public void setSal(boolean sal)
    {
        verifyMutability();
        _sal = sal;
    }

    public boolean isClinic()
    {
        return _clinic;
    }

    public void setClinic(boolean clinic)
    {
        verifyMutability();
        _clinic = clinic;
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
        if (isEndpoint())
            typeString.append("Endpoint Lab");
        if (isRepository())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Repository");
        }
        if (isSal())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Site Affiliated Lab");
        }
        if (isClinic())
        {
            if (typeString.length() > 0)
                typeString.append(", ");
            typeString.append("Clinic");
        }
        return typeString.toString();
    }

    @Override
    protected boolean supportsACLUpdate()
    {
        return true;
    }
}
