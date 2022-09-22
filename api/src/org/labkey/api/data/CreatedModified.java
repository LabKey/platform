package org.labkey.api.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.old.JSONObject;
import org.labkey.api.security.User;

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

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    @JsonProperty("modified")
    public Long getModified()
    {
        return _modified;
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

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }
}
