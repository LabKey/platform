package org.labkey.study.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.Entity;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 8, 2011
 * Time: 2:49:27 PM
 */

/**
 * Represents a classification of participants into related groups.
 */
public class ParticipantClassification extends Entity
{
    private int _rowId;
    private boolean _shared;
    private String _label;
    private String _type;
    private boolean _autoUpdate;

    // properties used when the type is a query
    private String _queryName;
    private String _schemaName;
    private String _viewName;

    // properties used when the type is a cohort (column in a dataset)
    private int _datasetId;
    private String _groupProperty;

    private String[] _participantIds = new String[0];
    private ParticipantGroup[] _groups = new ParticipantGroup[0];

    public enum Type {
        list,
        query,
        cohort,
    }

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        if (Type.valueOf(type) == null)
            throw new IllegalArgumentException("Invalid ParticipantClassification type");
        
        _type = type;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public void setViewName(String viewName)
    {
        _viewName = viewName;
    }

    public boolean isShared()
    {
        return _shared;
    }

    public void setShared(boolean shared)
    {
        _shared = shared;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isAutoUpdate()
    {
        return _autoUpdate;
    }

    public void setAutoUpdate(boolean autoUpdate)
    {
        _autoUpdate = autoUpdate;
    }

    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        _datasetId = datasetId;
    }

    public String getGroupProperty()
    {
        return _groupProperty;
    }

    public void setGroupProperty(String groupProperty)
    {
        _groupProperty = groupProperty;
    }

    public String[] getParticipantIds()
    {
        return _participantIds;
    }

    public void setParticipantIds(String[] participantIds)
    {
        if (!Type.list.name().equals(getType()))
            throw new UnsupportedOperationException("Only participant sets that are of type: list can accept an array of participant IDs.");

        _participantIds = participantIds;
    }

    public ParticipantGroup[] getGroups()
    {
        return _groups;
    }

    public void setGroups(ParticipantGroup[] groups)
    {
        _groups = groups;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        json.put("rowId", getRowId());
        json.put("shared", isShared());
        json.put("label", getLabel());
        json.put("type", getType());
        json.put("autoUpdate", isAutoUpdate());
        json.put("created", getCreated());
        json.put("createdBy", getCreatedBy());
        json.put("container", getContainerId());

        if (Type.query.equals(Type.valueOf(getType())))
        {
            json.put("queryName", getQueryName());
            json.put("schemaName", getSchemaName());
            json.put("viewName", getViewName());
        }

        if (Type.cohort.equals(Type.valueOf(getType())))
        {
            json.put("datasetId", getDatasetId());
            json.put("groupProperty", getGroupProperty());
        }

        // special case simple group list for now
        if (_groups.length == 1)
        {
            JSONArray ptids = new JSONArray();
            for (String ptid : _groups[0].getParticipantIds())
            {
                ptids.put(ptid);
            }
            json.put("participantIds", ptids);
        }

        return json;
    }

    public void copySpecialFields(ParticipantClassification copy)
    {
        if (getEntityId() == null)
            setEntityId(copy.getEntityId());
        if (getCreatedBy() == 0)
            setCreatedBy(copy.getCreatedBy());
        if (getCreated() == null)
            setCreated(copy.getCreated());
        if (getContainerId() == null)
            setContainer(copy.getContainerId());
    }
}
