/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.api.data;

import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: klum
 * Date: Jun 10, 2011
 * Time: 3:16:27 PM
 */
abstract public class AbstractParticipantGroup<T> extends Entity
{
    private int _rowId;
    private String _label;
    private int _categoryId;  // fk to participant category
    private String _categoryLabel;
    private String _filters;
    private String _description;
    private boolean _session;

    protected Set<T> _participantIds = new LinkedHashSet<>();

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

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public int getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(int categoryId)
    {
        _categoryId = categoryId;
    }

    public Set<T> getParticipantSet()
    {
        return new LinkedHashSet<>(_participantIds);
    }

    public void setParticipantSet(Set<T> participantSet)
    {
        _participantIds = new LinkedHashSet<>(participantSet);
    }

    abstract public T[] getParticipantIds();

    public void setParticipantIds(T[] participantIds)
    {
        Set<T> participants = new LinkedHashSet<>();
        Set<T> duplicates = new LinkedHashSet<>();

        // validate that there are no duplicates
        for (T id : participantIds)
        {
            if (participants.contains(id))
                duplicates.add(id);

            participants.add(id);
        }

        if (!duplicates.isEmpty())
        {
            StringBuilder sb = new StringBuilder();

            sb.append("Duplicate ID(s) were found: ");
            String delim = "";
            int i=0;

            for (T id : duplicates)
            {
                sb.append(delim);
                sb.append(id);
                delim = ", ";

                if (++i >= 5)
                    break;
            }

            if (duplicates.size() > 5)
            {
                sb.append(" (5 shown out of a total of: ");
                sb.append(duplicates.size());
                sb.append(")");
            }
            sb.append(". Duplicates are not allowed in a group.");

            throw new IllegalArgumentException(sb.toString());
        }
        _participantIds = participants;
    }

    public void addParticipantId(T participantId)
    {
        if (_participantIds.contains(participantId))
            throw new IllegalArgumentException("ID :" + participantId + " is specified more than once, duplicates are not allowed in a group.");

        _participantIds.add(participantId);
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getFilters()
    {
        return _filters;
    }

    public void setFilters(String filters)
    {
        _filters = filters;
    }

    public String getCategoryLabel()
    {
        return _categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel)
    {
        _categoryLabel = categoryLabel;
    }

    public boolean isSession()
    {
        return _session;
    }

    public void setSession(boolean session)
    {
        _session = session;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AbstractParticipantGroup that = (AbstractParticipantGroup)o;

        if (_rowId != that._rowId) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _rowId;
    }

    public JSONObject toJSON()
    {
        return toJSON(true);
    }

    public JSONObject toJSON(boolean includeParticipants)
    {
        JSONObject json = new JSONObject();

        json.put("rowId", getRowId());
        json.put("label", getLabel());
        json.put("description", getDescription());
        json.put("categoryId", getCategoryId());
        json.put("categoryLabel", getCategoryLabel());
        if (includeParticipants)
            json.put("participantIds", getParticipantIds());
        json.put("createdBy", getCreatedBy());
        json.put("modifiedBy", getModifiedBy());
        json.put("filters", getFilters());
        json.put("session", isSession());

        return json;
    }

    public void copySpecialFields(AbstractParticipantGroup copy)
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
