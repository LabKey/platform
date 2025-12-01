package org.labkey.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONObject;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class CreatedModified
{
    private Long _created;
    private User _createdBy;
    private Long _modified;
    private User _modifiedBy;

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

    public void setCreatedBy(int createdById)
    {
        _createdBy = UserManager.getUser(createdById);
    }

    @JsonProperty("modified")
    public Long getModified()
    {
        return _modified;
    }

    @JsonIgnore
    public Date getModifiedDate()
    {
        return _modified == null ? null : new Date(_modified);
    }

    public void setModified(Long modified)
    {
        _modified = modified;
    }

    @JsonIgnore // modified is serialized as Long
    public void setModified(Date modified)
    {
        if (modified != null)
            setModified(modified.getTime());
    }

    @JsonProperty("modifiedBy")
    public JSONObject getModifiedBy()
    {
        if (_modifiedBy == null)
            return null;
        return _modifiedBy.getUserProps();
    }

    @JsonIgnore
    public User getModifiedByUser()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public void setModifiedBy(int modifiedById)
    {
        _modifiedBy = UserManager.getUser(modifiedById);
    }
}
