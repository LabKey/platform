/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.QueryService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.GUID;
import org.labkey.study.writer.StudyExportContext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
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
    private StudySnapshotType _type;

    private boolean _refresh;  // Saved in settings as well, but dedicated refresh column allows quick filtering
    private SnapshotSettings _settings;

    @SuppressWarnings({"UnusedDeclaration"})
    // Invoked by data access layer via reflection
    public StudySnapshot()
    {
    }

    public StudySnapshot(StudyExportContext ctx, Container destination, boolean specimenRefresh, ChildStudyDefinition def)
    {
        _type = def.getMode();
        _source = ctx.getContainer().getEntityId();
        _destination = destination.getEntityId();
        _refresh = specimenRefresh;
        _settings = new SnapshotSettings(ctx, specimenRefresh, def);
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

    public StudySnapshotType getType()
    {
        return _type;
    }

    public void setType(StudySnapshotType type)
    {
        _type = type;
    }

    // Hold the actual snapshot settings... this is serialized to (and deserialized from) the
    // database via Jackson. To serialize/deserialize another property, create a new member, a getter method,
    // and initialize it appropriately in the constructor below.
    // For properties that are lists or sets, null and missing indicates ALL and empty [] indicates none
    public static class SnapshotSettings
    {
        /* General Setup */
        private String description;
        /* Participants */
        private List<Integer> participantGroups = new ArrayList<>();
        private List<String> participants = new ArrayList<>();
        /* Datasets */
        private Set<Integer> datasets = new HashSet<>();
        private boolean datasetRefresh;
        private Integer datasetRefreshDelay;
        /* Timepoints */
        private Set<Integer> visits = new HashSet<>();
        /* Specimens */
        private Integer specimenRequestId;
        private boolean includeSpecimens;
        private boolean specimenRefresh;
        /* Study Objects */
        private List<String> studyObjects = new ArrayList<>();
        /* Lists */
        private List<String> lists = new ArrayList<>();
        /* Views */
        private List<String> views = new ArrayList<>();
        /* Reports */
        private List<String> reports = new ArrayList<>();
        /* Folder Objects */
        private List<String> folderObjects = new ArrayList<>();
        /* Publish Options */
        /* removeProtectedColumns is only here for legacy studies, no longer readable
           Jackson serializer gets cranky if it sees properties it doesn't know about */
        private boolean removeProtectedColumns;
        private boolean removePhiColumns;
        private PHI phiLevel;
        private boolean shiftDates;
        private boolean useAlternateParticipantIds;
        private boolean maskClinic;

        // Called via Jackson reflection
        @SuppressWarnings("UnusedDeclaration")
        private SnapshotSettings()
        {
        }

        private SnapshotSettings(StudyExportContext ctx, boolean refresh, ChildStudyDefinition def)
        {
            Study study = StudyService.get().getStudy(ctx.getContainer());

            loadGeneralSetup(def);
            loadParticipants(ctx, def);
            loadDatasets(study, ctx, def);
            loadTimepoints(study, ctx);
            loadSpecimens(def, refresh);
            loadStudyObjects(def);
            loadLists(ctx, def);
            loadViews(ctx, def);
            loadReports(ctx, def);
            loadFolderObjects(def);
            loadPublishOptions(ctx);
        }

        private void loadGeneralSetup(ChildStudyDefinition def)
        {
            if (def.getDescription() != null)
                description = def.getDescription();
        }

        private void loadParticipants(StudyExportContext ctx, ChildStudyDefinition def)
        {
            participants = ctx.getParticipants();
            if (def.isParticipantGroupsAll())
            {
                participantGroups = null; // indicates all selected
            }
            else if (def.getGroups() != null && def.getGroups().length > 0)
            {
                participantGroups = Arrays.asList(ArrayUtils.toObject(def.getGroups()));
            }
            else if (participants.isEmpty())
            {
                // "use all participants from source study" was selected
                participants = null;
            }
        }

        private void loadDatasets(Study study, StudyExportContext ctx, ChildStudyDefinition def)
        {
            if (ctx.getDatasetIds() != null && !ctx.getDatasetIds().isEmpty())
            {
                if (study != null && ctx.getDatasetIds().size() == study.getDatasets().size())
                    datasets = null; // indicates all selected
                else
                    datasets = ctx.getDatasetIds();
            }

            datasetRefresh = def.isUpdate();
            datasetRefreshDelay = def.getUpdateDelay() > 0 ? def.getUpdateDelay() : null;
        }

        private void loadTimepoints(Study study, StudyExportContext ctx)
        {
            // at least one visits is required, so it is either all or a subset
            if (ctx.getVisitIds() != null && !ctx.getVisitIds().isEmpty())
            {
                if (study != null && ctx.getVisitIds().size() == study.getVisits(Visit.Order.SEQUENCE_NUM).size())
                    visits = null; // indicates all selected
                else
                    visits = ctx.getVisitIds();
            }
            else
            {
                visits = null; // indicates all selected
            }
        }

        private void loadSpecimens(ChildStudyDefinition def, boolean refresh)
        {
            specimenRequestId = def.getRequestId();
            includeSpecimens = def.isIncludeSpecimens();
            specimenRefresh = refresh;
        }

        private void loadStudyObjects(ChildStudyDefinition def)
        {
            if (def.isStudyPropsAll())
                studyObjects = null; // indicates all selected
            else if (def.getStudyProps() != null && def.getStudyProps().length > 0)
                studyObjects = Arrays.asList(def.getStudyProps());

        }

        private void loadLists(StudyExportContext ctx, ChildStudyDefinition def)
        {
            if (def.getLists() != null && def.getLists().length > 0)
            {
                if (def.getLists().length == ListService.get().getLists(ctx.getContainer()).size())
                {
                    lists = null; // indicates all selected
                }
                else
                {
                    // write out list names instead of ids
                    for (Integer listId : def.getLists())
                    {
                        ListDefinition listDef = ListService.get().getList(ctx.getContainer(), listId);
                        if (listDef != null)
                            lists.add(listDef.getName());
                    }
                }
            }
        }

        private void loadViews(StudyExportContext ctx, ChildStudyDefinition def)
        {
            if (def.isViewsAll())
            {
                views = null; // indicates all selected
            }
            else if (def.getViews() != null && def.getViews().length > 0)
            {
                // write out view names instead of entityIds
                for (String entityid : def.getViews())
                {
                    try
                    {
                        String viewName = QueryService.get().getCustomViewNameFromEntityId(ctx.getContainer(), entityid);
                        if (viewName != null)
                            views.add(viewName);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        private void loadReports(StudyExportContext ctx, ChildStudyDefinition def)
        {
            if (def.isReportsAll())
            {
                reports = null; // indicates all selected
            }
            else if (def.getReports() != null && def.getReports().length > 0)
            {
                // write out report names instead of entityIds
                for (String entityid : def.getReports())
                {
                    Report report = ReportService.get().getReportByEntityId(ctx.getContainer(), entityid);
                    if (report != null)
                        reports.add(report.getDescriptor().getReportName());
                }
            }
        }

        private void loadFolderObjects(ChildStudyDefinition def)
        {
            if (def.isFolderPropsAll())
                folderObjects = null; // indicates all selected
            else if (def.getFolderProps() != null && def.getFolderProps().length > 0)
                folderObjects = Arrays.asList(def.getFolderProps());
        }

        private void loadPublishOptions(StudyExportContext ctx)
        {
            phiLevel = ctx.getPhiLevel();
            shiftDates = ctx.isShiftDates();
            useAlternateParticipantIds = ctx.isAlternateIds();
            maskClinic = ctx.isMaskClinic();
        }

        public boolean isSpecimenRefresh()
        {
            return specimenRefresh;
        }

        public boolean isUseAlternateParticipantIds()
        {
            return useAlternateParticipantIds;
        }

        /* removeProtectedColumns is only here for legacy studies, no longer readable
           Jackson serializer gets cranky if it sees properties it doesn't know about */
        public boolean isRemoveProtectedColumns()
        {
            return false;
        }

        public boolean isRemovePhiColumns()
        {
            return false;
        }

        public PHI getPhiLevel()
        {
            return phiLevel;
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

        public Set<Integer> getDatasets()
        {
            return datasets;
        }

        public String getDescription()
        {
            return description;
        }

        public List<Integer> getParticipantGroups()
        {
            return participantGroups;
        }

        public boolean isDatasetRefresh()
        {
            return datasetRefresh;
        }

        public Integer getDatasetRefreshDelay()
        {
            return datasetRefreshDelay;
        }

        public boolean isIncludeSpecimens()
        {
            return includeSpecimens;
        }

        public List<String> getStudyObjects()
        {
            return studyObjects;
        }

        public List<String> getFolderObjects()
        {
            return folderObjects;
        }

        public List<String> getLists()
        {
            return lists;
        }

        public List<String> getViews()
        {
            return views;
        }

        public List<String> getReports()
        {
            return reports;
        }

        public Integer getSpecimenRequestId()
        {
            return specimenRequestId;
        }
    }
}
