/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.study;

import java.util.Date;

/**
 * Bean representing a row in the table of study {@link Dataset}s.
 */
public class DatasetDB
{
    private String _container;
    private int _datasetId;
    private String _typeUri;
    private String _label;
    private Boolean _showByDefault;
    private int _displayOrder;
    private String _entityId;
    private String visitDatePropertyName;
    private String _keyPropertyName;
    private String _name;
    private String _descripton;
    private Boolean _demographicData;
    private Integer _cohortId;
    private Integer _protocolId;
    private String _keyManagementType;
    private Integer _categoryId;
    private Date _modified;
    private String _type;

    public DatasetDB() {}

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        _datasetId = datasetId;
    }

    public String getTypeUri()
    {
        return _typeUri;
    }

    public void setTypeUri(String typeUri)
    {
        _typeUri = typeUri;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public Boolean getShowByDefault()
    {
        return _showByDefault;
    }

    public void setShowByDefault(Boolean showByDefault)
    {
        _showByDefault = showByDefault;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getVisitDatePropertyName()
    {
        return visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        this.visitDatePropertyName = visitDatePropertyName;
    }

    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        _keyPropertyName = keyPropertyName;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDescripton()
    {
        return _descripton;
    }

    public void setDescripton(String descripton)
    {
        _descripton = descripton;
    }

    public Boolean getDemographicData()
    {
        return _demographicData;
    }

    public void setDemographicData(Boolean demographicData)
    {
        _demographicData = demographicData;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    public Integer getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(Integer protocolId)
    {
        _protocolId = protocolId;
    }

    public String getKeyManagementType()
    {
        return _keyManagementType;
    }

    public void setKeyManagementType(String keyManagementType)
    {
        _keyManagementType = keyManagementType;
    }

    public Integer getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        _categoryId = categoryId;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }
}
