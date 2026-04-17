package org.labkey.api.workflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;

import java.util.Date;
import java.util.Map;

public class WorkEntity
{
    public enum WorkType
    {
        Job,
        Task,
        Action
    }

    public enum EntityType
    {
        Sample,
        Source,
        Plate,
    }

    protected Long _rowId;
    protected GUID _containerId;
    protected WorkType _workType;
    protected Long _workRowId;
    protected EntityType _entityType;
    protected Long _entityValue;
    protected Long _created;
    protected User _createdBy;

    public WorkEntity()
    {}

    public WorkEntity(Map<String, Object> map)
    {
        _rowId = MapUtils.getLong(map, "rowId");
        if (map.get("workType") instanceof String)
            _workType = WorkType.valueOf((String) map.get("workType"));
        else
            _workType = (WorkType) map.get("workType");
        _workRowId = MapUtils.getLong(map, "workRowId");
        if (map.get("entityType") instanceof String)
            _entityType = EntityType.valueOf((String) map.get("entityType"));
        else
            _entityType = (EntityType) map.get("entityType");
        _entityValue = MapUtils.getLong(map, "entityValue");
        if (map.get("Container") != null)
            this.setContainerId(new GUID((String) map.get("Container")));
    }

    public WorkEntity(ExpMaterial sample)
    {
        _entityType = EntityType.Sample;
        _entityValue = sample.getRowId();
    }

    public WorkEntity(ExpMaterial sample, WorkType workType, Long workRowId)
    {
        this(sample);
        _workType = workType;
        _workRowId = workRowId;
    }


    public Long getRowId()
    {
        return _rowId;
    }

    public void setRowId(Long rowId)
    {
        _rowId = rowId;
    }

    public GUID getContainerId()
    {
        return _containerId;
    }

    public void setContainerId(GUID containerId)
    {
        _containerId = containerId;
    }

    @JsonProperty("created")
    public Long getCreated()
    {
        return _created;
    }

    @JsonIgnore
    public Date getCreatedDate()
    {
        return _created == null ? null : new Date(_created);
    }

    public void setCreated(Long created)
    {
        _created = created;
    }

    @JsonIgnore // created is serialized as Long
    public void setCreated(Date created)
    {
        if (created != null)
            setCreated(created.getTime());
    }

    @JsonProperty("createdBy")
    public JSONObject getCreatedBy()
    {
        if (_createdBy == null)
            return null;
        return _createdBy.getUserProps();
    }

    @JsonIgnore
    public User getCreatedByUser()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public void setCreatedBy(Integer createdById)
    {
        if (createdById != null)
            _createdBy = UserManager.getUser(createdById);
    }

    public WorkType getWorkType()
    {
        return _workType;
    }

    public void setWorkType(WorkType workType)
    {
        _workType = workType;
    }

    public void setWorkType(String workTypeStr)
    {
        _workType = StringUtils.isEmpty(StringUtils.trimToEmpty(workTypeStr)) ? null : WorkType.valueOf(workTypeStr.trim());
    }

    public Long getWorkRowId()
    {
        return _workRowId;
    }

    public void setWorkRowId(Long workRowId)
    {
        _workRowId = workRowId;
    }

    public EntityType getEntityType()
    {
        return _entityType;
    }

    public void setEntityType(EntityType entityType)
    {
        _entityType = entityType;
    }

    public void setValueType(String valueTypeStr)
    {
        _entityType = EntityType.valueOf(valueTypeStr);
    }

    public Long getEntityValue()
    {
        return _entityValue;
    }

    public void setEntityValue(Long entityValue)
    {
        _entityValue = entityValue;
    }

    @JsonIgnore
    public String getKey()
    {
        return _entityType + ":" + _entityValue;
    }
}
