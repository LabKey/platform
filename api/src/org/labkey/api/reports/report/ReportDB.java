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

package org.labkey.api.reports.report;

import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;

import java.util.Date;

/**
 * Created by IntelliJ IDEA.
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

    public ReportDB(){}
    public ReportDB(Container c, int userId, String key, ReportDescriptor descriptor)
    {
        _containerId = c.getId();
        _entityId = descriptor.getEntityId();
        _createdBy = descriptor.getCreatedBy();
        _created = descriptor.getCreated();
        _key = key;
        _descriptorXML = descriptor.serialize();
        _reportOwner = descriptor.getOwner();
        _flags = descriptor.getFlags();
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

    public int getFlags()
    {
        return _flags;
    }

    public void setFlags(int flags)
    {
        _flags = flags;
    }
}
