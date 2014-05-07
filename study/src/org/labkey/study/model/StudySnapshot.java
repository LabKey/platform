/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.labkey.api.data.Container;
import org.labkey.api.util.GUID;
import org.labkey.study.writer.StudyExportContext;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 9/22/12
 * Time: 7:03 AM
 */

// Simple bean for persisting/loading from study.StudySnapshot
public class StudySnapshot
{
    private int _rowId;
    private GUID _source;
    private GUID _destination;
    private int _createdBy;
    private long _created;
    private int _modifiedBy;
    private long _modified;

    private boolean _refresh;  // Saved in settings as well, but dedicated refresh column allows quick filtering
    private SnapshotSettings _settings;

    @SuppressWarnings({"UnusedDeclaration"})
    // Invoked by data access layer via reflection
    public StudySnapshot()
    {
    }

    public StudySnapshot(StudyExportContext ctx, Container destination, boolean specimenRefresh)
    {
        _source = ctx.getContainer().getEntityId();
        _destination = destination.getEntityId();
        _refresh = specimenRefresh;
        _settings = new SnapshotSettings(ctx, specimenRefresh);
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public GUID getSource()
    {
        return _source;
    }

    public void setSource(GUID source)
    {
        _source = source;
    }

    public GUID getDestination()
    {
        return _destination;
    }

    public void setDestination(GUID destination)
    {
        _destination = destination;
    }

    public int getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(int createdBy)
    {
        _createdBy = createdBy;
    }

    public Date getCreated()
    {
        return new Date(_created);
    }

    public void setCreated(Date created)
    {
        _created = created.getTime();
    }

    public int getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(int modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public Date getModified()
    {
        return new Date(_modified);
    }

    public void setModified(Date modified)
    {
        _modified = modified.getTime();
    }

    public boolean isRefresh()
    {
        return _refresh;
    }

    public void setRefresh(boolean refresh)
    {
        _refresh = refresh;
    }

    public String getSettings()
    {
        ObjectMapper mapper = new ObjectMapper();
        try
        {
            return mapper.writeValueAsString(_settings);
        }
        catch (JsonProcessingException e)
        {
            // In previous usage of GSON, there was potential for unhandled exception from gson.toJson().
            // Jackson explicitly throws exceptions, but there's still nothing we can do about it if one happens here.
            throw new RuntimeException(e);
        }
    }

    public void setSettings(String settings)
    {
        ObjectMapper mapper = new ObjectMapper();
        try
        {
            _settings = mapper.readValue(settings, SnapshotSettings.class);
        }
        catch (IOException e)
        {
            // In previous usage of GSON, there was potential for unhandled exception from gson.fromJson().
            // Jackson explicitly throws exceptions, but there's still nothing we can do about it if one happens here.
            throw new RuntimeException(e);
        }
    }

    public SnapshotSettings getSnapshotSettings()
    {
        return _settings;
    }

    // Hold the actual snapshot settings that we care about... this is serialized to (and deserialized from) the
    // database via Jackson. To serialize/deserialize another property, create a new member (and optional getter)
    // and initialize it appropriately in the constructor below.
    public static class SnapshotSettings
    {
        private boolean specimenRefresh;
        private boolean useAlternateParticipantIds;
        private boolean removeProtectedColumns;
        private boolean shiftDates;
        private boolean maskClinic;
        private Set<Integer> visits;
        private List<String> participants;

        // Called via Jackson reflection
        @SuppressWarnings("UnusedDeclaration")
        private SnapshotSettings()
        {
        }

        private SnapshotSettings(StudyExportContext ctx, boolean refresh)
        {
            specimenRefresh = refresh;
            useAlternateParticipantIds = ctx.isAlternateIds();
            removeProtectedColumns = ctx.isRemoveProtected();
            shiftDates = ctx.isShiftDates();
            maskClinic = ctx.isMaskClinic();

            visits = ctx.getVisitIds();
            participants = ctx.getParticipants();
        }

        public boolean isSpecimenRefresh()
        {
            return specimenRefresh;
        }

        public boolean isUseAlternateParticipantIds()
        {
            return useAlternateParticipantIds;
        }

        public boolean isRemoveProtectedColumns()
        {
            return removeProtectedColumns;
        }

        public boolean isShiftDates()
        {
            return shiftDates;
        }

        public boolean isMaskClinic()
        {
            return maskClinic;
        }

        public Set<Integer> getVisits()
        {
            return visits;
        }

        public List<String> getParticipants()
        {
            return participants;
        }
    }
}
