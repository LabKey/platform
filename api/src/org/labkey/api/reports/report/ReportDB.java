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
        _createdBy = userId;
        _created = new Date();
        _key = key;
        _descriptorXML = descriptor.serialize();
        _reportOwner = descriptor.getOwner();
        _flags = descriptor.getFlags();
    }

    public ReportDB(Report r)
    {
        _containerId = r.getDescriptor().getContainerId();
        _entityId = r.getDescriptor().getEntityId();
        _createdBy = r.getDescriptor().getCreatedBy();
        _created = r.getDescriptor().getCreated();
        _key = r.getDescriptor().getReportKey();
        _descriptorXML = r.getDescriptor().serialize();
        _reportOwner = r.getDescriptor().getOwner();
        _flags = r.getDescriptor().getFlags();
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
