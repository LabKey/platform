package org.labkey.study.model;

import com.google.gson.Gson;
import org.labkey.api.data.Container;
import org.labkey.api.util.GUID;
import org.labkey.study.writer.StudyExportContext;

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
        Gson gson = new Gson();
        return gson.toJson(_settings);
    }

    public void setSettings(String settings)
    {
        Gson gson = new Gson();
        _settings = gson.fromJson(settings, SnapshotSettings.class);
    }

    public SnapshotSettings getSnapshotSettings()
    {
        return _settings;
    }

    // Hold the actual snapshot settings that we care about... this is serialized to (and deserialized from) the
    // database via Gson. To serialize/deserialize another property, create a new member (and optional getter)
    // and initialize it appropriately in the constructor below.
    public static class SnapshotSettings
    {
        private boolean specimenRefresh;
        private boolean useAlternateIds;
        private boolean removeProtected;
        private boolean shiftDates;
        private Set<Integer> visits;
        private List<String> participants;

        private SnapshotSettings()
        {
        }

        private SnapshotSettings(StudyExportContext ctx, boolean refresh)
        {
            specimenRefresh = refresh;
            useAlternateIds = ctx.isAlternateIds();
            removeProtected = ctx.isRemoveProtected();
            shiftDates = ctx.isShiftDates();

            visits = ctx.getVisitIds();
            participants = ctx.getParticipants();
        }

        public boolean isSpecimenRefresh()
        {
            return specimenRefresh;
        }

        public boolean isUseAlternateIds()
        {
            return useAlternateIds;
        }

        public boolean isRemoveProtected()
        {
            return removeProtected;
        }

        public boolean isShiftDates()
        {
            return shiftDates;
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
