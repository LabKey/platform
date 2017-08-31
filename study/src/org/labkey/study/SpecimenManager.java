/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.study;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.comparators.ComparableComparator;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.Selector.ForEachBlock;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyCachable;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.specimen.SpecimenController;
import org.labkey.study.importer.RequestabilityManager;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.importer.SpecimenImporter.VialSpecimenRollup;
import org.labkey.study.model.AdditiveType;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.DerivativeType;
import org.labkey.study.model.ExtendedSpecimenRequestView;
import org.labkey.study.model.LocationImpl;
import org.labkey.study.model.PrimaryType;
import org.labkey.study.model.SpecimenComment;
import org.labkey.study.model.SpecimenEvent;
import org.labkey.study.model.SpecimenRequest;
import org.labkey.study.model.SpecimenRequestActor;
import org.labkey.study.model.SpecimenRequestEvent;
import org.labkey.study.model.SpecimenRequestRequirement;
import org.labkey.study.model.SpecimenRequestStatus;
import org.labkey.study.model.SpecimenTypeSummary;
import org.labkey.study.model.SpecimenTypeSummaryRow;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Vial;
import org.labkey.study.model.VisitImpl;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.requirements.RequirementProvider;
import org.labkey.study.requirements.SpecimenRequestRequirementProvider;
import org.labkey.study.security.permissions.ManageRequestsPermission;
import org.labkey.study.security.permissions.RequestSpecimensPermission;
import org.labkey.study.specimen.SpecimenCommentAuditProvider;
import org.labkey.study.specimen.report.SpecimenCountSummary;
import org.labkey.study.specimen.settings.DisplaySettings;
import org.labkey.study.specimen.settings.RepositorySettings;
import org.labkey.study.specimen.settings.RequestNotificationSettings;
import org.labkey.study.specimen.settings.StatusSettings;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

public class SpecimenManager implements ContainerManager.ContainerListener
{
    private final static SpecimenManager _instance = new SpecimenManager();

    public static final String STUDY_SPECIMENS_SAMPLE_SET_NAME = "Study Specimens";

    private final QueryHelper<SpecimenRequestEvent> _requestEventHelper;
//    private final QueryHelper<AdditiveType> _additiveHelper;
//    private final QueryHelper<DerivativeType> _derivativeHelper;
//    private final QueryHelper<PrimaryType> _primaryTypeHelper;
    private final QueryHelper<SpecimenRequest> _requestHelper;
    private final QueryHelper<SpecimenRequestStatus> _requestStatusHelper;
    private final RequirementProvider<SpecimenRequestRequirement, SpecimenRequestActor> _requirementProvider =
            new SpecimenRequestRequirementProvider();
    private final Comparator<SpecimenEvent> _specimenEventDateComparator = new SpecimenEventDateComparator();

    private SpecimenManager()
    {
/*        _primaryTypeHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoPrimaryType();
            }
        }, PrimaryType.class);
        _derivativeHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoDerivativeType();
            }
        }, DerivativeType.class);
        _additiveHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoAdditiveType();
            }
        }, AdditiveType.class);
*/
        _requestEventHelper = new QueryHelper<>(() -> StudySchema.getInstance().getTableInfoSampleRequestEvent(), SpecimenRequestEvent.class);
        _requestHelper = new QueryHelper<>(() -> StudySchema.getInstance().getTableInfoSampleRequest(), SpecimenRequest.class);
        _requestStatusHelper = new QueryHelper<>(() -> StudySchema.getInstance().getTableInfoSampleRequestStatus(), SpecimenRequestStatus.class);

        initGroupedValueAllowedColumnMap();

        ContainerManager.addContainerListener(this);
    }

    public static SpecimenManager getInstance()
    {
        return _instance;
    }


    public boolean isSpecimensEmpty(Container container, User user)
    {
        TableSelector selector = getSpecimensSelector(container, user, null);
        return !selector.exists();
    }
 

    public List<Vial> getVials(Container container, User user, String participantId, Double visit)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        filter.addCondition(FieldKey.fromParts("VisitValue"), visit);
        return getVials(container, user, filter);
    }


    public RequirementProvider<SpecimenRequestRequirement, SpecimenRequestActor> getRequirementsProvider()
    {
        return _requirementProvider;
    }

    @Override
    public void containerCreated(Container c, User user)
    {
        clearCaches(c);
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        clearCaches(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        clearCaches(c);
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        clearCaches((Container)evt.getSource());
    }

    public Vial getVial(Container container, User user, long rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        List<Vial> vials = getVials(container, user, filter);
        if (vials.isEmpty())
            return null;
        return vials.get(0);
    }

    /** Looks for any specimens that have the given id as a globalUniqueId  */
    public Vial getVial(Container container, User user, String globalUniqueId)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?)", new Object[] { globalUniqueId }));
        List<Vial> matches = getVials(container, user, filter);
        if (matches == null || matches.isEmpty())
            return null;
        if (matches.size() > 1)
        {
            // we apparently have two specimens with IDs that differ only in case; do a case sensitive check
            // here to find the right one:
            for (Vial vial : matches)
            {
                if (vial.getGlobalUniqueId().equals(globalUniqueId))
                    return vial;
            }
            throw new IllegalStateException("Expected at least one vial to exactly match the specified global unique ID: " + globalUniqueId);
        }
        else
            return matches.get(0);
    }

    public List<Vial> getVials(Container container, User user, String participantId, Date date)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        Calendar endCal = DateUtil.newCalendar(date.getTime());
        endCal.add(Calendar.DATE, 1);
        filter.addClause(new SimpleFilter.SQLClause("DrawTimestamp >= ? AND DrawTimestamp < ?", new Object[] {date, endCal.getTime()}));
        return getVials(container, user, filter);
    }

    public List<SpecimenEvent> getSpecimenEvents(@NotNull Vial vial)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("VialId"), vial.getRowId());
        return getSpecimenEvents(vial.getContainer(), filter);
    }

    public List<SpecimenEvent> getSpecimenEvents(List<Vial> vials, boolean includeObsolete)
    {
        if (vials == null || vials.size() == 0)
            return Collections.emptyList();
        Collection<Long> vialIds = new HashSet<>();
        Container container = null;
        for (Vial vial : vials)
        {
            vialIds.add(vial.getRowId());
            if (container == null)
                container = vial.getContainer();
            else if (!container.equals(vial.getContainer()))
                throw new IllegalArgumentException("All specimens must be from the same container");
        }
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromString("VialId"), vialIds);
        if (!includeObsolete)
            filter.addCondition(FieldKey.fromString("Obsolete"), false);
        return getSpecimenEvents(container, filter);
    }

    private List<SpecimenEvent> getSpecimenEvents(final Container container, Filter filter)
    {
        final List<SpecimenEvent> specimenEvents = new ArrayList<>();
        TableInfo tableInfo = StudySchema.getInstance().getTableInfoSpecimenEvent(container);

        new TableSelector(tableInfo, filter, null).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                specimenEvents.add(new SpecimenEvent(container, map));
            }
        });

        return specimenEvents;
    }

    private static class SpecimenEventDateComparator implements Comparator<SpecimenEvent>
    {
        private Date getAnyDate(SpecimenEvent event)
        {
            if (event.getLabReceiptDate() != null)
                return event.getLabReceiptDate();
            else
            {
                Date storageDate = event.getStorageDate();
                if (storageDate != null)
                    return storageDate;
                else
                    return event.getShipDate();
            }
        }

        private int getTieBreakValue(SpecimenEvent event)
        {
            // our events have the same dates; in this case, we have to consider
            // the date type; a shipping date always comes after a storage date,
            // and a storage date always comes after a receipt date.
            if (event.getLabReceiptDate() != null)
                return 1;
            else if (event.getStorageDate() != null)
                return 2;
            else if (event.getShipDate() != null)
                return 3;
            throw new IllegalStateException("Can only tiebreak events with at least one date present.");
        }

        public int compare(SpecimenEvent event1, SpecimenEvent event2)
        {
            // Obsolete always < non-obsolete
            if (event1.getObsolete() != event2.getObsolete())
                if (event1.getObsolete())
                    return -1;
                else
                    return 1;

            // we use any date in the event, since we assume that no two events can have
            // overlapping date ranges:
            Date date1 = getAnyDate(event1);
            Date date2 = getAnyDate(event2);
            if (date1 == null && date2 == null)
                return compareExternalIds(event1, event2);
            if (date1 == null)
                return -1;
            if (date2 == null)
                return 1;
            Long ms1 = date1.getTime();
            Long ms2 = date2.getTime();
            int comp = ms1.compareTo(ms2);
            if (comp != 0)
                return comp;
            comp = getTieBreakValue(event2) - getTieBreakValue(event1);
            if (comp != 0)
                return comp;
            return compareExternalIds(event1, event2);
        }
    }

    private static int compareExternalIds(SpecimenEvent event1, SpecimenEvent event2)
    {
        long compExternalIds = event1.getExternalId() - event2.getExternalId();
        if (compExternalIds < 0)
            return -1;
        if (compExternalIds > 0)
            return 1;
        return 0;
    }

    public List<SpecimenEvent> getDateOrderedEventList(Vial vial)
    {
        List<SpecimenEvent> eventList = new ArrayList<>();
        List<SpecimenEvent> events = getSpecimenEvents(vial);
        if (events == null || events.isEmpty())
            return eventList;
        eventList.addAll(events);
        eventList.sort(getSpecimenEventDateComparator());
        return eventList;
    }

    public Map<Vial, List<SpecimenEvent>> getDateOrderedEventLists(List<Vial> vials, boolean includeObsolete)
    {
        List<SpecimenEvent> allEvents = getSpecimenEvents(vials, includeObsolete);
        Map<Long, List<SpecimenEvent>> vialIdToEvents = new HashMap<>();

        for (SpecimenEvent event : allEvents)
        {
            List<SpecimenEvent> vialEvents = vialIdToEvents.computeIfAbsent(event.getVialId(), k -> new ArrayList<>());
            vialEvents.add(event);
        }

        Map<Vial, List<SpecimenEvent>> results = new HashMap<>();

        for (Vial vial : vials)
        {
            List<SpecimenEvent> events = vialIdToEvents.get(vial.getRowId());
            if (events != null && events.size() > 0)
                events.sort(getSpecimenEventDateComparator());
            else
                events = Collections.emptyList();
            results.put(vial, events);
        }

        return results;
    }


    public Comparator<SpecimenEvent> getSpecimenEventDateComparator()
    {
        return _specimenEventDateComparator;
    }


    public LocationImpl getCurrentLocation(Vial vial)
    {
        Integer locationId = getCurrentLocationId(vial);
        if (locationId != null)
            return StudyManager.getInstance().getLocation(vial.getContainer(), locationId);
        return null;
    }

    public Integer getCurrentLocationId(Vial vial)
    {
        List<SpecimenEvent> events = getDateOrderedEventList(vial);
        return getCurrentLocationId(events);
    }

    public Integer getCurrentLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent lastEvent = dateOrderedEvents.get(dateOrderedEvents.size() - 1);

            if (lastEvent.getShipDate() == null &&
                    (lastEvent.getShipBatchNumber() == null || lastEvent.getShipBatchNumber() == 0) &&
                    (lastEvent.getShipFlag() == null || lastEvent.getShipFlag() == 0))
            {
                return lastEvent.getLabId();
            }
        }
        return null;
    }

    private boolean skipAsProcessingLocation(SpecimenEvent event)
    {
        boolean allNullDates = event.getLabReceiptDate() == null && event.getStorageDate() == null && event.getShipDate() == null;
        //
        return allNullDates && !safeComp(event.getLabId(), event.getOriginatingLocationId());
    }

    public SpecimenEvent getFirstEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent firstEvent = dateOrderedEvents.get(0);
            // walk backwards through the events until we find an event with at least one date field filled in that isn't
            // the first event.  Leaving all specimen event dates blank shouldn't make an event the processing location.
            for (int i = 1; i < dateOrderedEvents.size() - 1 && skipAsProcessingLocation(firstEvent); i++)
                firstEvent = dateOrderedEvents.get(i);
            return firstEvent;
        }
        return null;
    }

    public SpecimenEvent getLastEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (dateOrderedEvents.isEmpty())
            return null;
        return dateOrderedEvents.get(dateOrderedEvents.size() - 1);
    }

    public Integer getProcessingLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getLabId() : null;
    }

    public String getFirstProcessedByInitials(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getProcessedByInitials() : null;
    }

    public LocationImpl getOriginatingLocation(Vial vial)
    {
        if (vial.getOriginatingLocationId() != null)
        {
            LocationImpl location = StudyManager.getInstance().getLocation(vial.getContainer(), vial.getOriginatingLocationId());
            if (location != null)
                return location;
        }

        List<SpecimenEvent> events = getDateOrderedEventList(vial);
        Integer firstLabId = getProcessingLocationId(events);
        if (firstLabId != null)
            return StudyManager.getInstance().getLocation(vial.getContainer(), firstLabId);
        else
            return null;
    }

    public long getMaxExternalId(Container container)
    {
        TableInfo tableInfo = StudySchema.getInstance().getTableInfoSpecimenEvent(container);
        SQLFragment sql = new SQLFragment("SELECT MAX(ExternalId) FROM ");
        sql.append(tableInfo.getSelectName());
        return new SqlSelector(tableInfo.getSchema(), sql).getArrayList(Long.class).get(0);
    }

    public List<SpecimenRequest> getRequests(Container c, User user)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Hidden"), Boolean.FALSE);
        if (user != null)
            filter.addCondition(FieldKey.fromParts("CreatedBy"), user.getUserId());
        return _requestHelper.get(c, filter, "-Created");
    }

    public SpecimenRequest getRequest(Container c, int rowId)
    {
        return _requestHelper.get(c, rowId);
    }

    public SpecimenRequest createRequest(User user, SpecimenRequest request, boolean createEvent) throws SQLException, AttachmentService.DuplicateFilenameException
    {
        request = _requestHelper.create(user, request);
        if (createEvent)
            createRequestEvent(user, request, RequestEventType.REQUEST_CREATED, request.getRequestDescription(), null);
        return request;
    }

    public void updateRequest(User user, SpecimenRequest request) throws SQLException, RequestabilityManager.InvalidRuleException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            _requestHelper.update(user, request);

            // update specimen states
            List<Vial> vials = request.getVials();
            if (vials != null && vials.size() > 0)
            {
                SpecimenRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
                updateSpecimenStatus(vials, user, status.isSpecimensLocked());
            }
            transaction.commit();
        }
    }

    /**
     * Update the lockedInRequest and available field states for the set of specimens.
     */
    private void updateSpecimenStatus(List<Vial> vials, User user, boolean lockedInRequest) throws SQLException, RequestabilityManager.InvalidRuleException
    {
        for (Vial vial : vials)
        {
            vial.setLockedInRequest(lockedInRequest);
            Table.update(user, StudySchema.getInstance().getTableInfoVial(vial.getContainer()), vial.getRowMap(), vial.getRowId());
        }
        updateRequestabilityAndCounts(vials, user);
        if (vials.size() > 0)
            clearCaches(getContainer(vials));
    }

    private Container getContainer(List<Vial> vials)
    {
        Container container = vials.get(0).getContainer();
        if (AppProps.getInstance().isDevMode())
        {
            for (int i = 1; i < vials.size(); i++)
            {
                if (!container.equals(vials.get(i).getContainer()))
                    throw new IllegalStateException("All specimens must be from the same container");
            }
        }
        return container;
    }

    private static final String UPDATE_SPECIMEN_SETS =
            " SET\n" +
                    "    TotalVolume = VialCounts.TotalVolume,\n" +
                    "    AvailableVolume = VialCounts.AvailableVolume,\n" +
                    "    VialCount = VialCounts.VialCount,\n" +
                    "    LockedInRequestCount = VialCounts.LockedInRequestCount,\n" +
                    "    AtRepositoryCount = VialCounts.AtRepositoryCount,\n" +
                    "    AvailableCount = VialCounts.AvailableCount,\n" +
                    "    ExpectedAvailableCount = VialCounts.ExpectedAvailableCount";

    private static final String UPDATE_SPECIMEN_SELECTS =
                    "\nFROM (\n" +
                    "\tSELECT SpecimenId,\n" +
                    "\t\tSUM(Volume) AS TotalVolume,\n" +
                    "\t\tSUM(CASE Available WHEN ? THEN Volume ELSE 0 END) AS AvailableVolume,\n" +
                    "\t\tCOUNT(GlobalUniqueId) AS VialCount,\n" +
                    "\t\tSUM(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) AS LockedInRequestCount,\n" +
                    "\t\tSUM(CASE AtRepository WHEN ? THEN 1 ELSE 0 END) AS AtRepositoryCount,\n" +
                    "\t\tSUM(CASE Available WHEN ? THEN 1 ELSE 0 END) AS AvailableCount,\n" +
                    "\t\t(COUNT(GlobalUniqueId) - SUM(\n" +
                    "\t\tCASE\n" +
                    "\t\t\t(CASE LockedInRequest WHEN ? THEN 1 ELSE 0 END) -- Null is considered false for LockedInRequest\n" +
                    "\t\t\t| (CASE Requestable WHEN ? THEN 1 ELSE 0 END)-- Null is considered true for Requestable\n" +
                    "\t\t\tWHEN 1 THEN 1 ELSE 0 END)\n" +
                    "\t\t) AS ExpectedAvailableCount";

    //                "\tFROM ";

    private void updateVialCounts(Container container, User user, List<Vial> vials)
    {
        TableInfo tableInfoSpecimen = StudySchema.getInstance().getTableInfoSpecimen(container);
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);

        String tableInfoSpecimenSelectName = tableInfoSpecimen.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();
        SpecimenImporter.RollupMap<VialSpecimenRollup> matchedRollups = SpecimenImporter.getVialToSpecimenRollups(container, user);

        SQLFragment updateSql = new SQLFragment();
        updateSql.append("UPDATE ").append(tableInfoSpecimenSelectName).append(UPDATE_SPECIMEN_SETS);
        for (List<SpecimenImporter.RollupInstance<VialSpecimenRollup>> rollupList : matchedRollups.values())
            for (Pair<String, VialSpecimenRollup> rollupItem : rollupList)
            {
                ColumnInfo column = tableInfoSpecimen.getColumn(rollupItem.first);
                if (null == column)
                    throw new IllegalStateException("Expected Specimen table column to exist.");
                String colSelectName = column.getSelectName();
                updateSql.append(",\n    ").append(colSelectName).append(" = VialCounts.").append(colSelectName);
            }

        updateSql.append(UPDATE_SPECIMEN_SELECTS);
        updateSql.add(Boolean.TRUE); // AvailableVolume
        updateSql.add(Boolean.TRUE); // LockedInRequestCount
        updateSql.add(Boolean.TRUE); // AtRepositoryCount
        updateSql.add(Boolean.TRUE); // AvailableCount
        updateSql.add(Boolean.TRUE); // LockedInRequest case of ExpectedAvailableCount
        updateSql.add(Boolean.FALSE); // Requestable case of ExpectedAvailableCount

        for (Map.Entry<String, List<SpecimenImporter.RollupInstance<VialSpecimenRollup>>> entry : matchedRollups.entrySet())
        {
            ColumnInfo vialColumn = tableInfoVial.getColumn(entry.getKey());
            if (null == vialColumn)
                throw new IllegalStateException("Expected Vial table column to exist.");
            String fromName = vialColumn.getSelectName();
            for (SpecimenImporter.RollupInstance<VialSpecimenRollup> rollupItem : entry.getValue())
            {
                VialSpecimenRollup rollup = rollupItem.second;
                ColumnInfo column = tableInfoSpecimen.getColumn(rollupItem.first);
                if (null == column)
                    throw new IllegalStateException("Expected Specimen table column to exist.");
                String toName = column.getSelectName();

                updateSql.append(",\n\t\t").append(rollup.getRollupSql(fromName, toName));
            }
        }

        updateSql.append("\tFROM ").append(tableInfoVialSelectName).append("\n");

        if (vials != null && vials.size() > 0)
        {
            Set<Long> specimenIds = new HashSet<>();
            for (Vial vial : vials)
                specimenIds.add(vial.getSpecimenId());

            updateSql.append("WHERE ")
                    .append(tableInfoVial.getColumn("SpecimenId").getValueSql(tableInfoVialSelectName))
                    .append(" IN (");
            String sep = "";
            for (Long id : specimenIds)
            {
                updateSql.append(sep).append("?");
                updateSql.add(id);
                sep = ", ";
            }
            updateSql.append(")\n");
        }

        updateSql.append("\tGROUP BY SpecimenId\n) VialCounts\nWHERE ")
                .append(tableInfoSpecimen.getColumn("RowId").getValueSql(tableInfoSpecimenSelectName))
                .append("= VialCounts.SpecimenId");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateSql);
    }

    public void updateVialCounts(Container container, User user)
    {
        updateVialCounts(container, user, null);
    }

    private void updateRequestabilityAndCounts(List<Vial> vials, User user) throws RequestabilityManager.InvalidRuleException
    {
        if (vials.size() == 0)
            return;
        Container container = getContainer(vials);

        // update requestable flags before updating counts, since available count could change:
        for (int start = 0; start < vials.size(); start += 1000)
        {
            List<Vial> subset = vials.subList(start, start + Math.min(1000, vials.size() - start));
            RequestabilityManager.getInstance().updateRequestability(container, user, subset);
        }

        for (int start = 0; start < vials.size(); start += 1000)
        {
            List<Vial> subset = vials.subList(start, start + Math.min(1000, vials.size() - start));
            updateVialCounts(container, user, subset);
        }
    }

    public SpecimenRequestRequirement[] getRequestRequirements(SpecimenRequest request)
    {
        if (request == null)
            return new SpecimenRequestRequirement[0];
        return request.getRequirements();
    }

    public void deleteRequestRequirement(User user, SpecimenRequestRequirement requirement) throws AttachmentService.DuplicateFilenameException
    {
        deleteRequestRequirement(user, requirement, true);
    }

    public void deleteRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent) throws AttachmentService.DuplicateFilenameException
    {
        if (createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_REMOVED, requirement.getRequirementSummary(), null);
        requirement.delete();
    }

    public void createRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent) throws AttachmentService.DuplicateFilenameException
    {
        createRequestRequirement(user, requirement, createEvent, false);
    }

    public void createRequestRequirement(User user, SpecimenRequestRequirement requirement, boolean createEvent, boolean force) throws AttachmentService.DuplicateFilenameException
    {
        SpecimenRequest request = getRequest(requirement.getContainer(), requirement.getRequestId());
        SpecimenRequestRequirement newRequirement = _requirementProvider.createRequirement(user, request, requirement, force);
        if (newRequirement != null && createEvent)
            createRequestEvent(user, requirement, RequestEventType.REQUIREMENT_ADDED, requirement.getRequirementSummary(), null);
    }

    public void updateRequestRequirement(User user, SpecimenRequestRequirement requirement)
    {
        requirement.update(user);
    }

    public boolean isInFinalState(SpecimenRequest request)
    {
        return getRequestStatus(request.getContainer(), request.getStatusId()).isFinalState();
    }

    public SpecimenRequestStatus getRequestStatus(Container c, int rowId)
    {
        return _requestStatusHelper.get(c, rowId);
    }

    @Nullable
    public AdditiveType getAdditiveType(Container c, int rowId)
    {
//        return _additiveHelper.get(c, rowId);
        List<AdditiveType> additiveTypes = getAdditiveTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
        if (!additiveTypes.isEmpty())
            return additiveTypes.get(0);
        return null;
    }

/*    public List<AdditiveType> getAdditiveTypes(Container c)
    {
        return _additiveHelper.get(c, "ExternalId");
    }
*/

    private List<AdditiveType> getAdditiveTypes(final Container container, @Nullable SimpleFilter filter)
    {
        final List<AdditiveType> additiveTypes = new ArrayList<>();
        new TableSelector(StudySchema.getInstance().getTableInfoSpecimenAdditive(container), filter, null).
            forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    additiveTypes.add(new AdditiveType(container, map));
                }
            });
        return additiveTypes;
    }

    public DerivativeType getDerivativeType(Container c, int rowId)
    {
//        return _derivativeHelper.get(c, rowId);
        List<DerivativeType> derivativeTypes = getDerivativeTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
        if (!derivativeTypes.isEmpty())
            return derivativeTypes.get(0);
        return null;
    }

/*    public List<DerivativeType> getDerivativeTypes(Container c)
    {
        return _derivativeHelper.get(c, "ExternalId");
    }
*/

    private List<DerivativeType> getDerivativeTypes(final Container container, @Nullable SimpleFilter filter)
    {
        final List<DerivativeType> derivativeTypes = new ArrayList<>();
        new TableSelector(StudySchema.getInstance().getTableInfoSpecimenDerivative(container), filter, null).
            forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    derivativeTypes.add(new DerivativeType(container, map));
                }
            });
        return derivativeTypes;
    }

    public PrimaryType getPrimaryType(Container c, int rowId)
    {
//        return _primaryTypeHelper.get(c, rowId);
        List<PrimaryType> primaryTypes = getPrimaryTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId), null);
        if (!primaryTypes.isEmpty())
            return primaryTypes.get(0);
        return null;
    }

    public List<PrimaryType> getPrimaryTypes(Container c)
    {
//        return _primaryTypeHelper.get(c, "ExternalId");
        return getPrimaryTypes(c, null, new Sort("ExternalId"));
    }

    private List<PrimaryType> getPrimaryTypes(final Container container, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        final List<PrimaryType> primaryTypes = new ArrayList<>();
        new TableSelector(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(container), filter, sort).
            forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
            {
                @Override
                public void exec(Map<String, Object> map) throws SQLException
                {
                    primaryTypes.add(new PrimaryType(container, map));
                }
            });
        return primaryTypes;
    }

    public List<SpecimenRequestStatus> getRequestStatuses(Container c, User user)
    {
        List<SpecimenRequestStatus> statuses = _requestStatusHelper.get(c, "SortOrder");
        // if the 'not-yet-submitted' status doesn't exist, create it here, with sort order -1,
        // so it's always first.
        if (statuses == null || statuses.isEmpty() || statuses.get(0).getSortOrder() != -1)
        {
            SpecimenRequestStatus notYetSubmittedStatus = new SpecimenRequestStatus();
            notYetSubmittedStatus.setContainer(c);
            notYetSubmittedStatus.setFinalState(false);
            notYetSubmittedStatus.setSpecimensLocked(true);
            notYetSubmittedStatus.setLabel("Not Yet Submitted");
            notYetSubmittedStatus.setSortOrder(-1);
            Table.insert(user, _requestStatusHelper.getTableInfo(), notYetSubmittedStatus);
            statuses = _requestStatusHelper.get(c, "SortOrder");
        }
        return statuses;
    }

    public SpecimenRequestStatus getRequestShoppingCartStatus(Container c, User user)
    {
        List<SpecimenRequestStatus> statuses = getRequestStatuses(c, user);
        if (statuses.get(0).getSortOrder() != -1)
            throw new IllegalStateException("Shopping cart status should be created automatically.");
        return statuses.get(0);
    }

    public SpecimenRequestStatus getInitialRequestStatus(Container c, User user, boolean nonCart)
    {
        List<SpecimenRequestStatus> statuses = getRequestStatuses(c, user);
        if (!nonCart && isSpecimenShoppingCartEnabled(c))
            return statuses.get(0);
        else
            return statuses.get(1);
    }

    public boolean hasEditRequestPermissions(User user, SpecimenRequest request)
    {
        if (request == null)
            return false;
        Container container = request.getContainer();
        if (!container.hasPermission(user, RequestSpecimensPermission.class))
            return false;
        if (container.hasPermission(user, ManageRequestsPermission.class))
            return true;

        if (isSpecimenShoppingCartEnabled(container))
        {
            SpecimenRequestStatus cartStatus = getRequestShoppingCartStatus(container, user);
            if (cartStatus.getRowId() == request.getStatusId() && request.getCreatedBy() == user.getUserId())
                return true;
        }
        return false;
    }

    public Set<Integer> getRequestStatusIdsInUse(Container c)
    {
        List<SpecimenRequest> requests = _requestHelper.get(c);
        Set<Integer> uniqueStatuses = new HashSet<>();
        for (SpecimenRequest request : requests)
            uniqueStatuses.add(request.getStatusId());
        return uniqueStatuses;
    }

    public void createRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.create(user, status);
    }

    public void updateRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.update(user, status);
    }

    public void deleteRequestStatus(User user, SpecimenRequestStatus status)
    {
        _requestStatusHelper.delete(status);
    }

    public List<SpecimenRequestEvent> getRequestEvents(Container c)
    {
        return _requestEventHelper.get(c);
    }

    public SpecimenRequestEvent getRequestEvent(Container c, int rowId)
    {
        return _requestEventHelper.get(c, rowId);
    }

    public enum RequestEventType
    {
        REQUEST_CREATED("Request Created"),
        REQUEST_STATUS_CHANGED("Request Status Changed"),
        REQUIREMENT_ADDED("Requirement Created"),
        REQUIREMENT_REMOVED("Requirement Removed"),
        REQUIREMENT_UPDATED("Requirement Updated"),
        REQUEST_UPDATED("Request Updated"),
        SPECIMEN_ADDED("Specimen Added"),
        SPECIMEN_REMOVED("Specimen Removed"),
        SPECIMEN_LIST_GENERATED("Specimen List Generated"),
        COMMENT_ADDED("Comment/Attachment(s) Added"),
        NOTIFICATION_SENT("Notification Sent");

        private String _displayText;

        RequestEventType(String displayText)
        {
            _displayText = displayText;
        }

        public String getDisplayText()
        {
            return _displayText;
        }
    }

    public SpecimenRequestEvent createRequestEvent(User user, SpecimenRequestRequirement requirement, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, requirement.getContainer(), requirement.getRequestId(), requirement.getRowId(), type, comments, attachments);
    }

    public SpecimenRequestEvent createRequestEvent(User user, SpecimenRequest request, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        return createRequestEvent(user, request.getContainer(), request.getRowId(), -1, type, comments, attachments);
    }

    private SpecimenRequestEvent createRequestEvent(User user, Container container, int requestId, int requirementId, RequestEventType type, String comments, List<AttachmentFile> attachments) throws AttachmentService.DuplicateFilenameException
    {
        SpecimenRequestEvent event = new SpecimenRequestEvent();
        event.setEntryType(type.getDisplayText());
        event.setComments(comments);
        event.setRequestId(requestId);
        event.setCreated(new Date(System.currentTimeMillis()));
        if (requirementId >= 0)
            event.setRequirementId(requirementId);
        event.setContainer(container);
        event.setEntityId(GUID.makeGUID());
        event = createRequestEvent(user, event);
        try
        {
            AttachmentService.get().addAttachments(event, attachments, user);
        }
        catch (AttachmentService.DuplicateFilenameException e)
        {
            // UI should (minimally) catch and display these errors nicely
            throw e;
        }
        catch (IOException e)
        {
            // this is unexpected, and indicative of a larger system problem; we'll convert to a runtime
            // exception, rather than requiring all event loggers to handle this unlikely scenario:
            throw new RuntimeException(e);
        }
        return event;
    }

    private void deleteRequestEvents(User user, SpecimenRequest request)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RequestId"), request.getRowId());
        List<SpecimenRequestEvent> events = _requestEventHelper.get(request.getContainer(), filter);
        for (SpecimenRequestEvent event : events)
        {
            AttachmentService.get().deleteAttachments(event);
            _requestEventHelper.delete(event);
        }
    }

    private SpecimenRequestEvent createRequestEvent(User user, SpecimenRequestEvent event)
    {
        return _requestEventHelper.create(user, event);
    }

    public List<Vial> getRequestVials(SpecimenRequest request)
    {
        final Container container = request.getContainer();
        StudySchema studySchema = StudySchema.getInstance();
        TableInfo tableInfoSpecimen = studySchema.getTableInfoSpecimen(container);
        TableInfo tableInfoVial = studySchema.getTableInfoVial(container);
        SQLFragment sql = new SQLFragment("SELECT Specimens.*, Vial.*, ? As Container FROM ");
        sql.add(container);
        sql.append(studySchema.getSchema().getTable("SampleRequest").getFromSQL("request"))
                .append(", ").append(studySchema.getSchema().getTable("SampleRequestSpecimen").getFromSQL("map"))
                .append(", ").append(tableInfoSpecimen.getFromSQL("Specimens"))
                .append(", ").append(tableInfoVial.getFromSQL("Vial"))
                .append("\nWHERE request.RowId = map.SampleRequestId AND Vial.GlobalUniqueId = map.SpecimenGlobalUniqueId\n")
                .append("AND Vial.SpecimenId = Specimens.RowId ")
                .append("AND request.Container = map.Container AND map.Container = ? AND request.RowId = ?");
        sql.add(container);
        sql.add(request.getRowId());

        // TODO: LinkedList?
        final List<Vial> vials = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEachMap(new ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> map) throws SQLException
            {
                vials.add(new Vial(container, map));
            }
        });

        return vials;
    }

    public RepositorySettings getRepositorySettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings");
        if (settingsMap.isEmpty())
        {
            RepositorySettings defaults = RepositorySettings.getDefaultSettings(container);
            saveRepositorySettings(container, defaults);
            return defaults;
        }
        else
            return new RepositorySettings(container, settingsMap);
    }

    public void saveRepositorySettings(Container container, RepositorySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRepositorySettings", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
        clearGroupedValuesForColumn(container);     // May have changed groupings
    }


    public RequestNotificationSettings getRequestNotificationSettings(Container container)
    {
        Map<String,String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications");
        if (settingsMap.get("ReplyTo") == null)
        {
            RequestNotificationSettings defaults = RequestNotificationSettings.getDefaultSettings(container);
            saveRequestNotificationSettings(container, defaults);
            return defaults;
        }
        else
            return new RequestNotificationSettings(settingsMap);
    }

    public void saveRequestNotificationSettings(Container container, RequestNotificationSettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestNotifications", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }


    public DisplaySettings getDisplaySettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay");
        if (settingsMap.isEmpty())
        {
            DisplaySettings defaults = DisplaySettings.getDefaultSettings();
            saveDisplaySettings(container, defaults);
            return defaults;
        }
        else
            return new DisplaySettings(settingsMap);
    }

    public void saveDisplaySettings(Container container, DisplaySettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestDisplay", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public StatusSettings getStatusSettings(Container container)
    {
        Map<String, String> settingsMap = PropertyManager.getProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus");
        if (settingsMap.get(StatusSettings.KEY_USE_SHOPPING_CART) == null)
        {
            StatusSettings defaults = StatusSettings.getDefaultSettings();
            saveStatusSettings(container, defaults);
            return defaults;
        }
        else
            return new StatusSettings(settingsMap);
    }

    public void saveStatusSettings(Container container, StatusSettings settings)
    {
        PropertyManager.PropertyMap settingsMap = PropertyManager.getWritableProperties(UserManager.getGuestUser(),
                container, "SpecimenRequestStatus", true);
        settings.populateMap(settingsMap);
        settingsMap.save();
    }

    public boolean isSpecimenShoppingCartEnabled(Container container)
    {
        return getStatusSettings(container).isUseShoppingCart();
    }

    public static class SpecimenRequestInput
    {
        private String _title;
        private String _helpText;
        private boolean _required;
        private boolean _rememberSiteValue;
        private boolean _multiLine;
        private int _displayOrder;
        private Map<Integer,String> _locationToDefaultValue;

        public SpecimenRequestInput(String title, String helpText, int displayOrder, boolean multiLine, boolean required, boolean rememberSiteValue)
        {
            _title = title;
            _required = required;
            _rememberSiteValue = rememberSiteValue;
            _helpText = helpText;
            _displayOrder = displayOrder;
            _multiLine = multiLine;
        }

        public SpecimenRequestInput(String title, String helpText, int displayOrder)
        {
            this(title, helpText, displayOrder, false, false, false);
        }

        public String getHelpText()
        {
            return _helpText;
        }

        public boolean isRememberSiteValue()
        {
            return _rememberSiteValue;
        }

        public boolean isRequired()
        {
            return _required;
        }

        public String getTitle()
        {
            return _title;
        }

        public int getDisplayOrder()
        {
            return _displayOrder;
        }

        public boolean isMultiLine()
        {
            return _multiLine;
        }

        public void setMultiLine(boolean multiLine)
        {
            _multiLine = multiLine;
        }

        public void setRememberSiteValue(boolean rememberSiteValue)
        {
            _rememberSiteValue = rememberSiteValue;
        }

        public void setRequired(boolean required)
        {
            _required = required;
        }

        public Map<Integer,String> getDefaultSiteValues(Container container) throws ValidationException
        {
            if (!isRememberSiteValue())
                throw new UnsupportedOperationException("Only those inputs set to remember site values can be queried for a site default.");

            if (_locationToDefaultValue != null)
                return _locationToDefaultValue;
            String defaultObjectLsid = getRequestInputDefaultObjectLsid(container);
            String setItemLsid = ensureOntologyManagerSetItem(container, defaultObjectLsid, getTitle());
            Map<Integer, String> locationToValue = new HashMap<>();

            Map<String, ObjectProperty> defaultValueProperties = OntologyManager.getPropertyObjects(container, setItemLsid);
            if (defaultValueProperties != null)
            {
                for (Map.Entry<String, ObjectProperty> defaultValue : defaultValueProperties.entrySet())
                {
                    String locationIdString = defaultValue.getKey().substring(defaultValue.getKey().lastIndexOf(".") + 1);
                    int locationId = Integer.parseInt(locationIdString);
                    locationToValue.put(locationId, defaultValue.getValue().getStringValue());
                }
            }
            _locationToDefaultValue = locationToValue;
            return _locationToDefaultValue;
        }

        public void setDefaultSiteValue(Container container, int locationId, String value) throws SQLException
        {
            try {
                assert locationId > 0 : "Invalid site id: " + locationId;
                if (!isRememberSiteValue())
                    throw new UnsupportedOperationException("Only those inputs configured to remember site values can set a site default.");
                _locationToDefaultValue = null;
                String parentObjectLsid = getRequestInputDefaultObjectLsid(container);

                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, getTitle());
                String propertyId = parentObjectLsid + "." + locationId;
                ObjectProperty defaultValueProperty = new ObjectProperty(setItemLsid, container, propertyId, value);
                OntologyManager.deleteProperty(setItemLsid, propertyId, container, container);
                OntologyManager.insertProperties(container, setItemLsid, defaultValueProperty);
            }
            catch (ValidationException e)
            {
                throw new SQLException(e.getMessage());
            }
        }
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container) throws SQLException
    {
        return getNewSpecimenRequestInputs(container, true);
    }

    public SpecimenRequestInput[] getNewSpecimenRequestInputs(Container container, boolean createIfMissing) throws SQLException
    {
        String parentObjectLsid = getRequestInputObjectLsid(container);
        Map<String,ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, parentObjectLsid);
        SpecimenRequestInput[] inputs = new SpecimenRequestInput[0];
        if (resourceProperties == null || resourceProperties.size() == 0)
        {
            if (createIfMissing)
            {
                inputs = new SpecimenRequestInput[] {
                        new SpecimenRequestInput("Assay Plan", "Please enter a description of or reference to the assay plan(s) that will be used for the requested specimens.", 0, true, true, false),
                        new SpecimenRequestInput("Shipping Information", "Please enter your shipping address along with any special instructions.", 1, true, true, true),
                        new SpecimenRequestInput("Comments", "Please enter any additional information regarding your request.", 2, true, false, false)
                };
                saveNewSpecimenRequestInputs(container, inputs);
            }
            return inputs;
        }
        else
        {
            inputs = new SpecimenRequestInput[resourceProperties.size()];
            for (Map.Entry<String, ObjectProperty> parentPropertyEntry : resourceProperties.entrySet())
            {
                String resourcePropertyLsid = parentPropertyEntry.getKey();
                int displayOrder = Integer.parseInt(resourcePropertyLsid.substring(resourcePropertyLsid.lastIndexOf('.') + 1));

                Map<String, ObjectProperty> inputProperties = parentPropertyEntry.getValue().retrieveChildProperties();
                String title = inputProperties.get(parentObjectLsid + ".Title").getStringValue();
                String helpText = null;
                if (inputProperties.get(parentObjectLsid + ".HelpText") != null)
                    helpText = inputProperties.get(parentObjectLsid + ".HelpText").getStringValue();
                boolean rememberSiteValue = inputProperties.get(parentObjectLsid + ".RememberSiteValue").getFloatValue() == 1;
                boolean required = inputProperties.get(parentObjectLsid + ".Required").getFloatValue() == 1;
                boolean multiLine = inputProperties.get(parentObjectLsid + ".MultiLine").getFloatValue() == 1;
                inputs[displayOrder] = new SpecimenRequestInput(title, helpText, displayOrder, multiLine, required, rememberSiteValue);
            }
        }
        return inputs;
    }

    private static String getRequestInputObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInput").toString();
    }

    private static String getRequestInputDefaultObjectLsid(Container container)
    {
        return new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + container.getRowId(), "RequestInputDefault").toString();
    }

    private static String ensureOntologyManagerSetItem(Container container, String lsidBase, String uniqueItemId) throws ValidationException
    {
        Integer listParentObjectId = OntologyManager.ensureObject(container, lsidBase);
        String listItemReferenceLsidPrefix = lsidBase + "#objectResource.";
        String listItemObjectLsid = lsidBase + "#" + uniqueItemId;
        String listItemPropertyReferenceLsid = listItemReferenceLsidPrefix + uniqueItemId;

        // ensure the object that corresponds to a single list item:
        OntologyManager.ensureObject(container, listItemObjectLsid, listParentObjectId);

        // check to make sure that the list item is wired up to the top-level list object via a property:
        Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(container, lsidBase);
        if (!properties.containsKey(listItemPropertyReferenceLsid))
        {
            // create the resource property that links the parent object to the list item object:
            ObjectProperty resourceProperty = new ObjectProperty(lsidBase, container,
                    listItemPropertyReferenceLsid, listItemObjectLsid, PropertyType.RESOURCE);
            OntologyManager.insertProperties(container, lsidBase, resourceProperty);
        }
        return listItemObjectLsid;
    }

    public void saveNewSpecimenRequestInputs(Container container, SpecimenRequestInput[] inputs) throws SQLException
    {
        if (!requestInputsChanged(container, inputs))
            return;

        try {
            String parentObjectLsid = getRequestInputObjectLsid(container);
            String defaultValuesObjectLsid = getRequestInputDefaultObjectLsid(container);
            OntologyManager.deleteOntologyObject(parentObjectLsid, container, true);
            OntologyManager.deleteOntologyObject(defaultValuesObjectLsid, container, true);
            for (int i = 0; i < inputs.length; i++)
            {
                SpecimenRequestInput input = inputs[i];
                String setItemLsid = ensureOntologyManagerSetItem(container, parentObjectLsid, "" + i);
                ObjectProperty[] props = new ObjectProperty[5];
                props[0] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".HelpText", input.getHelpText());
                props[1] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Required", input.isRequired() ? 1 : 0);
                props[2] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".RememberSiteValue", input.isRememberSiteValue() ? 1 : 0);
                props[3] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".Title", input.getTitle());
                props[4] = new ObjectProperty(setItemLsid, container, parentObjectLsid + ".MultiLine", input.isMultiLine() ? 1 : 0);
                OntologyManager.insertProperties(container, setItemLsid, props);
            }
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    private boolean requestInputsChanged(Container container, SpecimenRequestInput[] newInputs) throws SQLException
    {
        SpecimenRequestInput[] oldInputs = getNewSpecimenRequestInputs(container, false);
        if (oldInputs.length == 0)
            return true;
        else if (oldInputs.length != newInputs.length)
            return true;
        else
        {
            for (int i = 0; i < oldInputs.length; i++)
            {
                if (oldInputs[i].isMultiLine() != newInputs[i].isMultiLine() ||
                    oldInputs[i].isRememberSiteValue() != newInputs[i].isRememberSiteValue() ||
                    oldInputs[i].isRequired() != newInputs[i].isRequired() ||
                    !oldInputs[i].getTitle().equals(newInputs[i].getTitle()) ||
                    !getSafeString(oldInputs[i].getHelpText()).equals(getSafeString(newInputs[i].getHelpText())))
                    return true;
            }
        }
        return false;
    }

    private String getSafeString(String str)
    {
        if (str == null)
            return "";
        else
            return str;
    }

    private static final ReentrantLock REQUEST_ADDITION_LOCK = new ReentrantLock();
    public void createRequestSampleMapping(User user, SpecimenRequest request, List<Vial> vials, boolean createEvents, boolean createRequirements)
            throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException,
                   SpecimenRequestException
    {
        if (vials == null || vials.size() == 0)
            return;

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction(REQUEST_ADDITION_LOCK))
        {
            for (Vial vial : vials)
            {
                if (!request.getContainer().getId().equals(vial.getContainer().getId()))
                    throw new IllegalStateException("Mismatched containers.");

                if (!vial.isAvailable())
                    throw new SpecimenRequestException("Vial unavailable.");
            }

            for (Vial vial : vials)
            {
                Map<String, Object> fields = new HashMap<>();
                fields.put("Container", request.getContainer().getId());
                fields.put("SampleRequestId", request.getRowId());
                fields.put("SpecimenGlobalUniqueId", vial.getGlobalUniqueId());
                Table.insert(user, StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), fields);
                if (createEvents)
                    createRequestEvent(user, request, RequestEventType.SPECIMEN_ADDED, vial.getSampleDescription(), null);
            }

            if (createRequirements)
                getRequirementsProvider().generateDefaultRequirements(user, request);

            SpecimenRequestStatus status = getRequestStatus(request.getContainer(), request.getStatusId());
            updateSpecimenStatus(vials, user, status.isSpecimensLocked());

            transaction.commit();
        }
    }

    public List<Vial> getVials(Container container, User user, int[] vialsRowIds)
    {
        Set<Long> uniqueRowIds = new HashSet<>(vialsRowIds.length);
        for (int vialRowId : vialsRowIds)
            uniqueRowIds.add((long)vialRowId);
        return getVials(container, user, uniqueRowIds);
    }

    public List<Vial> getVials(Container container, User user, Set<Long> vialRowIds)
    {
        // Take a set to eliminate dups - issue 26940

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("RowId"), vialRowIds);
        List<Vial> vials = getVials(container, user, filter);
        if (vials.size() != vialRowIds.size())
        {
            List<Long> unmatchedRowIds = new ArrayList<>(vialRowIds);
            for (Vial vial : vials)
            {
                unmatchedRowIds.remove(vial.getRowId());
            }
            throw new SpecimenRequestException("One or more specimen RowIds had no matching specimen: " + unmatchedRowIds);
        }
        return vials;
    }

    public static class SpecimenRequestException extends RuntimeException
    {
        public SpecimenRequestException()
        {
        }

        public SpecimenRequestException(String message)
        {
            super(message);
        }
    }

    public List<Vial> getVials(Container container, User user, String[] globalUniqueIds) throws SpecimenRequestException
    {
        SimpleFilter filter = new SimpleFilter();
        Set<String> uniqueRowIds = new HashSet<>(globalUniqueIds.length);
        Collections.addAll(uniqueRowIds, globalUniqueIds);
        List<String> ids = new ArrayList<>(uniqueRowIds);
        filter.addInClause(FieldKey.fromParts("GlobalUniqueId"), ids);
        List<Vial> vials = getVials(container, user, filter);
        if (vials == null || vials.size() != ids.size())
            throw new SpecimenRequestException("Vial not found.");       // an id has no matching specimen, let caller determine what to report
        return vials;
    }

    public void deleteRequest(User user, SpecimenRequest request) throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        DbScope scope = _requestHelper.getTableInfo().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            List<Vial> vials = request.getVials();
            List<Long> specimenIds = new ArrayList<>(vials.size());
            for (Vial vial : vials)
                specimenIds.add(vial.getRowId());

            deleteRequestSampleMappings(user, request, specimenIds, false);

            deleteMissingSpecimens(request);

            _requirementProvider.deleteRequirements(request);

            deleteRequestEvents(user, request);
            _requestHelper.delete(request);

            transaction.commit();
        }
    }

    public void deleteRequestSampleMappings(User user, SpecimenRequest request, List<Long> vialIds, boolean createEvents)
            throws SQLException, RequestabilityManager.InvalidRuleException, AttachmentService.DuplicateFilenameException
    {
        if (vialIds == null || vialIds.size() == 0)
            return;

        Set<Long> vialRowIds = new HashSet<>(vialIds);
        List<Vial> vials = getVials(request.getContainer(), user, vialRowIds);
        List<String> globalUniqueIds = new ArrayList<>(vials.size());
        List<String> descriptions = new ArrayList<>();
        for (Vial vial : vials)
        {
            globalUniqueIds.add(vial.getGlobalUniqueId());
            descriptions.add(vial.getSampleDescription());
        }

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(request.getContainer());
            filter.addCondition(FieldKey.fromParts("SampleRequestId"), request.getRowId());
            filter.addInClause(FieldKey.fromParts("SpecimenGlobalUniqueId"), globalUniqueIds);
            Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), filter);
            if (createEvents)
            {
                for (String description : descriptions)
                    createRequestEvent(user, request, RequestEventType.SPECIMEN_REMOVED, description, null);
            }

            updateSpecimenStatus(vials, user, false);

            transaction.commit();
        }
    }

    public @NotNull List<Integer> getRequestIdsForSpecimen(Vial vial) throws SQLException
    {
        return getRequestIdsForSpecimen(vial, false);
    }

    public @NotNull List<Integer> getRequestIdsForSpecimen(Vial vial, boolean lockingRequestsOnly) throws SQLException
    {
        if (vial == null)
            return Collections.emptyList();

        SQLFragment sql = new SQLFragment("SELECT SampleRequestId FROM " + StudySchema.getInstance().getTableInfoSampleRequestSpecimen() +
                " Map, " + StudySchema.getInstance().getTableInfoSampleRequest() + " Request, " +
                StudySchema.getInstance().getTableInfoSampleRequestStatus() + " Status WHERE SpecimenGlobalUniqueId = ? " +
                "AND Request.Container = ? AND Map.Container = Request.Container AND Status.Container = Request.Container " +
                "AND Map.SampleRequestId = Request.RowId AND Request.StatusId = Status.RowId");
        sql.add(vial.getGlobalUniqueId());
        sql.add(vial.getContainer().getId());

        if (lockingRequestsOnly)
        {
            sql.append(" AND Status.SpecimensLocked = ?");
            sql.add(Boolean.TRUE);
        }

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(Integer.class);
    }

    public SpecimenTypeSummary getSpecimenTypeSummary(Container container, @NotNull User user)
    {
        StudyQuerySchema studyQuerySchema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, false);
        TableInfo tableInfoSpecimenWrap = studyQuerySchema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenWrap)
            throw new IllegalStateException("SpecimenDetail table not found.");

        TableInfo additiveTableInfo = studyQuerySchema.getTable(StudyQuerySchema.SPECIMEN_ADDITIVE_TABLE_NAME);
        TableInfo derivativeTableInfo = studyQuerySchema.getTable(StudyQuerySchema.SPECIMEN_DERIVATIVE_TABLE_NAME);
        TableInfo primaryTypeTableInfo = studyQuerySchema.getTable(StudyQuerySchema.SPECIMEN_PRIMARY_TYPE_TABLE_NAME);
        String tableInfoSelectName = "SpecimenWrap";

        // TODO: consider caching

        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            return null;

        SQLFragment specimenTypeSummarySQL = new SQLFragment("SELECT\n" +
                "\tPrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tDerivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tAdditive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSUM(VialCount) AS VialCount\n" +
                "FROM (\n" +
                "\tSELECT\n" +
                "\tPT.PrimaryType AS PrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tDT.Derivative AS Derivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tAT.Additive AS Additive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSpecimens.VialCount\n" +
                "\tFROM\n");

        SQLFragment sqlPtidFilter = new SQLFragment();
        if (study.isAncillaryStudy())
        {
/*            StudyQuerySchema sourceStudySchema = StudyQuerySchema.createSchema(study.getSourceStudy(), null, false);
            SpecimenWrapTable sourceStudyTableInfo = (SpecimenWrapTable)sourceStudySchema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
            tableInfoSpecimenWrap.setUnionTable(sourceStudyTableInfo);

            String[] ptids = StudyManager.getInstance().getParticipantIds(study);
            sqlPtidFilter.append("\t\t\tWHERE ").append(tableInfoSpecimenWrap.getColumn("PTID").getValueSql(tableInfoSelectName)).append(" IN (");
            if (ptids == null || ptids.length == 0)
                sqlPtidFilter.append("NULL");
            else
            {
                String comma = "";
                for (String ptid : ptids)
                {
                    sqlPtidFilter.append(comma).append("?");
                    sqlPtidFilter.add(ptid);
                    comma = ", ";
                }
            }
            sqlPtidFilter.append(")\n");  */
        }

        specimenTypeSummarySQL.append("\t\t(SELECT ")
                .append(tableInfoSpecimenWrap.getColumn("PrimaryTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("DerivativeTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("AdditiveTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append("\n\t\t\tSUM(").append(tableInfoSpecimenWrap.getColumn("VialCount").getValueSql(tableInfoSelectName))
                .append(") AS VialCount\n")
                .append("\n\t\tFROM ").append(tableInfoSpecimenWrap.getFromSQL(tableInfoSelectName)).append("\n");
        specimenTypeSummarySQL.append(sqlPtidFilter);
        specimenTypeSummarySQL.append("\t\tGROUP BY ")
                .append(tableInfoSpecimenWrap.getColumn("PrimaryTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("DerivativeTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("AdditiveTypeId").getValueSql(tableInfoSelectName))
                .append("\t\t\t) Specimens\n").append(
                "\tLEFT OUTER JOIN ").append(primaryTypeTableInfo.getFromSQL("PT")).append(  " ON\n" +
                        "\t\tPT.RowId = Specimens.PrimaryTypeId\n" +
                        "\tLEFT OUTER JOIN ").append(derivativeTableInfo.getFromSQL("DT")).append(" ON\n" +
                        "\t\tDT.RowId = Specimens.DerivativeTypeId\n" +
                        "\tLEFT OUTER JOIN ").append(additiveTableInfo.getFromSQL("AT")).append(" ON\n" +
                        "\t\tAT.RowId = Specimens.AdditiveTypeId\n" +
                        ") ContainerTotals\n" +
                        "GROUP BY PrimaryType, PrimaryTypeId, Derivative, DerivativeTypeId, Additive, AdditiveTypeId\n" +
                        "ORDER BY PrimaryType, Derivative, Additive");

        SpecimenTypeSummaryRow[] rows = new SqlSelector(StudySchema.getInstance().getSchema(), specimenTypeSummarySQL).getArray(SpecimenTypeSummaryRow.class);

        return new SpecimenTypeSummary(container, rows);
    }

    private class DistinctValueList extends ArrayList<String> implements StudyCachable
    {
        private final Container _container;
        private final String _cacheKey;

        private DistinctValueList(Container container, String cacheKey)
        {
            super();
            _container = container;
            _cacheKey = cacheKey;
        }

        public StudyCachable createMutable()
        {
            throw new UnsupportedOperationException("DistinctValueList objects are never mutable.");
        }

        public Container getContainer()
        {
            return _container;
        }

        public Object getPrimaryKey()
        {
            return _cacheKey;
        }

        public void lock()
        {
        }
    }

/*       --- this has been unused for some time. Used to be called from SpecimenBean, but that's been commented out since I don't know when (Dave 5/7/15)
         --- if resurrected, need to deal with DbCache
    public List<String> getDistinctColumnValues(Container container, User user, ColumnInfo col, boolean forceDistinctQuery,
                                                String orderBy, TableInfo forcedTable) throws SQLException
    {
        String cacheKey = "DistinctColumnValues." + col.getColumnName();
        boolean isLookup = col.getFk() != null && !forceDistinctQuery;
        TableInfo tinfo = forcedTable;
        if (tinfo == null)
            tinfo = isLookup ? col.getFk().getLookupTableInfo() : col.getParentTable();
        TableInfo cachedTinfo = tinfo instanceof FilteredTable ? ((FilteredTable) tinfo).getRealTable() : tinfo;

        // TODO: Convert this to use a CacheLoader
        DistinctValueList distinctValues = (DistinctValueList) StudyCache.getCached(cachedTinfo, container, cacheKey);

        if (null != distinctValues)
            return distinctValues;

        final DistinctValueList newDistinctValues = new DistinctValueList(container, cacheKey);

        if (col.isBooleanType())
        {
            newDistinctValues.add("True");
            newDistinctValues.add("False");
        }
        else
        {
            Selector selector;

            if (isLookup)
            {
                if (tinfo.supportsContainerFilter())
                {
                    Set<Container> containers = new HashSet<>();
                    containers.add(container);
                    Study study = StudyManager.getInstance().getStudy(container);
                    if (study != null && study.isAncillaryStudy())
                    {
                        Container sourceStudy = study.getSourceStudy().getContainer();
                        if (sourceStudy != null && sourceStudy.hasPermission(user, ReadPermission.class))
                            containers.add(sourceStudy);
                    }
                    ((ContainerFilterable)tinfo).setContainerFilter(new ContainerFilter.SimpleContainerFilter(containers));
                }

                selector = new TableSelector(tinfo.getColumn(tinfo.getTitleColumn()), null,
                        new Sort(orderBy != null ? orderBy : tinfo.getTitleColumn()));
            }
            else
            {
                SQLFragment sql = new SQLFragment("SELECT DISTINCT ").append(col.getValueSql("_distinct").getSQL()).append(" FROM ");
                sql.append(tinfo.getFromSQL("_distinct"));
                if (orderBy != null)
                    sql.append(" ORDER BY ").append(orderBy);

                selector = new SqlSelector(tinfo.getSchema(), sql);
            }

            selector.forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    Object value = rs.getObject(1);
                    if (value != null && value.toString().length() > 0)
                        newDistinctValues.add(value.toString());
                }
            });
        }

        StudyCache.cache(cachedTinfo, container, cacheKey, newDistinctValues);

        return newDistinctValues;
    }
*/
    public void deleteMissingSpecimens(SpecimenRequest specimenRequest) throws SQLException
    {
        List<String> missingSpecimens = getMissingSpecimens(specimenRequest);
        if (missingSpecimens.isEmpty())
            return;
        SimpleFilter filter = SimpleFilter.createContainerFilter(specimenRequest.getContainer());
        filter.addCondition(FieldKey.fromParts("SampleRequestId"), specimenRequest.getRowId());
        filter.addInClause(FieldKey.fromParts("SpecimenGlobalUniqueId"), missingSpecimens);
        Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), filter);
    }

    public boolean isSampleRequestEnabled(Container container)
    {
        return isSampleRequestEnabled(container, true);
    }

    public boolean isSampleRequestEnabled(Container container, boolean checkExistingStatuses)
    {
        if (!checkExistingStatuses)
        {
            return getRepositorySettings(container).isEnableRequests();
        }
        else
        {
            if (!getRepositorySettings(container).isEnableRequests())
                return false;
            List<SpecimenRequestStatus> statuses = _requestStatusHelper.get(container, "SortOrder");
            return (statuses != null && statuses.size() > 1);
        }
    }

    public List<String> getMissingSpecimens(SpecimenRequest specimenRequest) throws SQLException
    {
        Container container = specimenRequest.getContainer();
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);
        SQLFragment sql = new SQLFragment("SELECT SpecimenGlobalUniqueId FROM study.SampleRequestSpecimen WHERE SampleRequestId = ? and Container = ? and \n" +
                "SpecimenGlobalUniqueId NOT IN (SELECT ");
        sql.add(specimenRequest.getRowId());
        sql.add(container);
        sql.append(tableInfoVial.getColumn("GlobalUniqueId").getValueSql("Vial"))
            .append(" FROM ").append(tableInfoVial.getFromSQL("Vial")).append(")");

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(String.class);
    }

    public Map<String,List<Vial>> getVialsForSampleHashes(Container container, User user, Collection<String> hashes, boolean onlyAvailable)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("SpecimenHash"), hashes);
        if (onlyAvailable)
            filter.addCondition(FieldKey.fromParts("Available"), true);
        List<Vial> vials = getVials(container, user, filter);
        Map<String, List<Vial>> map = new HashMap<>();
        for (Vial vial : vials)
        {
            String hash = vial.getSpecimenHash();
            List<Vial> keyVials = map.get(hash);
            if (null == keyVials)
            {
                keyVials = new ArrayList<>();
                map.put(hash, keyVials);
            }
            keyVials.add(vial);
        }

        return map;
    }

    public Map<String, Integer> getSampleCounts(Container container, Collection<String> specimenHashes)
    {
        TableInfo tableInfoSpecimen = StudySchema.getInstance().getTableInfoSpecimen(container);

        SQLFragment extraClause = null;
        if (specimenHashes != null)
        {
            extraClause = new SQLFragment(" WHERE SpecimenHash ");
            tableInfoSpecimen.getSqlDialect().appendInClauseSql(extraClause, specimenHashes);
        }

        final Map<String, Integer> map = new HashMap<>();

        SQLFragment sql = new SQLFragment("SELECT SpecimenHash, CAST(AvailableCount AS Integer) AS AvailableCount FROM ");
        sql.append(tableInfoSpecimen.getFromSQL(""));
        if (extraClause != null)
        {
            sql.append(extraClause);
        }
        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>() {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String specimenHash = rs.getString("SpecimenHash");
                map.put(specimenHash, rs.getInt("AvailableCount"));
            }
        });

        return map;
    }

    public int getSampleCountForVisit(VisitImpl visit) throws SQLException
    {
        Container container = visit.getContainer();
        TableInfo tableInfoSpecimen = StudySchema.getInstance().getTableInfoSpecimen(container);
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);

        String tableInfoSpecimenAlias = "Specimen";
        String tableInfoVialAlias = "Vial";
        SQLFragment visitRangeSql = getVisitRangeSql(visit, tableInfoSpecimen, tableInfoSpecimenAlias);
        if (null == visitRangeSql)
            return 0;

        SQLFragment sql = new SQLFragment("SELECT COUNT(*) AS NumVials FROM ");
        sql.append(tableInfoVial.getFromSQL(tableInfoVialAlias)).append(" \n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimen.getFromSQL(tableInfoSpecimenAlias)).append(" ON\n\t")
                .append(tableInfoVial.getColumn("SpecimenId").getValueSql(tableInfoVialAlias)).append(" = ")
                .append(tableInfoSpecimen.getColumn("RowId").getValueSql(tableInfoSpecimenAlias))
                .append("\n WHERE ").append(visitRangeSql);
        List<Integer> results = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArrayList(Integer.class);
        if (1 != results.size())
            throw new IllegalStateException("Expected value from Select Count(*)");
        return results.get(0);
    }


    public void deleteSamplesForVisit(VisitImpl visit)
    {
        Container container = visit.getContainer();
        TableInfo tableInfoSpecimen = StudySchema.getInstance().getTableInfoSpecimen(container);
        TableInfo tableInfoSpecimenEvent = StudySchema.getInstance().getTableInfoSpecimenEvent(container);
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);

        String tableInfoSpecimenSelectName = tableInfoSpecimen.getSelectName();
        String tableInfoSpecimenEventSelectName = tableInfoSpecimenEvent.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();

        // Fix 19048 (dave); handle "no visit" case better; would be better to call once and get both variations
        SQLFragment visitRangeSql1 = getVisitRangeSql(visit, tableInfoSpecimen, tableInfoSpecimenSelectName);
        SQLFragment visitRangeSql2 = getVisitRangeSql(visit, tableInfoSpecimen, "Specimen");
        if (null == visitRangeSql1 || null == visitRangeSql2)
            return;

        SQLFragment specimenRowIdWhereSql = new SQLFragment(" WHERE ").append(visitRangeSql2);

        SQLFragment deleteEventSql = new SQLFragment("DELETE FROM ");
        deleteEventSql.append(tableInfoSpecimenEventSelectName)
                .append(" WHERE RowId IN (\n")
                .append("SELECT Event.RowId FROM ")
                .append(tableInfoSpecimenEventSelectName).append(" AS Event\n")
                .append("LEFT OUTER JOIN ").append(tableInfoVialSelectName).append(" AS Vial ON\n")
                .append("\tEvent.VialId = Vial.RowId\n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimenSelectName).append(" AS Specimen ON\n")
                .append("\tVial.SpecimenId = Specimen.RowId\n")
                .append(specimenRowIdWhereSql).append(")");
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteEventSql);

        SQLFragment deleteVialSql = new SQLFragment("DELETE FROM ");
        deleteVialSql.append(tableInfoVialSelectName)
                .append(" WHERE RowId IN (\n")
                .append("SELECT Vial.RowId FROM ")
                .append(tableInfoVialSelectName).append(" AS Vial\n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimenSelectName).append(" AS Specimen ON\n")
                .append("\tVial.SpecimenId = Specimen.RowId\n")
                .append(specimenRowIdWhereSql).append(")");

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteVialSql);

        SQLFragment specimenRowIdSelectSql = new SQLFragment("FROM " + tableInfoSpecimen.getSelectName() + " WHERE ").append(visitRangeSql1);
        SQLFragment deleteSpecimenSql = new SQLFragment("DELETE ");
        deleteSpecimenSql.append(specimenRowIdSelectSql);

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(deleteSpecimenSql);

        clearCaches(visit.getContainer());
    }

    @Nullable
    private SQLFragment getVisitRangeSql(VisitImpl visit, TableInfo tinfoSpecimen, String specimenAlias)
    {
        // Return null only in the case where there are 0 participant visits for the given visit
        Study study = StudyService.get().getStudy(visit.getContainer());
        if (null == study)
            throw new IllegalStateException("No study found.");

        SQLFragment sqlVisitRange = new SQLFragment();
        sqlVisitRange.append(tinfoSpecimen.getColumn("VisitValue").getValueSql(specimenAlias)).append(" >= ? AND ")
                .append(tinfoSpecimen.getColumn("VisitValue").getValueSql(specimenAlias)).append(" <= ?");

        SQLFragment sql = new SQLFragment();
        if (TimepointType.VISIT == study.getTimepointType())
        {
            sql.append(sqlVisitRange);
            sql.add(visit.getSequenceNumMin());
            sql.add(visit.getSequenceNumMax());
        }
        else
        {
            // For date-based we need to get the range from ParticipantVisit
            ColumnInfo columnInfo = StudySchema.getInstance().getTableInfoParticipantVisit().getColumn("SequenceNum");
            Filter filter = new SimpleFilter(FieldKey.fromString("VisitRowId"), visit.getRowId());
            Sort sort = new Sort();
            sort.insertSortColumn(FieldKey.fromString("SequenceNum"), Sort.SortDirection.ASC);
            ArrayList<Double> visitValues = new TableSelector(columnInfo, filter, sort).getArrayList(Double.class);
            if (0 == visitValues.size())
            {
                // No participant visits for this timepoint; return False
                return null;
            }
            else
            {
                sql.append(sqlVisitRange);
                sql.add(visitValues.get(0));
                sql.add(visitValues.get(visitValues.size() - 1));
            }
        }
        return sql;
    }

    public void deleteSpecimen(@NotNull Vial vial, boolean clearCaches) throws SQLException
    {
        Container container = vial.getContainer();
        TableInfo tableInfoSpecimenEvent = StudySchema.getInstance().getTableInfoSpecimenEvent(container);
        TableInfo tableInfoVial = StudySchema.getInstance().getTableInfoVial(container);
        if (null == tableInfoSpecimenEvent || null == tableInfoVial)
            return;

        String tableInfoSpecimenEventSelectName = tableInfoSpecimenEvent.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();

        SQLFragment sqlFragmentEvent = new SQLFragment("DELETE FROM ");
        sqlFragmentEvent.append(tableInfoSpecimenEventSelectName).append(" WHERE VialId = ?");
        sqlFragmentEvent.add(vial.getRowId());
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sqlFragmentEvent);

        SQLFragment sqlFragment = new SQLFragment("DELETE FROM ");
        sqlFragment.append(tableInfoVialSelectName).append(" WHERE RowId = ?");
        sqlFragment.add(vial.getRowId());
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sqlFragment);

        if (clearCaches)
            clearCaches(vial.getContainer());
    }

    public void deleteAllSampleData(Container c, Set<TableInfo> set)
    {
        // UNDONE: use transaction?
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);

        Table.delete(StudySchema.getInstance().getTableInfoSampleRequestSpecimen(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestSpecimen());
        Table.delete(_requestEventHelper.getTableInfo(), containerFilter);
        assert set.add(_requestEventHelper.getTableInfo());
        Table.delete(_requestHelper.getTableInfo(), containerFilter);
        assert set.add(_requestHelper.getTableInfo());
        Table.delete(_requestStatusHelper.getTableInfo(), containerFilter);
        assert set.add(_requestStatusHelper.getTableInfo());

        new SpecimenTablesProvider(c, null, null).deleteTables();
/*
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenAdditive(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenAdditive());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenDerivative(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenDerivative());
        Table.delete(StudySchema.getInstance().getTableInfoSpecimenPrimaryType(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSpecimenPrimaryType());
*/
        Table.delete(StudySchema.getInstance().getTableInfoSampleAvailabilityRule(), containerFilter);
        assert set.add(StudySchema.getInstance().getTableInfoSampleAvailabilityRule());


        _requirementProvider.purgeContainer(c);
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestRequirement());
        assert set.add(StudySchema.getInstance().getTableInfoSampleRequestActor());

        DbSchema expSchema = ExperimentService.get().getSchema();
        TableInfo tinfoMaterial = expSchema.getTable("Material");

        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(c, SpecimenManager.STUDY_SPECIMENS_SAMPLE_SET_NAME);

        if (sampleSet != null)
        {
            SimpleFilter materialFilter = new SimpleFilter(containerFilter);
            materialFilter.addCondition(FieldKey.fromParts("CpasType"), sampleSet.getLSID());
            Table.delete(tinfoMaterial, materialFilter);
        }

        // Views  // TODO when these views get removed remove this
        assert set.add(StudySchema.getInstance().getSchema().getTable("LockedSpecimens"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenSummary"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("SpecimenDetail"));
        assert set.add(StudySchema.getInstance().getSchema().getTable("VialCounts"));

        clearGroupedValuesForColumn(c);
    }


    public void clearCaches(Container c)
    {
        _requestEventHelper.clearCache(c);
        _requestHelper.clearCache(c);
        _requestStatusHelper.clearCache(c);
        for (StudyImpl study : StudyManager.getInstance().getAncillaryStudies(c))
            clearCaches(study.getContainer());

        clearGroupedValuesForColumn(c);
    }

    public List<VisitImpl> getVisitsWithSpecimens(Container container, User user)
    {
        return getVisitsWithSpecimens(container, user, null);
    }

    public List<VisitImpl> getVisitsWithSpecimens(Container container, User user, CohortImpl cohort)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tinfo = schema.getTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);

        FieldKey visitKey = FieldKey.fromParts("Visit");
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, Collections.singleton(visitKey));
        Collection<ColumnInfo> cols = new ArrayList<>();
        cols.add(colMap.get(visitKey));
        Set<FieldKey> unresolvedColumns = new HashSet<>();
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, null, null, unresolvedColumns);
        if (!unresolvedColumns.isEmpty())
            throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
        // generate our select SQL:
        SQLFragment specimenSql = Table.getSelectSQL(tinfo, cols, null, null);

        SQLFragment visitIdSQL = new SQLFragment("SELECT DISTINCT Visit FROM (" + specimenSql.getSQL() + ") SimpleSpecimenQuery");
        visitIdSQL.addAll(specimenSql.getParamsArray());

        List<Integer> visitIds = new SqlSelector(StudySchema.getInstance().getSchema(), visitIdSQL).getArrayList(Integer.class);

        // Get shared visit study
        Study visitStudy = StudyManager.getInstance().getStudyForVisits(StudyManager.getInstance().getStudy(container));

        SimpleFilter filter = SimpleFilter.createContainerFilter(visitStudy.getContainer());
        filter.addInClause(FieldKey.fromParts("RowId"), visitIds);
        if (cohort != null)
            filter.addWhereClause("CohortId IS NULL OR CohortId = ?", new Object[] { cohort.getRowId() });
        return new TableSelector(StudySchema.getInstance().getTableInfoVisit(), filter, new Sort("DisplayOrder,SequenceNumMin")).getArrayList(VisitImpl.class);
    }

    public static class SummaryByVisitType extends SpecimenCountSummary
    {
        private String _primaryType;
        private String _derivative;
        private String _additive;
        private Long _participantCount;
        private Set<String> _participantIds;

        public String getPrimaryType()
        {
            return _primaryType;
        }

        public void setPrimaryType(String primaryType)
        {
            _primaryType = primaryType;
        }

        public String getDerivative()
        {
            return _derivative;
        }

        public void setDerivative(String derivative)
        {
            _derivative = derivative;
        }

        public Long getParticipantCount()
        {
            return _participantCount;
        }

        public void setParticipantCount(Long participantCount)
        {
            _participantCount = participantCount;
        }

        public Set<String> getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(Set<String> participantIds)
        {
            _participantIds = participantIds;
        }

        public String getAdditive()
        {
            return _additive;
        }

        public void setAdditive(String additive)
        {
            _additive = additive;
        }
    }

    public static class RequestSummaryByVisitType extends SummaryByVisitType
    {
        private Integer _destinationSiteId;
        private String _siteLabel;

        public Integer getDestinationSiteId()
        {
            return _destinationSiteId;
        }

        public void setDestinationSiteId(Integer destinationSiteId)
        {
            _destinationSiteId = destinationSiteId;
        }

        public String getSiteLabel()
        {
            return _siteLabel;
        }

        public void setSiteLabel(String siteLabel)
        {
            _siteLabel = siteLabel;
        }
    }

    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, boolean includeParticipantGroups, SpecimenTypeLevel level) throws SQLException
    {
        return getSpecimenSummaryByVisitType(container, user, null, includeParticipantGroups, level);
    }

    public static class SpecimenTypeBeanProperty
    {
        private FieldKey _typeKey;
        private String _beanProperty;
        private SpecimenTypeLevel _level;

        public SpecimenTypeBeanProperty(FieldKey typeKey, String beanProperty, SpecimenTypeLevel level)
        {
            _typeKey = typeKey;
            _beanProperty = beanProperty;
            _level = level;
        }

        public FieldKey getTypeKey()
        {
            return _typeKey;
        }

        public String getBeanProperty()
        {
            return _beanProperty;
        }

        public SpecimenTypeLevel getLevel()
        {
            return _level;
        }
    }

    public enum SpecimenTypeLevel
    {
        PrimaryType()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> list = new ArrayList<>();
                list.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("PrimaryType", "Description"), "primaryType", this));
                return list;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType() };
            }

            public String getLabel()
            {
                return "Primary Type";
            }},
        Derivative()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.PrimaryType.getGroupingColumns();
                parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("DerivativeType", "Description"), "derivative", this));
                return parent;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative() };
            }
            public String getLabel()
            {
                return "Derivative";
            }},
        Additive()
        {
            public List<SpecimenTypeBeanProperty> getGroupingColumns()
            {
                List<SpecimenTypeBeanProperty> parent = SpecimenTypeLevel.Derivative.getGroupingColumns();
                parent.add(new SpecimenTypeBeanProperty(FieldKey.fromParts("AdditiveType", "Description"), "additive", this));
                return parent;
            }

            public String[] getTitleHierarchy(SummaryByVisitType summary)
            {
                return new String[] { summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive() };
            }

            public String getLabel()
            {
                return "Additive";
            }};

        public abstract String[] getTitleHierarchy(SummaryByVisitType summary);
        public abstract List<SpecimenTypeBeanProperty> getGroupingColumns();
        public abstract String getLabel();
    }

    private class SpecimenDetailQueryHelper
    {
        private SQLFragment _viewSql;
        private String _typeGroupingColumns;
        private Map<String, SpecimenTypeBeanProperty> _aliasToTypePropertyMap;

        private SpecimenDetailQueryHelper(SQLFragment viewSql, String typeGroupingColumns, Map<String, SpecimenTypeBeanProperty> aliasToTypePropertyMap)
        {
            _viewSql = viewSql;
            _typeGroupingColumns = typeGroupingColumns;
            _aliasToTypePropertyMap = aliasToTypePropertyMap;
        }

        public SQLFragment getViewSql()
        {
            return _viewSql;
        }

        public String getTypeGroupingColumns()
        {
            return _typeGroupingColumns;
        }

        public Map<String, SpecimenTypeBeanProperty> getAliasToTypePropertyMap()
        {
            return _aliasToTypePropertyMap;
        }
    }

    private SpecimenDetailQueryHelper getSpecimenDetailQueryHelper(Container container, User user,
                                                                   CustomView baseView, SimpleFilter specimenDetailFilter,
                                                                   SpecimenTypeLevel level)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tinfo = schema.getTable(StudyQuerySchema.SPECIMEN_DETAIL_TABLE_NAME);

        Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty = new LinkedHashMap<>();

        Collection<FieldKey> columns = new HashSet<>();
        if (baseView != null)
        {
            // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
            ActionURL url = new ActionURL();
            baseView.applyFilterAndSortToURL(url, "mockDataRegion");
            specimenDetailFilter.addUrlFilters(url, "mockDataRegion");
        }

        // Build a list fo FieldKeys for all the columns that we must select,
        // regardless of whether they're in the selected specimen view.  We need to ask the view which
        // columns are required in case there's a saved filter on a column outside the primary table:
        columns.add(FieldKey.fromParts("Container"));
        columns.add(FieldKey.fromParts("Visit"));
        columns.add(FieldKey.fromParts("SequenceNum"));
        columns.add(FieldKey.fromParts("LockedInRequest"));
        columns.add(FieldKey.fromParts("GlobalUniqueId"));
        columns.add(FieldKey.fromParts(StudyService.get().getSubjectColumnName(container)));
        if (StudyManager.getInstance().showCohorts(container, schema.getUser()))
            columns.add(FieldKey.fromParts("CollectionCohort"));
        columns.add(FieldKey.fromParts("Volume"));
        if (level != null)
        {
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
                columns.add(typeProperty.getTypeKey());
        }

        // turn our fieldkeys into columns:
        Collection<ColumnInfo> cols = new ArrayList<>();
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, columns);
        Set<FieldKey> unresolvedColumns = new HashSet<>();
        cols.addAll(colMap.values());
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, specimenDetailFilter, null, unresolvedColumns);
        if (!unresolvedColumns.isEmpty())
            throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
        // generate our select SQL:
        SQLFragment viewSql = new SQLFragment();
        viewSql.appendComment("<getSpecimenDetailQueryHelper>", tinfo.getSqlDialect());
        viewSql.append(Table.getSelectSQL(tinfo, cols, specimenDetailFilter, null));
        viewSql.appendComment("</getSpecimenDetailQueryHelper>", tinfo.getSqlDialect());

        // save off the aliases for our grouping columns, so we can group by them later:
        String groupingColSql = null;
        if (level != null)
        {
            StringBuilder builder = new StringBuilder();
            String sep = "";
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
            {
                ColumnInfo col = colMap.get(typeProperty.getTypeKey());
                builder.append(sep).append(col.getAlias());
                sep = ", ";
                aliasToTypeProperty.put(col.getAlias(), typeProperty);
            }
            groupingColSql = builder.toString();
        }
        return new SpecimenDetailQueryHelper(viewSql, groupingColSql, aliasToTypeProperty);
    }


    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, SimpleFilter specimenDetailFilter,
            boolean includeParticipantGroups, SpecimenTypeLevel level) throws SQLException
    {
        return getSpecimenSummaryByVisitType(container, user, specimenDetailFilter, includeParticipantGroups, level, null);
    }


    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, SimpleFilter specimenDetailFilter,
        boolean includeParticipantGroups, SpecimenTypeLevel level,
        CustomView baseView) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }

        final SpecimenDetailQueryHelper viewSqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        String perPtidSpecimenSQL = "\t-- Inner SELECT gets the number of vials per participant/visit/type:\n" +
            "\tSELECT InnerView.Container, InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + ",\n" +
            "\tInnerView." + StudyService.get().getSubjectColumnName(container) + ", COUNT(*) AS VialCount, SUM(InnerView.Volume) AS PtidVolume \n" +
            "FROM (\n" + viewSqlHelper.getViewSql().getSQL() + "\n) InnerView\n" +
            "\tGROUP BY InnerView.Container, InnerView." + StudyService.get().getSubjectColumnName(container) +
                ", InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n";

        SQLFragment sql = new SQLFragment("-- Outer grouping allows us to count participants AND sum vial counts:\n" +
            "SELECT VialData.Visit AS Visit, " + viewSqlHelper.getTypeGroupingColumns() + ", COUNT(*) as ParticipantCount, \n" +
            "SUM(VialData.VialCount) AS VialCount, SUM(VialData.PtidVolume) AS TotalVolume FROM \n" +
            "(\n" + perPtidSpecimenSQL + ") AS VialData\n" +
            "GROUP BY Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n" +
            "ORDER BY " + viewSqlHelper.getTypeGroupingColumns() + ", Visit");
        sql.addAll(viewSqlHelper.getViewSql().getParamsArray());

        final List<SummaryByVisitType> ret = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                SummaryByVisitType summary = new SummaryByVisitType();
                if (rs.getObject("Visit") != null)
                    summary.setVisit(rs.getInt("Visit"));
                summary.setTotalVolume(rs.getDouble("TotalVolume"));
                Double vialCount = rs.getDouble("VialCount");
                summary.setVialCount(vialCount.longValue());
                Double participantCount = rs.getDouble("ParticipantCount");
                summary.setParticipantCount(participantCount.longValue());

                for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : viewSqlHelper.getAliasToTypePropertyMap().entrySet())
                {
                    String value = rs.getString(typeProperty.getKey());
                    try
                    {
                        PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                    }
                    catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                ret.add(summary);
            }
        });

        SummaryByVisitType[] summaries = ret.toArray(new SummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(perPtidSpecimenSQL, viewSqlHelper.getViewSql().getParamsArray(),
                    viewSqlHelper.getAliasToTypePropertyMap(), summaries, StudyService.get().getSubjectColumnName(container), "Visit");

        return summaries;
    }

    private String getPtidListKey(Integer visit, String primaryType, String derivativeType, String additiveType)
    {
        return visit + "/" + primaryType + "/" +
            (derivativeType != null ? derivativeType : "all") +
            (additiveType != null ? additiveType : "all");
    }

    public LocationImpl[] getSitesWithRequests(Container container)
    {
        TableInfo locationTableInfo = StudySchema.getInstance().getTableInfoSite(container);
        SQLFragment sql = new SQLFragment("SELECT * FROM " + locationTableInfo.getSelectName() + " WHERE rowid IN\n" +
                "(SELECT destinationsiteid FROM study.samplerequest WHERE container = ?)\n" +
                "AND container = ? ORDER BY label", container.getId(), container.getId());

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getArray(LocationImpl.class);
    }

    public Set<LocationImpl> getEnrollmentSitesWithRequests(Container container, User user)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tableInfoSpecimenDetail = schema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenDetail)
            throw new IllegalStateException("SpecimenDetail table not found.");
        String tableInfoAlias = "Specimen";
        SQLFragment sql = new SQLFragment("SELECT Participant.EnrollmentSiteId FROM ");
        sql.append(tableInfoSpecimenDetail.getFromSQL(tableInfoAlias)).append(", ")
                .append("study.SampleRequestSpecimen AS RequestSpecimen, \n" +
                "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                "study.Participant AS Participant\n" +
                "WHERE Request.Container = Status.Container AND\n" +
                "\tRequest.StatusId = Status.RowId AND\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container AND\n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                "\tParticipant.Container = Specimen.Container AND\n" +
                "\tParticipant.ParticipantId = Specimen.Ptid AND\n" +
                "\tStatus.SpecimensLocked = ? AND\n" +
                "\tRequest.Container = ?");
        sql.add(Boolean.TRUE);
        sql.add(container);

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    public Set<LocationImpl> getEnrollmentSitesWithSpecimens(Container container, User user)
    {
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tableInfoSpecimenDetail = schema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenDetail)
            throw new IllegalStateException("SpecimenDetail table not found.");
        String tableInfoAlias = "Specimen";
        SQLFragment sql = new SQLFragment("SELECT EnrollmentSiteId FROM ");
        sql.append(tableInfoSpecimenDetail.getFromSQL(tableInfoAlias)).append(", study.Participant AS Participant\n" +
                "WHERE Specimen.Ptid = Participant.ParticipantId AND\n" +
                "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                "\tSpecimen.Container = Participant.Container AND\n" +
                "\tSpecimen.Container = ?\n" +
                "GROUP BY EnrollmentSiteId");
        sql.add(container);

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    private Set<LocationImpl> getSitesWithIdSql(final Container container, final String idColumnName, SQLFragment sql)
    {
        final Set<LocationImpl> locations = new TreeSet<>((s1, s2) ->
        {
            if (s1 == null && s2 == null)
                return 0;
            if (s1 == null)
                return -1;
            if (s2 == null)
                return 1;
            return s1.getLabel().compareTo(s2.getLabel());
        });

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                // try getObject first to see if we have a value for our row; getInt will coerce the null to
                // zero, which could (theoretically) be a valid site ID.
                if (rs.getObject(idColumnName) == null)
                    locations.add(null);
                else
                    locations.add(StudyManager.getInstance().getLocation(container, rs.getInt(idColumnName)));
            }
        });

        return locations;
    }


    public static class SummaryByVisitParticipant extends SpecimenCountSummary
    {
        private String _participantId;
        private String _cohort;

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public String getCohort()
        {
            return _cohort;
        }

        public void setCohort(String cohort)
        {
            _cohort = cohort;
        }
    }

    public Collection<SummaryByVisitParticipant> getParticipantSummaryByVisitType(Container container, User user,
                                SimpleFilter specimenDetailFilter, CustomView baseView, CohortFilter.Type cohortType) throws SQLException
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, null);
        String subjectCol = StudyService.get().getSubjectColumnName(container);
        SQLFragment cohortJoinClause = null;
        switch (cohortType)
        {
            case DATA_COLLECTION:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.ParticipantVisit ON\n " +
                        "\tSpecimenQuery.SequenceNum = study.ParticipantVisit.SequenceNum AND\n" +
                        "\tSpecimenQuery." + subjectCol + " = study.ParticipantVisit.ParticipantId AND\n" +
                        "\tSpecimenQuery.Container = study.ParticipantVisit.Container\n" +
                        "LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.ParticipantVisit.CohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.ParticipantVisit.Container = study.Cohort.Container\n");
                break;
            case PTID_CURRENT:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.Participant.CurrentCohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.Participant.Container = study.Cohort.Container\n");
                break;
            case PTID_INITIAL:
                cohortJoinClause = new SQLFragment("LEFT OUTER JOIN study.Cohort ON \n" +
                        "\tstudy.Participant.InitialCohortId = study.Cohort.RowId AND\n" +
                        "\tstudy.Participant.Container = study.Cohort.Container\n");
                break;
        }

        SQLFragment ptidSpecimenSQL = new SQLFragment();
        SqlDialect d = StudySchema.getInstance().getSqlDialect();
        ptidSpecimenSQL.appendComment("<getParticipantSummaryByVisitType>", d);
        ptidSpecimenSQL.append("SELECT SpecimenQuery.Visit AS Visit, SpecimenQuery.").append(subjectCol).append(" AS ParticipantId,\n")
                .append("COUNT(*) AS VialCount, study.Cohort.Label AS Cohort, SUM(SpecimenQuery.Volume) AS TotalVolume\n").append("FROM (");
        ptidSpecimenSQL.append(sqlHelper.getViewSql());
        ptidSpecimenSQL.append(") AS SpecimenQuery\n" + "LEFT OUTER JOIN study.Participant ON\n" + "\tSpecimenQuery.").append(subjectCol).append(" = study.Participant.ParticipantId AND\n").append("\tSpecimenQuery.Container = study.Participant.Container\n");
        ptidSpecimenSQL.append(cohortJoinClause);
        ptidSpecimenSQL.append("GROUP BY study.Cohort.Label, SpecimenQuery.").append(subjectCol).append(", Visit\n").append("ORDER BY study.Cohort.Label, SpecimenQuery.").append(subjectCol).append(", Visit");
        ptidSpecimenSQL.appendComment("</getParticipantSummaryByVisitType>", d);

        return new SqlSelector(StudySchema.getInstance().getSchema(), ptidSpecimenSQL).getCollection(SummaryByVisitParticipant.class);
    }


    public RequestSummaryByVisitType[] getRequestSummaryBySite(Container container, User user, SimpleFilter specimenDetailFilter, boolean includeParticipantGroups, SpecimenTypeLevel level, CustomView baseView, boolean completeRequestsOnly) throws SQLException
    {
        if (specimenDetailFilter == null)
        {
            specimenDetailFilter = new SimpleFilter();
        }
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }

        final SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        TableInfo locationTableInfo = StudySchema.getInstance().getTableInfoSite(container);
        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String sql = "SELECT Specimen.Container,\n" +
                "Specimen." + subjectCol + ",\n" +
                "Request.DestinationSiteId,\n" +
                "Site.Label AS SiteLabel,\n" +
                "Visit AS Visit,\n" +
                 sqlHelper.getTypeGroupingColumns() + ", COUNT(*) AS VialCount, SUM(Volume) AS TotalVolume\n" +
                "FROM (" + sqlHelper.getViewSql().getSQL() + ") AS Specimen\n" +
                "JOIN study.SampleRequestSpecimen AS RequestSpecimen ON \n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container\n" +
                "JOIN study.SampleRequest AS Request ON\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container\n" +
                "JOIN " + locationTableInfo.getSelectName() + " AS Site ON\n" +
                "\tSite.Container = Request.Container AND\n" +
                "\tSite.RowId = Request.DestinationSiteId\n" +
                "JOIN study.SampleRequestStatus AS Status ON\n" +
                "\tStatus.Container = Request.Container AND\n" +
                "\tStatus.RowId = Request.StatusId and Status.SpecimensLocked = ?\n" +
                (completeRequestsOnly ? "\tAND Status.FinalState = ?\n" : "") +
                "GROUP BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit\n" +
                "ORDER BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit";

        Object[] params = new Object[sqlHelper.getViewSql().getParamsArray().length + 1 + (completeRequestsOnly ? 1 : 0)];
        System.arraycopy(sqlHelper.getViewSql().getParamsArray(), 0, params, 0, sqlHelper.getViewSql().getParamsArray().length);
        params[params.length - 1] = Boolean.TRUE;
        if (completeRequestsOnly)
            params[params.length - 2] = Boolean.TRUE;

        SqlDialect d = StudySchema.getInstance().getSqlDialect();
        SQLFragment fragment = new SQLFragment();
        fragment.appendComment("<getRequestSummaryBySite>",d);
        fragment.append(sql);
        fragment.appendComment("</getRequestSummaryBySite>", d);
        fragment.addAll(params);

        final List<RequestSummaryByVisitType> ret = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), fragment).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                RequestSummaryByVisitType summary = new RequestSummaryByVisitType();
                summary.setDestinationSiteId(rs.getInt("DestinationSiteId"));
                summary.setSiteLabel(rs.getString("SiteLabel"));
                summary.setVisit(rs.getInt("Visit"));
                summary.setTotalVolume(rs.getDouble("TotalVolume"));
                Double vialCount = rs.getDouble("VialCount");
                summary.setVialCount(vialCount.longValue());

                for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : sqlHelper.getAliasToTypePropertyMap().entrySet())
                {
                    String value = rs.getString(typeProperty.getKey());

                    try
                    {
                        PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                    }
                    catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
                    {
                        Logger.getLogger(SpecimenManager.class).error(e);
                    }
                }
                ret.add(summary);
            }
        });

        RequestSummaryByVisitType[] summaries = ret.toArray(new RequestSummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(sql, params, null, summaries, subjectCol, "Visit");

        return summaries;
    }

    private static final int GET_COMMENT_BATCH_SIZE = 1000;

    public Map<Vial, SpecimenComment> getSpecimenComments(List<Vial> vials) throws SQLException
    {
        if (vials == null || vials.size() == 0)
            return Collections.emptyMap();

        Container container = vials.get(0).getContainer();
        final Map<Vial, SpecimenComment> result = new HashMap<>();
        int offset = 0;

        while (offset < vials.size())
        {
            final Map<String, Vial> idToVial = new HashMap<>();

            for (int current = offset; current < offset + GET_COMMENT_BATCH_SIZE && current < vials.size(); current++)
            {
                Vial vial = vials.get(current);
                idToVial.put(vial.getGlobalUniqueId(), vial);
                if (!container.equals(vial.getContainer()))
                    throw new IllegalArgumentException("All specimens must be from the same container");
            }

            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addInClause(FieldKey.fromParts("GlobalUniqueId"), idToVial.keySet());

            new TableSelector(StudySchema.getInstance().getTableInfoSpecimenComment(), filter, null).forEach(new Selector.ForEachBlock<SpecimenComment>()
            {
                @Override
                public void exec(SpecimenComment comment) throws SQLException
                {
                    Vial vial = idToVial.get(comment.getGlobalUniqueId());
                    result.put(vial, comment);
                }
            }, SpecimenComment.class);

            offset += GET_COMMENT_BATCH_SIZE;
        }

        return result;
    }

    public SpecimenComment getSpecimenCommentForVial(Container container, String globalUniqueId) throws SQLException
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("GlobalUniqueId"), globalUniqueId);

        return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenComment(), filter, null).getObject(SpecimenComment.class);
    }

    public SpecimenComment getSpecimenCommentForVial(Vial vial) throws SQLException
    {
        return getSpecimenCommentForVial(vial.getContainer(), vial.getGlobalUniqueId());
    }

    public SpecimenComment[] getSpecimenCommentForSpecimen(Container container, String specimenHash) throws SQLException
    {
        return getSpecimenCommentForSpecimens(container, Collections.singleton(specimenHash));
    }

    public SpecimenComment[] getSpecimenCommentForSpecimens(Container container, Collection<String> specimenHashes) throws SQLException
    {
        SimpleFilter hashFilter = SimpleFilter.createContainerFilter(container);
        hashFilter.addInClause(FieldKey.fromParts("SpecimenHash"), specimenHashes);

        return new TableSelector(StudySchema.getInstance().getTableInfoSpecimenComment(), hashFilter, new Sort("GlobalUniqueId")).getArray(SpecimenComment.class);
    }

    private boolean safeComp(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    private void auditSpecimenComment(User user, Vial vial, String oldComment, String newComment, boolean prevConflictState, boolean newConflictState)
    {
        String verb = "updated";
        if (oldComment == null)
            verb = "added";
        else if (newComment == null)
            verb = "deleted";
        String message = "";
        if (!safeComp(oldComment, newComment))
        {
            message += "Comment " + verb + ".\n";
            if (oldComment != null)
                message += "Previous value: " + oldComment + "\n";
            if (newComment != null)
                message += "New value: " + newComment + "\n";
        }

        if (!safeComp(prevConflictState, newConflictState))
        {
            message = "QC alert flag changed.\n";
            if (oldComment != null)
                message += "Previous value: " + prevConflictState + "\n";
            if (newComment != null)
                message += "New value: " + newConflictState + "\n";
        }

        SpecimenCommentAuditProvider.SpecimenCommentAuditEvent event = new SpecimenCommentAuditProvider.SpecimenCommentAuditEvent(vial.getContainer().getId(), message);
        event.setVialId(vial.getGlobalUniqueId());

        AuditLogService.get().addEvent(user, event);
    }

    public SpecimenComment setSpecimenComment(User user, Vial vial, String commentText, boolean qualityControlFlag, boolean qualityControlFlagForced) throws SQLException
    {
        TableInfo commentTable = StudySchema.getInstance().getTableInfoSpecimenComment();
        DbScope scope = commentTable.getSchema().getScope();
        SpecimenComment comment = getSpecimenCommentForVial(vial);
        boolean clearComment = commentText == null && !qualityControlFlag && !qualityControlFlagForced;
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SpecimenComment result;
            if (clearComment)
            {
                if (comment != null)
                {
                    Table.delete(commentTable, comment.getRowId());
                    auditSpecimenComment(user, vial, comment.getComment(), null, comment.isQualityControlFlag(), false);
                }
                result = null;
            }
            else
            {
                if (comment != null)
                {
                    String prevComment = comment.getComment();
                    boolean prevConflictState = comment.isQualityControlFlag();
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeUpdate(user);
                    result = Table.update(user, commentTable, comment, comment.getRowId());
                    auditSpecimenComment(user, vial, prevComment, result.getComment(), prevConflictState, result.isQualityControlFlag());
                }
                else
                {
                    comment = new SpecimenComment();
                    comment.setGlobalUniqueId(vial.getGlobalUniqueId());
                    comment.setSpecimenHash(vial.getSpecimenHash());
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeInsert(user, vial.getContainer().getId());
                    result = Table.insert(user, commentTable, comment);
                    auditSpecimenComment(user, vial, null, result.getComment(), false, comment.isQualityControlFlag());
                }
            }
            transaction.commit();
            return result;
        }
    }

    private void setSummaryParticipantGroups(String sql, Object[] paramArray, final Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty,
                                           SummaryByVisitType[] summaries, final String ptidColumnName, final String visitValueColumnName) throws SQLException
    {
        SQLFragment fragment = new SQLFragment(sql);
        fragment.addAll(paramArray);

        final Map<String, Set<String>> cellToPtidSet = new HashMap<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), fragment).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String ptid = rs.getString(ptidColumnName);
                Integer visit = rs.getInt(visitValueColumnName);
                String primaryType = null;
                String derivative = null;
                String additive = null;

                for (Map.Entry<String, SpecimenTypeBeanProperty> entry : aliasToTypeProperty.entrySet())
                {
                    switch (entry.getValue().getLevel())
                    {
                        case PrimaryType:
                            primaryType = rs.getString(entry.getKey());
                            break;
                        case Derivative:
                            derivative = rs.getString(entry.getKey());
                            break;
                        case Additive:
                            additive = rs.getString(entry.getKey());
                            break;
                    }
                }

                String key = getPtidListKey(visit, primaryType, derivative, additive);

                Set<String> ptids = cellToPtidSet.get(key);
                if (ptids == null)
                {
                    ptids = new TreeSet<>();
                    cellToPtidSet.put(key, ptids);
                }
                ptids.add(ptid != null ? ptid : "[unknown]");
            }
        });

        for (SummaryByVisitType summary : summaries)
        {
            Integer visit = summary.getVisit();
            String key = getPtidListKey(visit, summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive());
            Set<String> ptids = cellToPtidSet.get(key);
            summary.setParticipantIds(ptids);
        }
    }

    private class GroupedValueColumnHelper
    {
        private String _viewColumnName;
        private String _sqlColumnName;
        private String _urlFilterName;
        private String _joinColumnName;

        public GroupedValueColumnHelper(String sqlColumnName, String viewColumnName, String urlFilterName, String joinColumnName)
        {
            _sqlColumnName = sqlColumnName;
            _viewColumnName = viewColumnName;
            _urlFilterName = urlFilterName;
            _joinColumnName = joinColumnName;
        }

        public String getViewColumnName()
        {
            return _viewColumnName;
        }

        public String getSqlColumnName()
        {
            return _sqlColumnName;
        }

        public String getUrlFilterName()
        {
            return _urlFilterName;
        }

        public String getJoinColumnName()
        {
            return _joinColumnName;
        }

        public FieldKey getFieldKey()
        {
            // constructs FieldKey whether it needs join or not
            if (null == _joinColumnName)
                return FieldKey.fromString(_viewColumnName);
            return FieldKey.fromParts(_sqlColumnName, _joinColumnName);
        }
    }

    // Map "ViewColumnName" name to object with sql column name and url filter name
    private final Map<String, GroupedValueColumnHelper> _groupedValueAllowedColumnMap = new HashMap<>();

    private void initGroupedValueAllowedColumnMap()
    {                                                                                       //    sqlColumnName    viewColumnName   urlFilterName          joinColumnName
        _groupedValueAllowedColumnMap.put("Primary Type",           new GroupedValueColumnHelper("PrimaryTypeId", "PrimaryType", "PrimaryType/Description", "PrimaryType"));
        _groupedValueAllowedColumnMap.put("Derivative Type",        new GroupedValueColumnHelper("DerivativeTypeId", "DerivativeType", "DerivativeType/Description",  "Derivative"));
        _groupedValueAllowedColumnMap.put("Additive Type",          new GroupedValueColumnHelper("AdditiveTypeId", "AdditiveType", "AdditiveType/Description",  "Additive"));
        _groupedValueAllowedColumnMap.put("Derivative Type2",       new GroupedValueColumnHelper("DerivativeTypeId2", "DerivativeType2", "DerivativeType2/Description",  "Derivative"));
        _groupedValueAllowedColumnMap.put("Sub Additive Derivative",new GroupedValueColumnHelper("SubAdditiveDerivative", "SubAdditiveDerivative", "SubAdditiveDerivative", null));
        _groupedValueAllowedColumnMap.put("Clinic",                 new GroupedValueColumnHelper("originatinglocationid", "Clinic", "Clinic/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Processing Location",    new GroupedValueColumnHelper("ProcessingLocation", "ProcessingLocation", "ProcessingLocation/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Protocol Number",        new GroupedValueColumnHelper("ProtocolNumber", "ProtocolNumber", "ProtocolNumber", null));
        _groupedValueAllowedColumnMap.put("Tube Type",              new GroupedValueColumnHelper("TubeType", "TubeType", "TubeType", null));
        _groupedValueAllowedColumnMap.put("Site Name",              new GroupedValueColumnHelper("CurrentLocation", "SiteName", "SiteName/Label", "Label"));
        _groupedValueAllowedColumnMap.put("Available",              new GroupedValueColumnHelper("Available", "Available", "Available", null));
        _groupedValueAllowedColumnMap.put("Freezer",                new GroupedValueColumnHelper("Freezer", "Freezer", "Freezer", null));
        _groupedValueAllowedColumnMap.put("Fr Container",           new GroupedValueColumnHelper("Fr_Container", "Fr_Container", "Fr_Container", null));
        _groupedValueAllowedColumnMap.put("Fr Position",            new GroupedValueColumnHelper("Fr_Position", "Fr_Position", "Fr_Position", null));
        _groupedValueAllowedColumnMap.put("Fr Level1",              new GroupedValueColumnHelper("Fr_Level1", "Fr_Level1", "Fr_Level1", null));
        _groupedValueAllowedColumnMap.put("Fr Level2",              new GroupedValueColumnHelper("Fr_Level2", "Fr_Level2", "Fr_Level2", null));
    }

    public Map<String, GroupedValueColumnHelper> getGroupedValueAllowedMap()
    {
        return _groupedValueAllowedColumnMap;
    }

    public String[] getGroupedValueAllowedColumns()
    {
        Set<String> keySet = _groupedValueAllowedColumnMap.keySet();
        String[] allowedColumns = keySet.toArray(new String[keySet.size()]);
        Arrays.sort(allowedColumns, new ComparableComparator<>());
        return allowedColumns;
    }

    private class GroupedValueFilter
    {
        private String _viewColumnName;
        private String _filterValueName;

        public GroupedValueFilter()
        {
        }

        public String getViewColumnName()
        {
            return _viewColumnName;
        }

        public String getFilterValueName()
        {
            return _filterValueName;
        }

        public void setFilterValueName(String filterValueName)
        {
            _filterValueName = filterValueName;
        }

        public void setViewColumnName(String viewColumnName)
        {
            _viewColumnName = viewColumnName;
        }
    }

    private DatabaseCache<Map<String, Map<String, Object>>> _groupedValuesCache = null;
    private class GroupedResults
    {
        public String viewName;
        public String urlFilterName;
        public String labelValue;
        public long count;
        public Map<String, GroupedResults> childGroupedResultsMap;
    }

    private static String getGroupedValuesCacheKey(Container container)
    {
        return container.getId();
    }

    public void clearGroupedValuesForColumn(Container container)
    {
        if (null == _groupedValuesCache)
            return;

        String cacheKey = getGroupedValuesCacheKey(container);
        _groupedValuesCache.remove(cacheKey);
    }

    @NotNull
    public Map<String, Map<String, Object>> getGroupedValuesForColumn(Container container, User user, ArrayList<String[]> groupings)
    {
        // ColumnName and filter names are "QueryView" names; map them to actual table names before building query
        Map<String, Map<String, Object>> groupedValues = Collections.emptyMap();
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            return groupedValues;

        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(container), user, true);
        TableInfo tableInfo = schema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        String cacheKey = getGroupedValuesCacheKey(container);
        if (null != _groupedValuesCache)
        {
            groupedValues = _groupedValuesCache.get(cacheKey);
            if (null != groupedValues)
                return groupedValues;
        }
        else
        {
            _groupedValuesCache = new DatabaseCache<>(
                    StudySchema.getInstance().getSchema().getScope(), 10, 8 * CacheManager.HOUR, "Grouped Values Cache");
        }

        TableResultSet resultSet;
        try
        {
            groupedValues = new HashMap<>();
            QueryService queryService = QueryService.get();
            for (String[] grouping : groupings)
            {
                List<FieldKey> fieldKeys = new ArrayList<>();
                for (String aGrouping : grouping)
                {
                    if (!StringUtils.isNotBlank(aGrouping))
                        break;      // Grouping may have null/blank entries for groupBys that are not chosen to be used
                    GroupedValueColumnHelper columnHelper = getGroupedValueAllowedMap().get(aGrouping);
                    FieldKey fieldKey = columnHelper.getFieldKey();
                    fieldKeys.add(fieldKey);
                }

                if (fieldKeys.isEmpty())
                    continue;               // Nothing specified for grouping

                // Basic SQL with joins
                Map<FieldKey, ColumnInfo> columnMap = queryService.getColumns(tableInfo, fieldKeys);

                SQLFragment sql = queryService.getSelectSQL(tableInfo, columnMap.values(), null, null, -1, 0, false);

                // Insert COUNT
                String sampleCountName = StudySchema.getInstance().getSqlDialect().makeLegalIdentifier("SampleCount");
                String countStr = " COUNT(*) As " + sampleCountName + ",\n";
                int insertIndex = sql.indexOf("SELECT");
                sql.insert(insertIndex + 6, countStr);

                sql.append("GROUP BY ");
                boolean firstGroupBy = true;
                for (ColumnInfo columnInfo : columnMap.values())
                {
                    if (!firstGroupBy)
                        sql.append(", ");
                    firstGroupBy = false;
                    sql.append(columnInfo.getValueSql(tableInfo.getTitle()));
                }

                sql.append("\nORDER BY ");
                boolean firstOrderBy = true;
                for (ColumnInfo columnInfo : columnMap.values())
                {
                    if (!firstOrderBy)
                        sql.append(", ");
                    firstOrderBy = false;
                    sql.append(columnInfo.getValueSql(tableInfo.getTitle()));
                }

                SqlSelector selector = new SqlSelector(tableInfo.getSchema(), sql);

                resultSet = selector.getResultSet();
                try
                {
                    if (null != resultSet)
                    {
                        // The result set is grouped by all levels together, so at the upper levels, we have to group ourselves
                        // Build a tree of GroupedResultsMaps, one level for each grouping level
                        //
                        Map<String, GroupedResults> groupedResultsMap = new HashMap<>();
                        while (resultSet.next())
                        {
                            Map<String, Object> rowMap = resultSet.getRowMap();
                            long count = 0;
                            Object countObject = rowMap.get(sampleCountName);
                            if (countObject instanceof Long)
                                count = (Long)countObject;
                            else if (countObject instanceof Integer)
                                count = (Integer)countObject;

                            Map<String, GroupedResults> currentGroupedResultsMap = groupedResultsMap;

                            for (int i = 0; i < grouping.length; i += 1)
                            {
                                if (!StringUtils.isNotBlank(grouping[i]))
                                    break;      // Grouping may have null entries for groupBys that are not chosen to be used

                                GroupedValueColumnHelper columnHelper = getGroupedValueAllowedMap().get(grouping[i]);
                                ColumnInfo columnInfo = columnMap.get(columnHelper.getFieldKey());
                                Object value = rowMap.get(columnInfo.getAlias());
                                String labelValue = (null != value) ? value.toString() : null;
                                GroupedResults groupedResults = currentGroupedResultsMap.get(labelValue);
                                if (null == groupedResults)
                                {
                                    groupedResults = new GroupedResults();
                                    groupedResults.viewName = grouping[i];
                                    groupedResults.urlFilterName = columnHelper.getUrlFilterName();
                                    groupedResults.labelValue = labelValue;
                                    groupedResults.childGroupedResultsMap = new HashMap<>();
                                    currentGroupedResultsMap.put(labelValue, groupedResults);
                                }
                                groupedResults.count += count;
                                currentGroupedResultsMap = groupedResults.childGroupedResultsMap;
                            }
                        }

                        Map<String, Object> groupedValue;
                        if (!groupedResultsMap.isEmpty())
                        {
                            groupedValue = buildGroupedValue(groupedResultsMap, container, new ArrayList<GroupedValueFilter>());
                        }
                        else
                        {
                            groupedValue = new HashMap<>(2);
                            groupedValue.put("name", grouping[0]);
                            groupedValue.put("values", new ArrayList<Map<String, Object>>());
                        }
                        groupedValues.put(grouping[0], groupedValue);
                    }
                }
                finally
                {
                    if (null != resultSet)
                        resultSet.close();
                }
            }

            _groupedValuesCache.put(cacheKey, groupedValues);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        return groupedValues;
    }

    private Map<String, Object> buildGroupedValue(Map<String, GroupedResults> groupedResultsMap, Container container, List<GroupedValueFilter> groupedValueFilters)
    {
        String viewName = null;
        ArrayList<Map<String, Object>> groupedValues = new ArrayList<>();
        for (GroupedResults groupedResults : groupedResultsMap.values())
        {
            viewName = groupedResults.viewName;             // They are all the same in this collection
            Map<String, Object> groupedValue = new HashMap<>(5);
            groupedValue.put("label", (null != groupedResults.labelValue) ? groupedResults.labelValue : "[empty]");
            groupedValue.put("count", groupedResults.count);
            groupedValue.put("url", getURL(container, groupedResults.urlFilterName, groupedValueFilters, groupedResults.labelValue));
            Map<String, GroupedResults> childGroupResultsMap = groupedResults.childGroupedResultsMap;
            if (null != childGroupResultsMap && !childGroupResultsMap.isEmpty())
            {
                GroupedValueFilter groupedValueFilter = new GroupedValueFilter();
                groupedValueFilter.setViewColumnName(groupedResults.viewName);
                groupedValueFilter.setFilterValueName(null != groupedResults.labelValue ? groupedResults.labelValue : null);
                List<GroupedValueFilter> groupedValueFiltersCopy = new ArrayList<>(groupedValueFilters); // Need copy because can't share across members of groupedResultsMap
                groupedValueFiltersCopy.add(groupedValueFilter);
                Map<String, Object> nextLevelGroup = buildGroupedValue(childGroupResultsMap, container, groupedValueFiltersCopy);
                groupedValue.put("group", nextLevelGroup);

            }
            groupedValues.add(groupedValue);
        }

        groupedValues.sort((o, o1) ->
        {
            String str = (String) o.get("label");
            String str1 = (String) o1.get("label");
            if (null == str)
            {
                if (null == str1)
                    return 0;
                else
                    return 1;
            }
            else if (null == str1)
                return -1;
            return (str.compareTo(str1));
        });

        Map<String, Object> groupedValue = new HashMap<>(2);
        groupedValue.put("name", viewName);
        groupedValue.put("values", groupedValues);
        return groupedValue;
    }

    private ActionURL getURL(Container container, String groupColumnName, List<GroupedValueFilter> filterNamesAndValues, String label)
    {
        ActionURL url = new ActionURL(SpecimenController.SamplesAction.class, container);
        addFilterParameter(url, groupColumnName, label);
        for (GroupedValueFilter filterColumnAndValue : filterNamesAndValues)
            addFilterParameter(url, getGroupedValueAllowedMap().get(filterColumnAndValue.getViewColumnName()).getUrlFilterName(), filterColumnAndValue.getFilterValueName());
        url.addParameter("showVials", "true");
        return url;
    }

    private void addFilterParameter(ActionURL url, String urlColumnName, String label)
    {
        url.addParameter("SpecimenDetail." + urlColumnName + "~eq", label);
    }

    @Nullable
    public ExtendedSpecimenRequestView getExtendedSpecimenRequestView(ViewContext context)
    {
        if (context == null || context.getContainer() == null)
            return null;

        Path path = ModuleHtmlView.getStandardPath("extendedrequest");

        for (Module module : context.getContainer().getActiveModules())
        {
            if (ModuleHtmlView.exists(module, path))
            {
                ModuleHtmlView moduleView = ModuleHtmlView.get(module, path);
                assert null != moduleView;
                String html = moduleView.getHtml();
                html = ModuleHtmlView.replaceTokens(html, context);
                return ExtendedSpecimenRequestView.createView(html);
            }
        }

        return null;
    }


    public List<Vial> getVials(final Container container, final User user, SimpleFilter filter)
    {
        // TODO: LinkedList?
        final List<Vial> vials = new ArrayList<>();

        getSpecimensSelector(container, user, filter)
            .forEachMap(map -> vials.add(new Vial(container, map)));

        return vials;
    }


    public TableSelector getSpecimensSelector(final Container container, final User user, SimpleFilter filter)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
        {
            throw new NotFoundException("No study in container " + container.getPath());
        }
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, user, true);
        TableInfo specimenTable = schema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        return new TableSelector(specimenTable, filter, null);
    }
}
