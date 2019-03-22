/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.labkey.api.data.Container;
import org.labkey.api.reports.model.ViewCategory;

import java.io.IOException;
import java.util.Date;

/**
 * User: Karl Lum
 * Date: Nov 3, 2006
 */
public class ReportDB
{
    private int _rowId;
    private String _containerId;
    private int _createdBy;
    private int _modifiedBy;
    private Date _created;
    private Date _modified;
    private String _key;
    private String _descriptorXML;
    private String _entityId;
    private Integer _reportOwner;
    private int _flags;
    private Integer _categoryId;
    private Integer _displayOrder;
    private Date _contentModified;

    public ReportDB(){}

    public ReportDB(Container c, String key, ReportDescriptor descriptor)
    {
        try
        {
            _containerId = c.getId();
            _entityId = descriptor.getEntityId();
            _createdBy = descriptor.getCreatedBy();
            _created = descriptor.getCreated();
            _key = key;
            _descriptorXML = descriptor.serialize(c);
            _reportOwner = descriptor.getOwner();
            _flags = descriptor.getFlags();
            _displayOrder = descriptor.getDisplayOrder();
            _contentModified = descriptor.getContentModified();
            if (_contentModified == null)
                _contentModified = new java.sql.Timestamp(System.currentTimeMillis());

            ViewCategory category = descriptor.getCategory(c);
            if (category != null)
                _categoryId = category.getRowId();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void setRowId(int rowId){_rowId = rowId;}
    public int getRowId(){return _rowId;}
    public void setContainerId(String id){_containerId = id;}
    public String getContainerId(){return _containerId;}
    public void setCreatedBy(int id){_createdBy = id;}
    public int getCreatedBy(){return _createdBy;}
    public void setModifiedBy(int id){_modifiedBy = id;}
    public int getModifiedBy(){return _modifiedBy;}
    public void setCreated(Date created){_created = created;}
    public Date getCreated(){return _created;}
    public void setModified(Date modified){_modified = modified;}
    public Date getModified(){return _modified;}
    public void setReportKey(String key){_key = key;}
    public String getReportKey(){return _key;}
    public void setDescriptorXML(String xml){_descriptorXML = xml;}
    public String getDescriptorXML(){return _descriptorXML;}
    public void setEntityId(String entity){_entityId = entity;}
    public String getEntityId(){return _entityId;}
    public Integer getReportOwner(){return _reportOwner;}
    public void setReportOwner(Integer owner){_reportOwner = owner;}

    public Integer getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        _categoryId = categoryId;
    }

    public Integer getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder)
    {
        _displayOrder = displayOrder;
    }

    public int getFlags()
    {
        return _flags;
    }

    public void setFlags(int flags)
    {
        _flags = flags;
    }

    public void setContentModified(Date contentModified)
    {
        _contentModified = contentModified;
    }
    public Date getContentModified()
    {
        return _contentModified;
    }
}
