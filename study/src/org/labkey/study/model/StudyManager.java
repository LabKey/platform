/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.*;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.study.QueryHelper;
import org.labkey.study.SampleManager;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.visitmanager.AbsoluteDateVisitManager;
import org.labkey.study.visitmanager.RelativeDateVisitManager;
import org.labkey.study.visitmanager.SequenceVisitManager;
import org.labkey.study.visitmanager.VisitManager;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;


public class StudyManager
{
    public static final SearchService.SearchCategory datasetCategory = new SearchService.SearchCategory("dataset", "Study Dataset");
    public static final SearchService.SearchCategory subjectCategory = new SearchService.SearchCategory("subject", "Study Subject");
    public static final SearchService.SearchCategory assayCategory = new SearchService.SearchCategory("assay", "Study Assay");

    private static final Logger _log = Logger.getLogger(StudyManager.class);
    private static StudyManager _instance;
    private static final String SCHEMA_NAME = "study";
    private final TableInfo _tableInfoVisitMap;
    private final TableInfo _tableInfoParticipant;
    private final TableInfo _tableInfoStudyData;
    private final TableInfo _tableInfoUploadLog;

    private final QueryHelper<StudyImpl> _studyHelper;
    private final QueryHelper<VisitImpl> _visitHelper;
    private final QueryHelper<SiteImpl> _siteHelper;
    private final DataSetHelper _dataSetHelper;
    private final QueryHelper<CohortImpl> _cohortHelper;

    private static final String LSID_REQUIRED = "LSID_REQUIRED";


    private StudyManager()
    {
        // prevent external construction with a private default constructor
        _studyHelper = new QueryHelper<StudyImpl>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoStudy();
                }
            }, StudyImpl.class);

        _visitHelper = new QueryHelper<VisitImpl>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoVisit();
                }
            }, VisitImpl.class);

        _siteHelper = new QueryHelper<SiteImpl>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoSite();
                }
            }, SiteImpl.class);

        _cohortHelper = new QueryHelper<CohortImpl>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoCohort();
                }
            }, CohortImpl.class);

        TableInfoGetter dataSetGetter = new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoDataSet();
            }
        };

        /* Whenever we explicitly invalidate a dataset, unmaterialize it as well
         * this is probably a little overkill, e.g. name change doesn't need to unmaterialize
         * however, this is the best choke point
         */
        _dataSetHelper = new DataSetHelper(dataSetGetter);
        _tableInfoVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
        _tableInfoParticipant = StudySchema.getInstance().getTableInfoParticipant();
        _tableInfoStudyData = StudySchema.getInstance().getTableInfoStudyData();
        _tableInfoUploadLog = StudySchema.getInstance().getTableInfoUploadLog();
    }

    class DataSetHelper extends QueryHelper<DataSetDefinition>
    {
        DataSetHelper(TableInfoGetter tableGetter)
        {
            super(tableGetter, DataSetDefinition.class);
        }

        private final Map<Container, PropertyDescriptor[]> sharedProperties = new HashMap<Container, PropertyDescriptor[]>();

        public PropertyDescriptor[] getSharedProperties(Container c) throws SQLException
        {
            PropertyDescriptor[] pds = sharedProperties.get(c);
            if (pds == null)
            {
                Container sharedContainer = ContainerManager.getSharedContainer();
                assert c != sharedContainer;

                Set<PropertyDescriptor> set = new LinkedHashSet<PropertyDescriptor>();
                DataSetDefinition[] defs = get(c);
                if (defs == null)
                {
                    pds = new PropertyDescriptor[0];
                }
                else
                {
                    for (DataSetDefinition def : defs)
                    {
                        Domain domain = def.getDomain();
                        if (domain == null)
                            continue;

                        for (DomainProperty dp : domain.getProperties())
                            if (dp.getContainer().equals(sharedContainer))
                                set.add(dp.getPropertyDescriptor());
                    }

                    pds = set.toArray(new PropertyDescriptor[set.size()]);
                }

                sharedProperties.put(c, pds);
            }

            return pds;
        }

        public void clearProperties(DataSetDefinition def)
        {
            sharedProperties.remove(def.getContainer());
        }

        @Override
        public void clearCache(DataSetDefinition def)
        {
            super.clearCache(def);
            def.unmaterialize();
            clearProperties(def);
        }
    }


    public static synchronized StudyManager getInstance()
    {
        if (_instance == null)
            _instance = new StudyManager();
        return _instance;
    }


    public static String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public synchronized StudyImpl getStudy(Container c)
    {
        try
        {
            StudyImpl study;
            boolean retry = true;

            while (true)
            {
                StudyImpl[] studies = _studyHelper.get(c);
                if (studies == null || studies.length == 0)
                    return null;
                else if (studies.length > 1)
                    throw new IllegalStateException("Only one study is allowed per container");
                else
                    study = studies[0];

                // UNDONE: There is a subtle bug in QueryHelper caching, cached objects shouldn't hold onto Container objects
                assert(study.getContainer().getId().equals(c.getId()));
                if (study.getContainer() == c)
                    break;

                if (!retry) // we only get one retry
                    break;

                _studyHelper.clearCache(study);
                retry = false;
            }

            // upgrade checks
            if (null == study.getEntityId() || c.getId().equals(study.getEntityId()))
            {
                study.setEntityId(GUID.makeGUID());
                updateStudy(null, study);
            }

            return study;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public StudyImpl[] getAllStudies() throws SQLException
    {
        return Table.select(StudySchema.getInstance().getTableInfoStudy(), Table.ALL_COLUMNS, null, null, StudyImpl.class);
    }

    @NotNull
    public Study[] getAllStudies(Container root, User user) throws SQLException
    {
        FilteredTable t = new FilteredTable(StudySchema.getInstance().getTableInfoStudy(), root, new ContainerFilter.CurrentAndSubfolders(user));
        t.wrapAllColumns(true);
        return Table.select(t, Table.ALL_COLUMNS, null, null, StudyImpl.class);
    }

    public StudyImpl createStudy(User user, StudyImpl study) throws SQLException
    {
        assert null != study.getContainer();
        if (study.getLsid() == null)
            study.initLsid();
        study = _studyHelper.create(user, study);

        //note: we no longer copy the container's policy to the study upon creation
        //instead, we let it inherit the container's policy until the security type
        //is changed to one of the advanced options. 

        return study;
    }

    public void updateStudy(User user, StudyImpl study) throws SQLException
    {
        Study oldStudy = getStudy(study.getContainer());
        Date oldStartDate = oldStudy.getStartDate();
        _studyHelper.update(user, study, new Object[] { study.getContainer() });

        if (oldStudy.getTimepointType() == TimepointType.DATE && !study.getStartDate().equals(oldStartDate))
        {
            // start date has changed, and datasets may use that value. Uncache.
            RelativeDateVisitManager visitManager = (RelativeDateVisitManager) getVisitManager(study);
            visitManager.recomputeDates(oldStartDate, user);
            clearCaches(study.getContainer(), true);
        }
        else
        {
            // Need to get rid of any old copies of the study
            clearCaches(study.getContainer(), false);
        }
    }

    public void createDataSetDefinition(User user, Container container, int dataSetId) throws SQLException
    {
        String name = Integer.toString(dataSetId);
        createDataSetDefinition(user, new DataSetDefinition(getStudy(container), dataSetId, name, name, null, null));
    }

    public void createDataSetDefinition(User user, DataSetDefinition dataSetDefinition) throws SQLException
    {
        if (dataSetDefinition.getDataSetId() <= 0)
            throw new IllegalArgumentException("datasetId must be greater than zero.");
        _dataSetHelper.create(user, dataSetDefinition);
        reindex(dataSetDefinition.getContainer());
    }

    public void updateDataSetDefinition(User user, DataSetDefinition dataSetDefinition) throws SQLException
    {
        boolean fTransaction=false;
        DbScope scope = getSchema().getScope();

        try
        {
            if (!scope.isTransactionActive())
            {
                scope.beginTransaction();
                fTransaction=true;
            }
            DataSet old = getDataSetDefinition(dataSetDefinition.getStudy(), dataSetDefinition.getDataSetId());
            if (null == old)
                throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);
            Object[] pk = new Object[]{dataSetDefinition.getContainer().getId(), dataSetDefinition.getDataSetId()};
            _dataSetHelper.update(user, dataSetDefinition, pk);

            if (!old.getLabel().equals(dataSetDefinition.getLabel()))
            {
                QueryService.get().updateCustomViewsAfterRename(dataSetDefinition.getContainer(), StudyQuerySchema.SCHEMA_NAME,
                        old.getLabel(), dataSetDefinition.getLabel());
            }
            if (fTransaction)
            {
                scope.commitTransaction();
                fTransaction = false;
            }
        }
        finally
        {
            if (fTransaction)
                scope.rollbackTransaction();
        }
        reindex(dataSetDefinition.getContainer());
    }

    public boolean isDataUniquePerParticipant(DataSet dataSet) throws SQLException
    {
        String sql = "SELECT max(n) FROM (select count(*) AS n from study.studydata WHERE container=? AND datasetid=? GROUP BY participantid) x";
        Integer maxCount = Table.executeSingleton(getSchema(), sql, new Object[] {dataSet.getContainer().getId(), dataSet.getDataSetId()}, Integer.class);
        return maxCount == null || maxCount.intValue() <= 1;
    }

    public static class VisitCreationException extends RuntimeException
    {
        public VisitCreationException(String message)
        {
            super(message);
        }
    }

    public int createVisit(Study study, User user, VisitImpl visit) throws SQLException
    {
        if (visit.getContainer() != null && !visit.getContainer().getId().equals(study.getContainer().getId()))
            throw new VisitCreationException("Visit container does not match study");
        visit.setContainer(study.getContainer());

        if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            throw new VisitCreationException("SequenceNumMin must be less than or equal to SequenceNumMax");
        VisitImpl[] visits = getVisits(study, Visit.Order.SEQUENCE_NUM);

        int prevDisplayOrder = 0;
        int prevChronologicalOrder = 0;

        for (VisitImpl existingVisit : visits)
        {
            if (existingVisit.getSequenceNumMin() < visit.getSequenceNumMin())
            {
                prevChronologicalOrder = existingVisit.getChronologicalOrder();
                prevDisplayOrder = existingVisit.getDisplayOrder();
            }

            if (existingVisit.getSequenceNumMin() > existingVisit.getSequenceNumMax())
                throw new VisitCreationException("Corrupt existing visit " + existingVisit.getLabel() +
                        ": SequenceNumMin must be less than or equal to SequenceNumMax");
            boolean disjoint = visit.getSequenceNumMax() < existingVisit.getSequenceNumMin() ||
                               visit.getSequenceNumMin() > existingVisit.getSequenceNumMax();
            if (!disjoint)
                throw new VisitCreationException("New visit " + visit.getLabel() + " overlaps existing visit " + existingVisit.getLabel());
        }

        // if our visit doesn't have a display order or chronological order set, but the visit before our new visit
        // (based on sequencenum) does, then assign the previous visit's order info to our new visit.  This won't always
        // be exactly right, but it's better than having all newly created visits appear at the beginning of the display
        // and chronological lists:
        if (visit.getDisplayOrder() == 0 && prevDisplayOrder > 0)
            visit.setDisplayOrder(prevDisplayOrder);
        if (visit.getChronologicalOrder() == 0 && prevChronologicalOrder > 0)
            visit.setChronologicalOrder(prevChronologicalOrder);

        visit = _visitHelper.create(user, visit);

        if (visit.getRowId() == 0)
            throw new VisitCreationException("Visit rowId has not been set properly");

        return visit.getRowId();
    }

    public void ensureVisit(Study study, User user, double visitId, Visit.Type type, String label) throws SQLException
    {
        VisitImpl[] visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        for (VisitImpl visit : visits)
        {
            // check to see if our new visitId is within the range of an existing visit:
            if (visit.getSequenceNumMin() <= visitId && visit.getSequenceNumMax() >= visitId)
                return;
        }
        createVisit(study, user, visitId,  type, label);
    }


    public void createVisit(Study study, User user, double visitId, Visit.Type type, String label) throws SQLException
    {
        VisitImpl visit = new VisitImpl(study.getContainer(), visitId, label, type);
        createVisit(study, user, visit);
    }

    public void createCohort(Study study, User user, CohortImpl cohort) throws SQLException
    {
        if (cohort.getContainer() != null && !cohort.getContainer().getId().equals(study.getContainer().getId()))
            throw new IllegalArgumentException("Cohort container does not match study");
        cohort.setContainer(study.getContainer());

        // Lsid requires the row id, which does not get created until this object has been inserted into the db
        if (cohort.getLsid() != null)
            throw new IllegalStateException("Attempt to create a new cohort with lsid already set");
        cohort.setLsid(LSID_REQUIRED);
        cohort = _cohortHelper.create(user, cohort);

        if (cohort.getRowId() == 0)
            throw new IllegalStateException("Cohort rowId has not been set properly");

        cohort.initLsid();
        _cohortHelper.update(user, cohort);
    }


    public void deleteVisit(StudyImpl study, VisitImpl visit, User user) throws SQLException
    {
        StudySchema schema = StudySchema.getInstance();
        try
        {
            schema.getSchema().getScope().beginTransaction();

            SQLFragment data = new SQLFragment();
            data.append("SELECT LSID FROM " + schema.getTableInfoStudyData() + " WHERE Container=? AND SequenceNum BETWEEN ? AND ?");
            data.add(study.getContainer().getId());
            data.add(visit.getSequenceNumMin());
            data.add(visit.getSequenceNumMax());
            OntologyManager.deleteOntologyObjects(schema.getSchema(), data, study.getContainer(), false);
            Table.execute(schema.getSchema(),
                    "DELETE FROM " + schema.getTableInfoStudyData() + "\n" +
                    "WHERE Container=? AND SequenceNum BETWEEN ? AND ?",
                    new Object[] {study.getContainer().getId(),visit.getSequenceNumMin(),visit.getSequenceNumMax()});
            Table.execute(schema.getSchema(),
                    "DELETE FROM " + schema.getTableInfoParticipantVisit() + "\n" +
                    "WHERE Container = ? and VisitRowId = ?",
                    new Object[] {study.getContainer().getId(), visit.getRowId()});

            Table.execute(schema.getSchema(), "DELETE FROM " + schema.getTableInfoVisitMap() + "\n" +
                    "WHERE Container=? AND VisitRowId=?",
                    new Object[] {study.getContainer().getId(), visit.getRowId()});
            // UNDONE broken _visitHelper.delete(visit);
            Table.delete(schema.getTableInfoVisit(), new Object[] {study.getContainer(), visit.getRowId()});
            _visitHelper.clearCache(visit);

            SampleManager.getInstance().deleteSamplesForVisit(visit);

            schema.getSchema().getScope().commitTransaction();

            for (DataSetDefinition def : getDataSetDefinitions(study))
            {
                def.unmaterialize();
                StudyManager.fireDataSetChanged(def);
            }

            getVisitManager(study).updateParticipantVisits(user, study.getDataSets());
        }
        finally
        {
            if (schema.getSchema().getScope().isTransactionActive())
                schema.getSchema().getScope().rollbackTransaction();
        }
    }


    public void updateVisit(User user, VisitImpl visit) throws SQLException
    {
        Object[] pk = new Object[]{visit.getContainer().getId(), visit.getRowId()};
        _visitHelper.update(user, visit, pk);
    }

    public void updateCohort(User user, CohortImpl cohort) throws SQLException
    {
        _cohortHelper.update(user, cohort);
    }

    public void updateParticipant(User user, Participant participant) throws SQLException
    {
        Table.update(user,
                _tableInfoParticipant,
                participant,
                new Object[] {participant.getContainer().getId(), participant.getParticipantId()}
        );
    }


    public SiteImpl[] getSites(Container container)
    {
        try
        {
            return _siteHelper.get(container, "Label");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public SiteImpl getSite(Container container, int id) throws SQLException
    {
        return _siteHelper.get(container, id);
    }

    public void createSite(User user, SiteImpl site) throws SQLException
    {
        _siteHelper.create(user, site);
    }

    public void updateSite(User user, SiteImpl site) throws SQLException
    {
        _siteHelper.update(user, site);
    }

    public void createVisitDataSetMapping(User user, Container container, int visitId,
                                          int dataSetId, boolean isRequired) throws SQLException
    {
        VisitDataSet vds = new VisitDataSet(container, dataSetId, visitId, isRequired);
        Table.insert(user, _tableInfoVisitMap, vds);
    }

    public VisitDataSet getVisitDataSetMapping(Container container, int visitRowId,
                                               int dataSetId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addCondition("VisitRowId", visitRowId);
        filter.addCondition("DataSetId", dataSetId);
        ResultSet rs = Table.select(_tableInfoVisitMap, Table.ALL_COLUMNS,
                filter, null);

        VisitDataSet vds = null;
        if (rs.next())
        {
            boolean required = rs.getBoolean("Required");
            vds = new VisitDataSet(container, dataSetId, visitRowId, required);
        }
        rs.close();
        return vds;
    }


    public VisitImpl[] getVisits(Study study, Visit.Order order)
    {
        return getVisits(study, null, null, order);
    }

    private VisitImpl[] EMPTY_VISIT_ARRAY = new VisitImpl[0];

    public VisitImpl[] getVisits(Study study, CohortImpl cohort, User user, Visit.Order order)
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return EMPTY_VISIT_ARRAY;

        try
        {
            SimpleFilter filter = null;
            if (cohort != null)
            {
                filter = new SimpleFilter("Container", study.getContainer().getId());
                if (showCohorts(study.getContainer(), user))
                    filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
            }
            return _visitHelper.get(study.getContainer(), filter, order.getSortColumns());
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void clearVisitCache(Study study)
    {
        _visitHelper.clearCache(study.getContainer());
    }


    public VisitImpl getVisitForRowId(Study study, int rowId)
    {
        try
        {
            return _visitHelper.get(study.getContainer(), rowId, "RowId");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private String getQCStateCacheName(Container container)
    {
        return container.getId() + "/" + QCState.class.toString();
    }
    
    public QCState[] getQCStates(Container container)
    {
        try
        {
            QCState[] states = (QCState[]) DbCache.get(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(container));

            if (states == null)
            {
                SimpleFilter filter = new SimpleFilter("Container", container);
                states = Table.select(StudySchema.getInstance().getTableInfoQCState(), Table.ALL_COLUMNS, filter, new Sort("Label"), QCState.class);
                DbCache.put(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(container), states, CacheManager.HOUR);
            }
            return states;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public boolean showQCStates(Container container)
    {
        return getQCStates(container).length > 0;
    }

    public boolean isQCStateInUse(QCState state)
    {
        try
        {
            StudyImpl study = getStudy(state.getContainer());
            if (safeIntegersEqual(study.getDefaultAssayQCState(), state.getRowId()) ||
                safeIntegersEqual(study.getDefaultDirectEntryQCState(), state.getRowId() )||
                safeIntegersEqual(study.getDefaultPipelineQCState(), state.getRowId()))
            {
                return true;
            }
            Integer count = Table.executeSingleton(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM " +
                    StudySchema.getInstance().getTableInfoStudyData() + " WHERE Container = ? AND QCState = ?",
                    new Object[] { state.getContainer().getId(), state.getRowId() }, Integer.class);
            return count != null && count.intValue() > 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public QCState insertQCState(User user, QCState state) throws SQLException
    {
        QCState[] preInsertStates = getQCStates(state.getContainer());
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        QCState newState = Table.insert(user, StudySchema.getInstance().getTableInfoQCState(), state);
        // switching from zero to more than zero QC states affects the columns in our materialized datasets
        // (adding a QC State column), so we unmaterialize them here:
        if (preInsertStates == null || preInsertStates.length == 0)
            clearCaches(state.getContainer(), true);
        return newState;
    }

    public QCState updateQCState(User user, QCState state) throws SQLException
    {
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        return Table.update(user, StudySchema.getInstance().getTableInfoQCState(), state, state.getRowId());
    }

    public void deleteQCState(QCState state) throws SQLException
    {
        QCState[] preDeleteStates = getQCStates(state.getContainer());
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        Table.delete(StudySchema.getInstance().getTableInfoQCState(), state.getRowId());

        // removing our last QC state affects the columns in our materialized datasets
        // (removing a QC State column), so we unmaterialize them here:
        if (preDeleteStates.length == 1)
            clearCaches(state.getContainer(), true);

    }

    @Nullable
    public QCState getDefaultQCState(StudyImpl study)
    {
        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        QCState defaultQCState = null;
        if (defaultQcStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(
                study.getContainer(), defaultQcStateId.intValue());
        return defaultQCState;
    }

    public QCState getQCStateForRowId(Container container, int rowId)
    {
        QCState[] states = getQCStates(container);
        for (QCState state : states)
        {
            if (state.getRowId() == rowId && state.getContainer().equals(container))
                return state;
        }
        return null;
    }

    private Map<String, VisitImpl> getVisitsForDataRows(Container container, int datasetId, Collection<String> dataLsids)
    {
        Map<String, VisitImpl> visits = new HashMap<String, VisitImpl>();
        if (dataLsids == null || dataLsids.isEmpty())
            return visits;

        SQLFragment sql = new SQLFragment("SELECT sd.LSID AS LSID, v.RowId AS RowId FROM study.StudyData sd\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.Container = pv.Container AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = v.Container\n" +
                "WHERE sd.DatasetId = ? AND\n" +
                "\tsd.Container = ? AND\n" +
                "\tsd.lsid IN (");
        sql.add(datasetId);
        sql.add(container.getId());
        boolean first = true;
        for (String dataLsid : dataLsids)
        {
            if (!first)
                sql.append(", ");
            sql.append("?");
            sql.add(dataLsid);
            first = false;
        }
        sql.append(")");

        Study study = getStudy(container);
        ResultSet rs = null;
        try
        {
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
            while (rs.next())
            {
                String lsid = rs.getString("LSID");
                int visitId = rs.getInt("RowId");
                visits.put(lsid, getVisitForRowId(study, visitId));
            }
            return visits;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }


    public void updateDataQCState(Container container, User user, int datasetId, Collection<String> lsids, QCState newState, String comments) throws SQLException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        Study study = getStudy(container);
        DataSetDefinition def = getDataSetDefinition(study, datasetId);

        Map<String, VisitImpl> lsidVisits = null;
        if (!def.isDemographicData())
            lsidVisits = getVisitsForDataRows(container, datasetId, lsids);
        Map<String, Object>[] rows = StudyService.get().getDatasetRows(user, container, datasetId, lsids);
        if (rows == null)
            return;

        Map<String, String> oldQCStates = new HashMap<String, String>();
        Map<String, String> newQCStates = new HashMap<String, String>();

        Set<String> updateLsids = new HashSet<String>();
        for (Map<String, Object> row : rows)
        {
            String lsid = (String) row.get("lsid");

            Integer oldStateId = (Integer) row.get(DataSetTable.QCSTATE_ID_COLNAME);
            QCState oldState = null;
            if (oldStateId != null)
                oldState = getQCStateForRowId(container, oldStateId.intValue());

            // check to see if we're actually changing state.  If not, no-op:
            if (safeIntegersEqual(newState != null ? newState.getRowId() : null, oldStateId))
                continue;

            updateLsids.add(lsid);

            StringBuilder auditKey = new StringBuilder(StudyService.get().getSubjectNounSingular(container) + " ");
            auditKey.append(row.get(StudyService.get().getSubjectColumnName(container)));
            if (!def.isDemographicData())
            {
                VisitImpl visit = lsidVisits.get(lsid);
                auditKey.append(", Visit ").append(visit.getLabel());
            }
            String keyProp = def.getKeyPropertyName();
            if (keyProp != null)
            {
                auditKey.append(", ").append(keyProp).append(" ").append(row.get(keyProp));
            }

            oldQCStates.put(auditKey.toString(), oldState != null ? oldState.getLabel() : "unspecified");
            newQCStates.put(auditKey.toString(), newState != null ? newState.getLabel() : "unspecified");
        }

        if (updateLsids.isEmpty())
            return;

        try
        {
            if (transactionOwner)
                scope.beginTransaction();

            SQLFragment sql = new SQLFragment("UPDATE " + StudySchema.getInstance().getTableInfoStudyData() + "\n" +
                    "SET QCState = ");
            // do string concatenation, rather that using a parameter, for the new state id because Postgres null
            // parameters are typed which causes a cast exception trying to set the value back to null (bug 6370)
            sql.append(newState != null ? newState.getRowId() : "NULL");
            sql.append(", modified = ?");
            sql.add(new Date());
            sql.append("\nWHERE DatasetId = ? AND\n" +
                    "\tContainer = ? AND\n" +
                    "\tlsid IN (");
            sql.add(datasetId);
            sql.add(container.getId());
            boolean first = true;
            for (String dataLsid : updateLsids)
            {
                if (!first)
                    sql.append(", ");
                sql.append("?");
                sql.add(dataLsid);
                first = false;
            }
            sql.append(")");

            Table.execute(StudySchema.getInstance().getSchema(), sql);

            def.deleteFromMaterialized(user, updateLsids);
            def.insertIntoMaterialized(user, updateLsids);

            String auditComment = "QC state was changed for " + updateLsids.size() + " record" +
                    (updateLsids.size() == 1 ? "" : "s") + ".  User comment: " + comments;

            AuditLogEvent event = new AuditLogEvent();
            event.setCreatedBy(user);

            event.setContainerId(container.getId());
            if (container.getProject() != null)
                event.setProjectId(container.getProject().getId());

            event.setIntKey1(datasetId);

            // IntKey2 is non-zero because we have details (a previous or new datamap)
            event.setIntKey2(1);

            event.setEventType(DatasetAuditViewFactory.DATASET_AUDIT_EVENT);
            event.setComment(auditComment);

            Map<String,Object> dataMap = new HashMap<String,Object>();
            dataMap.put("oldRecordMap", SimpleAuditViewFactory.encodeForDataMap(oldQCStates, false));
            dataMap.put("newRecordMap", SimpleAuditViewFactory.encodeForDataMap(newQCStates, false));
            AuditLogService.get().addEvent(event, dataMap, AuditLogService.get().getDomainURI(DatasetAuditViewFactory.DATASET_AUDIT_EVENT));

            clearCaches(container, false);

            if (transactionOwner)
                scope.commitTransaction();
        }
        finally
        {
            if (transactionOwner)
            {
                if (scope.isTransactionActive())
                    scope.rollbackTransaction();
            }
        }
    }
    
    private boolean safeIntegersEqual(Integer first, Integer second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }

    public boolean showCohorts(Container container, User user)
    {
        if (user == null)
            return false;
        if (user.isAdministrator())
            return true;
        StudyImpl study = StudyManager.getInstance().getStudy(container);

        if (study == null)
            return false;

        if (study.isManualCohortAssignment())
        {
            // If we're not reading from a dataset for cohort definition,
            // we use the container's permission
            return SecurityManager.getPolicy(container).hasPermission(user, ReadPermission.class);
        }

        // Automatic cohort assignment -- can the user read the source dataset?
        Integer cohortDatasetId = study.getParticipantCohortDataSetId();
        if (cohortDatasetId != null)
        {
            DataSetDefinition def = getDataSetDefinition(study, cohortDatasetId.intValue());
            if (def != null)
                return def.canRead(user);
        }
        return false;
    }

    public void assertCohortsViewable(Container container, User user)
    {
        if (!user.isAdministrator())
        {
            StudyImpl study = StudyManager.getInstance().getStudy(container);

            if (study.isManualCohortAssignment())
            {
                if (!SecurityManager.getPolicy(container).hasPermission(user, ReadPermission.class))
                    throw new UnauthorizedException("User does not have permission to view cohort information");
            }

            // Automatic cohort assignment -- check the source dataset for permissions
            Integer cohortDatasetId = study.getParticipantCohortDataSetId();
            if (cohortDatasetId != null)
            {
                DataSetDefinition def = getDataSetDefinition(study, cohortDatasetId.intValue());
                if (def != null)
                {
                    if (!def.canRead(user))
                        throw new UnauthorizedException("User does not have permissions to view cohort information.");
                }
            }
        }
    }

    public CohortImpl[] getCohorts(Container container, User user)
    {
        assertCohortsViewable(container, user);
        try
        {
            return _cohortHelper.get(container,"Label");
        }
        catch (SQLException se)
        {
            throw new RuntimeSQLException(se);
        }
    }

    public CohortImpl getCurrentCohortForParticipant(Container container, User user, String participantId) throws SQLException
    {
        assertCohortsViewable(container, user);
        Participant participant = getParticipant(getStudy(container), participantId);
        if (participant != null && participant.getCurrentCohortId() != null)
            return _cohortHelper.get(container, participant.getCurrentCohortId().intValue());
        return null;
    }

    public CohortImpl getCohortForRowId(Container container, User user, int rowId)
    {
        assertCohortsViewable(container, user);
        try
        {
            return _cohortHelper.get(container, rowId);
        }
        catch (SQLException se)
        {
            throw new RuntimeSQLException(se);
        }
    }

    public CohortImpl getCohortByLabel(Container container, User user, String label)
    {
        assertCohortsViewable(container, user);
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addCondition("Label", label);

        try
        {
            CohortImpl[] cohorts = _cohortHelper.get(container, filter);
            if (cohorts != null && cohorts.length == 1)
                return cohorts[0];
        }
        catch (SQLException se)
        {
            UnexpectedException.rethrow(se);
        }
        return null;
    }

    private boolean isCohortInUse(CohortImpl cohort, TableInfo table, String... columnNames)
    {
        try
        {
            List<Object> params = new ArrayList<Object>();
            params.add(cohort.getContainer().getId());

            StringBuilder cols = new StringBuilder("(");
            String or = "";
            for (String columnName : columnNames)
            {
                cols.append(or).append(columnName).append(" = ?");
                params.add(cohort.getRowId());
                or = " OR ";
            }
            cols.append(")");

            Integer count = Table.executeSingleton(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM " +
                    table + " WHERE Container = ? AND " + cols.toString(),
                    params.toArray(new Object[params.size()]), Integer.class);
            return count != null && count.intValue() > 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isCohortInUse(CohortImpl cohort)
    {
        return isCohortInUse(cohort, StudySchema.getInstance().getTableInfoDataSet(), "CohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoParticipant(), "CurrentCohortId", "InitialCohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoParticipantVisit(), "CohortId") ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoVisit(), "CohortId");
    }

    public void deleteCohort(CohortImpl cohort) throws SQLException
    {
        _cohortHelper.delete(cohort);

        // delete extended properties
        Container container = cohort.getContainer();
        String lsid = cohort.getLsid();
        Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, lsid);
        if (resourceProperties != null && !resourceProperties.isEmpty())
        {
            OntologyManager.deleteOntologyObject(lsid, container, false);
        }
    }


    public VisitImpl getVisitForSequence(Study study, double seqNum)
    {
        VisitImpl[] visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        for (VisitImpl v : visits)
        {
            if (seqNum >= v.getSequenceNumMin() && seqNum <= v.getSequenceNumMax())
                return v;
        }
        return null;
    }

    public DataSetDefinition[] getDataSetDefinitions(Study study)
    {
        return getDataSetDefinitions(study, null);
    }

    public DataSetDefinition[] getDataSetDefinitions(Study study, CohortImpl cohort)
    {
        try
        {
            SimpleFilter filter = null;
            if (cohort != null)
            {
                filter = new SimpleFilter("Container", study.getContainer().getId());
                filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
            }
            return _dataSetHelper.get(study.getContainer(), filter, "DisplayOrder,Category,DataSetId");
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public PropertyDescriptor[] getSharedProperties(Study study)
    {
        try
        {
            return _dataSetHelper.getSharedProperties(study.getContainer());
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Nullable
    public DataSetDefinition getDataSetDefinition(Study s, int id)
    {
        try
        {
            DataSetDefinition ds = _dataSetHelper.get(s.getContainer(), id, "DataSetId");
            if (null == ds)
                return null;
            // update old rows w/o entityid
            if (null == ds.getEntityId())
            {
                ds.setEntityId(GUID.makeGUID());
                updateDataSetDefinition(null, ds);
            }
            return ds;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Nullable
    public DataSetDefinition getDataSetDefinition(Study s, String label)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", s.getContainer().getId());
            filter.addCondition("Label", label);

            DataSetDefinition[] defs = _dataSetHelper.get(s.getContainer(), filter);
            if (defs != null && defs.length == 1)
                return defs[0];

            return null;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    @Nullable
    public DataSet getDataSetDefinitionByName(Study s, String name)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", s.getContainer().getId());
            filter.addCondition("Name", name);

            DataSet[] defs = _dataSetHelper.get(s.getContainer(), filter);
            if (defs != null && defs.length == 1)
                return defs[0];

            return null;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public List<String> getDatasetLSIDs(User user, DataSetDefinition def) throws ServletException, SQLException
    {
        TableInfo tInfo = def.getTableInfo(user, true, false);
        Set<String> select = Collections.singleton("lsid");

        @SuppressWarnings("unchecked")
        Map<String,Object>[] data = Table.select(tInfo, select, new SimpleFilter(), null, Map.class);
        
        List<String> lsids = new ArrayList<String>(data.length);
        for (Map<String,Object> row : data)
        {
            lsids.add(row.get("lsid").toString());
        }
        return lsids;
    }

    public void uncache(DataSetDefinition def)
    {
        _dataSetHelper.clearCache(def);
        def.unmaterialize();
    }


    public Map<VisitMapKey,Boolean> getRequiredMap(Study study)
    {
        TableInfo tableVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
        try
        {
        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), "SELECT DatasetId, VisitRowId, Required FROM " + tableVisitMap + " WHERE Container=?",
                new Object[] { study.getContainer() });
        HashMap<VisitMapKey,Boolean> map = new HashMap<VisitMapKey, Boolean>();
        while (rs.next())
            map.put(new VisitMapKey(rs.getInt(1), rs.getInt(2)), rs.getBoolean(3));
        rs.close();
        return map;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }



    private static final String VISITMAP_JOIN_BY_VISIT = "SELECT d.*, vm.Required\n" +
            "FROM study.Visit v, study.DataSet d, study.VisitMap vm\n" +
            "WHERE v.RowId = vm.VisitRowId and vm.DataSetId = d.DataSetId and " +
            "v.Container = vm.Container and vm.Container = d.Container " +
            "and v.Container = ? and v.RowId = ?\n" +
            "ORDER BY d.DisplayOrder,d.DataSetId;";

    private static final String VISITMAP_JOIN_BY_DATASET = "SELECT vm.VisitRowId, vm.Required\n" +
            "FROM study.VisitMap vm JOIN study.Visit v ON vm.VisitRowId = v.RowId\n" +
            "WHERE vm.Container = ? AND vm.DataSetId = ?\n" +
            "ORDER BY v.DisplayOrder, v.RowId;";

    List<VisitDataSet> getMapping(VisitImpl visit)
    {
        if (visit.getContainer() == null)
            throw new IllegalStateException("Visit has no container");

        try
        {
            ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_VISIT,
                    new Object[] { visit.getContainer().getId(), visit.getRowId() });
            List<VisitDataSet> visitDataSets = new ArrayList<VisitDataSet>();
            while (rs.next())
            {
                int dataSetId = rs.getInt("DataSetId");
                boolean isRequired = rs.getBoolean("Required");
                visitDataSets.add(new VisitDataSet(visit.getContainer(), dataSetId, visit.getRowId(), isRequired));
            }
            rs.close();
            return visitDataSets;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public List<VisitDataSet> getMapping(DataSet dataSet)
    {
        try
        {
            ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_DATASET,
                    new Object[]{dataSet.getContainer().getId(), dataSet.getDataSetId()});
            List<VisitDataSet> visitDataSets = new ArrayList<VisitDataSet>();
            while (rs.next())
            {
                int visitRowId = rs.getInt("VisitRowId");
                boolean isRequired = rs.getBoolean("Required");
                visitDataSets.add(new VisitDataSet(dataSet.getContainer(), dataSet.getDataSetId(), visitRowId, isRequired));
            }
            rs.close();
            return visitDataSets;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public void updateVisitDataSetMapping(User user, Container container, int visitId,
                                          int dataSetId, VisitDataSetType type) throws SQLException
    {
        VisitDataSet vds = getVisitDataSetMapping(container, visitId, dataSetId);
        if (vds == null)
        {
            if (type != VisitDataSetType.NOT_ASSOCIATED)
            {
                // need to insert a new VisitMap entry:
                createVisitDataSetMapping(user, container, visitId,
                        dataSetId, type == VisitDataSetType.REQUIRED);
            }
        }
        else if (type == VisitDataSetType.NOT_ASSOCIATED)
        {
            // need to remove an existing VisitMap entry:
            Table.delete(_tableInfoVisitMap,
                    new Object[] { container.getId(), visitId, dataSetId});
        }
        else if ((VisitDataSetType.OPTIONAL == type && vds.isRequired()) ||
                 (VisitDataSetType.REQUIRED == type && !vds.isRequired()))
        {
            Map<String,Object> required = new HashMap<String, Object>(1);
            required.put("Required", VisitDataSetType.REQUIRED == type ? Boolean.TRUE : Boolean.FALSE);
            Table.update(user, _tableInfoVisitMap, required,
                    new Object[]{container.getId(), visitId, dataSetId});
        }
    }

    public int getNumDatasetRows(DataSet dataset)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo sdTable = StudySchema.getInstance().getTableInfoStudyData();

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS numRows FROM ");
        sql.append(sdTable);
        sql.append(" WHERE Container = '").append(dataset.getContainer().getId());
        sql.append("' AND DatasetId = ").append(dataset.getDataSetId());

        try
        {
            return Table.executeSingleton(schema, sql.toString(), null, Integer.class).intValue();
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }


    public int purgeDataset(Study study, DataSetDefinition dataset, User user)
    {
        return purgeDataset(study, dataset, null, user);
    }

    /**
     * Delete all rows from a dataset or just those newer than the cutoff date.
     */
    public int purgeDataset(Study study, DataSetDefinition dataset, Date cutoff, User user)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();
        Container c = study.getContainer();
        int count;

        TableInfo data = StudySchema.getInstance().getTableInfoStudyData();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        filter.addCondition("DatasetId", dataset.getDataSetId());

        try
        {
            CPUTimer time = new CPUTimer("purge");
            time.start();

            SQLFragment sub = new SQLFragment("SELECT LSID FROM " + data + " " + "WHERE Container = ? and DatasetId = ?",
                    c.getId(), dataset.getDataSetId());
            if (cutoff != null)
                sub.append(" AND _VisitDate > ?").add(cutoff);
            OntologyManager.deleteOntologyObjects(StudySchema.getInstance().getSchema(), sub, c, false);

            SQLFragment studyDataFrag = new SQLFragment(
                    "DELETE FROM " + data + "\n" +
                    "WHERE Container = ? and DatasetId = ?",
                    c.getId(), dataset.getDataSetId());
            if (cutoff != null)
                studyDataFrag.append(" AND _VisitDate > ?").add(cutoff);
            count = Table.execute(StudySchema.getInstance().getSchema(), studyDataFrag);

            time.stop();
            _log.debug("purgeDataset " + dataset.getDisplayString() + " " + DateUtil.formatDuration(time.getTotal()/1000));
        }
        catch (SQLException s)
        {
            throw new RuntimeSQLException(s);
        }
        finally
        {
            dataset.unmaterialize();
            StudyManager.fireDataSetChanged(dataset);
        }
        return count;
    }


    /** delete a dataset definition along with associated type, data, visitmap entries */
    public void deleteDataset(StudyImpl study, User user, DataSetDefinition ds) throws SQLException
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        deleteDatasetType(study, user, ds);
        try {
            QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(study.getContainer(), 
                    StudyManager.getSchemaName(), ds.getLabel());
            if (def != null)
                def.delete(user);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        Table.execute(StudySchema.getInstance().getSchema(),
                "DELETE FROM " + _tableInfoVisitMap + "\n" +
                "WHERE Container=? AND DatasetId=?",
                new Object[] {study.getContainer(), ds.getDataSetId()});

        // UNDONE: This is broken
        // this._dataSetHelper.delete(ds);
        Table.execute(StudySchema.getInstance().getSchema(),
                "DELETE FROM " + StudySchema.getInstance().getTableInfoDataSet() + "\n" +
                "WHERE Container=? AND DatasetId=?",
                new Object[] {study.getContainer(), ds.getDataSetId()});

        _dataSetHelper.clearCache(ds);

        SecurityManager.deletePolicy(ds);

        if (safeIntegersEqual(ds.getDataSetId(), study.getParticipantCohortDataSetId()))
            CohortManager.getInstance().setManualCohortAssignment(study, user, Collections.<String, Integer>emptyMap());

        unindexDataset(ds);
    }


    /** delete a dataset type and data */
    public void deleteDatasetType(Study study, User user,  DataSetDefinition ds) throws SQLException
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        if (null == ds)
            return;

        purgeDataset(study, ds, user);

        if (ds.getTypeURI() != null)
        {
            ds = ds.createMutable();
            try
            {
                OntologyManager.deleteType(ds.getTypeURI(), study.getContainer());
            }
            catch (DomainNotFoundException x)
            {
                // continue
            }
            ds.setTypeURI(null);
            updateDataSetDefinition(user, ds);
        }
    }


    public void clearCaches(Container c, boolean unmaterializeDatasets)
    {
        Study study = getStudy(c);
        _studyHelper.clearCache(c);
        _visitHelper.clearCache(c);
        _siteHelper.clearCache(c);
        if (unmaterializeDatasets)
            for (DataSetDefinition def : getDataSetDefinitions(study))
                uncache(def);
        _dataSetHelper.clearCache(c);

        DbCache.clear(StudySchema.getInstance().getTableInfoQCState());
        DbCache.clear(StudySchema.getInstance().getTableInfoParticipant());
    }

    public void deleteAllStudyData(Container c) throws SQLException
    {
        deleteAllStudyData(c, null, false, true);
    }

    public void deleteAllStudyData(Container c, User user, boolean deleteDatasetData, boolean deleteStudyDesigns) throws SQLException
    {
        // Cancel any reload timer
        StudyReload.cancelTimer(c);

        // Before we delete any data, we need to go fetch the Dataset definitions.
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        List<DataSetDefinition> dsds;
        if (study == null) // no study in this folder
            dsds = Collections.emptyList();
        else
            dsds = study.getDataSets();

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean localTransaction = !scope.isTransactionActive();

        HashSet<TableInfo> deletedTables = new HashSet<TableInfo>();
        SimpleFilter containerFilter = new SimpleFilter("Container", c.getId());

        try
        {
            if (localTransaction)
                scope.beginTransaction();

            if (deleteStudyDesigns)
                StudyDesignManager.get().deleteStudyDesigns(c, deletedTables);
            else            //If study design came from another folder, move it back to where it came from
                StudyDesignManager.get().inactivateStudyDesign(c);

            //If deleteDatasetData is false, OntologyManager will clean up on folder delete
            if (deleteDatasetData)
                for (DataSetDefinition dsd : dsds)
                    deleteDataset(study, user, dsd);
            //
            // samples
            //
            SampleManager.getInstance().deleteAllSampleData(c, deletedTables);

            //
            // metadata
            //
            Table.delete(_tableInfoVisitMap, containerFilter);
            assert deletedTables.add(_tableInfoVisitMap);
            Table.delete(_tableInfoUploadLog, containerFilter);
            assert deletedTables.add(_tableInfoUploadLog);
            Table.delete(_dataSetHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_dataSetHelper.getTableInfo());
            Table.delete(_siteHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_siteHelper.getTableInfo());
            Table.delete(_visitHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_visitHelper.getTableInfo());
            Table.delete(_studyHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_studyHelper.getTableInfo());

            //
            // participant and assay data (OntologyManager will take care of properties)
            //
            Table.delete(StudySchema.getInstance().getTableInfoStudyData(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoStudyData());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantVisit(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantVisit());
            Table.delete(_tableInfoParticipant, containerFilter);
            assert deletedTables.add(_tableInfoParticipant);
            Table.delete(StudySchema.getInstance().getTableInfoCohort(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoCohort());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantView(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantView());

            //
            // plate service
            //
            Table.delete(StudySchema.getInstance().getSchema().getTable("Well"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("Well"));
            Table.delete(StudySchema.getInstance().getSchema().getTable("WellGroup"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("WellGroup"));
            Table.delete(StudySchema.getInstance().getTableInfoPlate(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoPlate());

            //
            // reports
            //
            ReportManager.get().deleteReports(c, deletedTables);

            // QC States
            Table.delete(StudySchema.getInstance().getTableInfoQCState(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoQCState());

            // Specimen comments
            Table.delete(StudySchema.getInstance().getTableInfoSpecimenComment(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoSpecimenComment());

            // Materialized tables
            for (DataSetDefinition dsd : dsds)
            {
                dsd.unmaterialize();
                fireDataSetChanged(dsd);
            }

            if (localTransaction)
            {
                scope.commitTransaction();
            }

        }
        finally
        {
            if (localTransaction)
                scope.closeConnection();
        }

        //
        // trust and verify
        //
        Set<String> deletedTableNames = new HashSet<String>();
        for (TableInfo t : deletedTables)
        {
            deletedTableNames.add(t.getName());
        }
        StringBuilder missed = new StringBuilder();
        for (TableInfo t : StudySchema.getInstance().getSchema().getTables())
            if (!deletedTableNames.contains(t.getName()))
            {
                if (!deleteStudyDesigns && isStudyDesignTable(t))
                    continue;

                missed.append(" ");
                missed.append(t.getName());
            }
        assert missed.length() == 0 : "forgot something? " + missed;
    }

    private boolean isStudyDesignTable(TableInfo t)
    {
        return t.getName().equals(StudyDesignManager.get().getStudyDesignTable().getName()) || t.getName().equals(StudyDesignManager.get().getStudyVersionTable().getName());
    }

    public String getDatasetType(Container c, int datasetId)
    {
        // not using executeSingleton, so I can distinguish not found from typeURI is null
        try
        {
            String[] result = Table.executeArray(getSchema(), "SELECT typeURI FROM study.dataset WHERE container=? AND datasetid=?",
                    new Object[]{c.getId(), datasetId}, String.class);
            if (result.length == 0)
                return null;
            assert result.length == 1;
            return result[0];
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public ParticipantDataset[] getParticipantDatasets(Container container, Collection<String> lsids) throws SQLException
    {
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("LSID IN (");
        Object[] params = new Object[lsids.size() + 1];
        String comma = "";
        int i = 0;
        for (String lsid : lsids)
        {
            whereClause.append(comma);
            whereClause.append("?");
            params[i++] = lsid;
            comma = ",";
        }
        whereClause.append(") AND Container = ?");
        params[lsids.size()] = container.getId();
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params);
        // We can't use the table layer to map results to our bean class because of the unfortunately named
        // "_VisitDate" column in study.StudyData.
        ResultSet rs = null;
        try
        {
            List<ParticipantDataset> pds = new ArrayList<ParticipantDataset>();
            rs = Table.select(_tableInfoStudyData, Table.ALL_COLUMNS, filter, null);
            while (rs.next())
            {
                ParticipantDataset pd = new ParticipantDataset();
                pd.setContainer(container);
                pd.setDataSetId(rs.getInt("DatasetId"));
                pd.setLsid(rs.getString("LSID"));
                pd.setSequenceNum(rs.getDouble("SequenceNum"));
                pd.setVisitDate(rs.getTimestamp("_VisitDate"));
                pd.setParticipantId(rs.getString("ParticipantId"));
                pds.add(pd);
            }
            return pds.toArray(new ParticipantDataset[pds.size()]);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
    }


    /**
     * After changing permissions on the study, we have to scrub the dataset acls to
     * remove any groups that no longer have read permission.
     *
     * UNDONE: move StudyManager into model package (so we can have protected access)
     */
    protected void scrubDatasetAcls(Study study, SecurityPolicy newPolicy)
    {
        //for every principal that plays something other than the RestrictedReaderRole,
        //delete that group's role assignments in all dataset policies
        Role restrictedReader = RoleManager.getRole(RestrictedReaderRole.class);

        Set<SecurableResource> resources = new HashSet<SecurableResource>();
        resources.addAll(Arrays.asList(getDataSetDefinitions(study)));

        Set<UserPrincipal> principals = new HashSet<UserPrincipal>();

        for (RoleAssignment ra : newPolicy.getAssignments())
        {
            if (!(ra.getRole().equals(restrictedReader)))
                principals.add(SecurityManager.getPrincipal(ra.getUserId()));
        }

        SecurityManager.clearRoleAssignments(resources, principals);
    }


    public long getParticipantCount(Study study)
    {
        try
        {
            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo table = _tableInfoParticipant;
            return Table.executeSingleton(schema,
                    "SELECT COUNT(ParticipantId) FROM " + table + " WHERE Container = ?",
                    new Object[]{study.getContainer().getId()}, Long.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public String[] getParticipantIds(Study study)
    {
        return getParticipantIds(study, -1);
    }

    public String[] getParticipantIds(Study study, int rowLimit)
    {
        try
        {
            DbSchema schema = StudySchema.getInstance().getSchema();
            TableInfo table = _tableInfoParticipant;
            SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + table + " WHERE Container = ? ORDER BY ParticipantId", study.getContainer().getId());
            if (rowLimit > 0)
                sql = schema.getSqlDialect().limitRows(sql, rowLimit);
            return Table.executeArray(schema, sql, String.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public AllParticipantData getAllParticipantData(Study study, String participantId, QCStateSet qcStateSet)
    {
        return AllParticipantData.get(study, participantId, qcStateSet);    
    }

    public SnapshotBean createSnapshot(SnapshotBean bean) throws SQLException, ServletException
    {
        DbScope scope = getSchema().getScope();
        String schemaName = bean.getSchemaName();

        if (scope.isTransactionActive())
            throw new IllegalStateException("Create snapshot with transaction open.");

        try
        {
            //Can't create schema during transaction
            getSchema().getSqlDialect().dropSchema(getSchema(), schemaName);
            Table.execute(getSchema(), getSchema().getSqlDialect().getCreateSchemaSql(schemaName), new Object[0]);
            scope.beginTransaction();
            for (String category : bean.getCategories())
            {
                for (String sourceName : bean.getSourceNames(category))
                {
                    TableSnapshotInfo snapshotInfo = bean.getTableSnapshotInfo(category, sourceName);
                    if (null != snapshotInfo && snapshotInfo.snapshot && null != snapshotInfo.getTableInfo())
                        Table.snapshot(snapshotInfo.getTableInfo(), schemaName + "." + bean.getDestTableName(category, sourceName));
                }
            }

            bean.setLastSnapshotDate(new Date());
            saveSnapshotInfo(bean);
            scope.commitTransaction();

            return bean;
        }
        finally
        {
            if (scope.isTransactionActive())
                scope.rollbackTransaction();
        }
        //Drop existing schema
        //
    }

    public static class SnapshotBean
    {
        private String schemaName;
        private Date lastSnapshotDate;
        private Container container;
        private Map<String, Map<String, TableSnapshotInfo>> tableSnapshotInfo = new HashMap<String, Map<String, TableSnapshotInfo>>();

        private SnapshotBean()
        {

        }

        private SnapshotBean(User user, Container container) throws ServletException
        {
            initFromContainer(user,container);
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public Date getLastSnapshotDate()
        {
            return lastSnapshotDate;
        }

        public void setLastSnapshotDate(Date lastSnapshotDate)
        {
            this.lastSnapshotDate = lastSnapshotDate;
        }

        public Container getContainer()
        {
            return container;
        }

        public void setContainer(Container container)
        {
            this.container = container;
        }

        public String[] getCategories()
        {
            String[] categories = tableSnapshotInfo.keySet().toArray(new String[tableSnapshotInfo.keySet().size()]);
            Arrays.sort(categories);
            return categories;
        }

        private void initFromContainer(User user, Container container) throws ServletException
        {
            this.container = container;
            this.schemaName = AliasManager.makeLegalName(container.getName() + "_" + container.getRowId(), getSchema().getSqlDialect());
            Map<String,ListDefinition> listMap = ListService.get().getLists(container);
            Map<String,TableSnapshotInfo> listSnapshotInfo = new HashMap<String, TableSnapshotInfo>();
            for (String listName : listMap.keySet())
                listSnapshotInfo.put(listName, new TableSnapshotInfo(listMap.get(listName).getTable(user)));

            tableSnapshotInfo.put("Lists", listSnapshotInfo);

            StudyImpl study = StudyManager.getInstance().getStudy(container);
            if (null == study)
                return;

            Map<String,TableSnapshotInfo> datasetSnapshotInfo = new HashMap<String, TableSnapshotInfo>();
            for (DataSetDefinition dsd : study.getDataSets())
                datasetSnapshotInfo.put(dsd.getName(), new TableSnapshotInfo(dsd.getTableInfo(user, true, false)));

            tableSnapshotInfo.put("Datasets", datasetSnapshotInfo);
        }
        
        public SortedSet<String> getSourceNames(String category)
        {
            SortedSet<String> names = new TreeSet<String>();
            //Placeholder
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null != categoryTables)
                names.addAll(categoryTables.keySet());
            
            return names;
        }

        public String getDestTableName(String category, String sourceName)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null != categoryTables)
            {
                TableSnapshotInfo info = categoryTables.get(sourceName);
                if (null != info && null != info.destTableName)
                    return info.destTableName;
            }

            return AliasManager.makeLegalName(sourceName, getSchema().getSqlDialect());
        }

        public boolean isSaveTable(String category, String sourceName)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null != categoryTables)
            {
                TableSnapshotInfo info = categoryTables.get(sourceName);
                if (null != info)
                    return info.snapshot;
            }

            return true;
        }

        void setDestTableInfo(String category, String sourceName, String destName, boolean snapshot)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null == categoryTables) //If no tableInfo can't set anything
                return;

            TableSnapshotInfo info = categoryTables.get(sourceName);
            if (null != info)
            {
                info.snapshot = snapshot;
                info.destTableName = destName;
            }
        }

        public void setSnapshot(String category, String sourceName, boolean snapshot)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null == categoryTables)
                return; //Tables changed & that one isn't there anymore
            TableSnapshotInfo snapshotInfo = categoryTables.get(sourceName);
            if (null == snapshotInfo)
                return;

            snapshotInfo.snapshot = snapshot;
        }

        public void setDestTableName(String category, String sourceName, String destName)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null == categoryTables)
                return; //Tables changed & that one isn't there anymore
            TableSnapshotInfo snapshotInfo = categoryTables.get(sourceName);
            if (null == snapshotInfo)
                return; //Tables changed & that one isn't there anymore

            snapshotInfo.destTableName = destName;
        }

        public TableSnapshotInfo getTableSnapshotInfo(String category, String sourceName)
        {
            Map<String,TableSnapshotInfo> categoryTables = tableSnapshotInfo.get(category);
            if (null == categoryTables)
                return null;

            return categoryTables.get(sourceName);
        }
    }

    static class TableSnapshotInfo
    {
        private boolean snapshot;
        private String destTableName;
        private TableInfo tableInfo;

        TableSnapshotInfo(TableInfo tableInfo, boolean snapshot, String destTableName)
        {
            this.tableInfo = tableInfo;
            this.snapshot = snapshot;
            this.destTableName = destTableName;
        }

        public TableSnapshotInfo(TableInfo tableInfo)
        {
            this(tableInfo, true, null);
        }

        public TableInfo getTableInfo()
        {
            return tableInfo;
        }

        public void setTableInfo(TableInfo tableInfo)
        {
            this.tableInfo = tableInfo;
        }
    }

    private static final String NO_SNAPSHOT = "~NO SNAPSHOT~";
    public SnapshotBean getSnapshotInfo(User user, Container container) throws ServletException
    {
        SnapshotBean bean = new SnapshotBean(user, container);
        applySavedSnapshotInfo(container, bean);

        return bean;
    }

    private void applySavedSnapshotInfo(Container container, SnapshotBean bean)
    {
        Map<String, String> props = PropertyManager.getProperties(container.getId(), "snapshot");
        if (props.isEmpty())
            return;

        bean.setSchemaName(props.get("schemaName"));
        bean.setLastSnapshotDate((Date) ConvertUtils.convert(props.get("lastSnapshotDate"), Date.class));
        for (String key : props.keySet())
        {
            int index = key.indexOf('.');
            if (index < 0)
                continue;

            String category = key.substring(0, index);
            String sourceName = key.substring(index + 1);
            String targetName = props.get(key);
            if (NO_SNAPSHOT.equals(targetName))
                bean.setSnapshot(category, sourceName, false);
            else
                bean.setDestTableInfo(category, sourceName, targetName, true);
        }
    }

    private void saveSnapshotInfo(SnapshotBean bean)
    {
        Map<String,String> props = PropertyManager.getWritableProperties(bean.getContainer().getId(), "snapshot", true);
        props.clear(); //Don't want to track old tables
        props.put("lastSnapshotDate", ConvertUtils.convert(bean.getLastSnapshotDate()));
        props.put("schemaName", bean.getSchemaName());
        for (String category : bean.getCategories())
            for (String tableName : bean.getSourceNames(category))
            {
                if (bean.isSaveTable(category, tableName))
                    props.put(category + "." + tableName, bean.getDestTableName(category, tableName));
                else
                    props.put(category + "." + tableName, NO_SNAPSHOT);
            }
        PropertyManager.saveProperties(props);
    }


    public static final String CONVERSION_ERROR = "Conversion Error";

    private List<Map<String, Object>> parseData(User user,
                                   DataSetDefinition def,
                                   DataLoader loader,
                                   Map<String, String> columnMap,
                                   List<String> errors)
            throws ServletException, IOException
    {
        TableInfo tinfo = def.getTableInfo(user, false, false);

        // We're going to lower-case the keys ourselves later,
        // so this needs to be case-insensitive
        if (!(columnMap instanceof CaseInsensitiveHashMap))
        {
            columnMap = new CaseInsensitiveHashMap<String>(columnMap);
        }

        Map<String,ColumnInfo> propName2Col = new CaseInsensitiveHashMap<ColumnInfo>();

        Domain domain = def.getDomain();

        if (null != domain)
        {
            for (Map.Entry<String, DomainProperty> aliasInfo : def.getDomain().createImportMap(true).entrySet())
            {
                propName2Col.put(aliasInfo.getKey(), tinfo.getColumn(aliasInfo.getValue().getName()));
            }
        }

        for (ColumnInfo col : tinfo.getColumns())
        {
            propName2Col.put(col.getName(), col);
            String uri = col.getPropertyURI();
            if (null != uri)
            {
                propName2Col.put(uri, col);
                String propName = uri.substring(uri.lastIndexOf('#')+1);
                if (!propName2Col.containsKey(propName))
                    propName2Col.put(propName,col);
            }
        }
        for (ColumnInfo col : tinfo.getColumns())
        {
            String label = col.getLabel();
            if (null != label && !propName2Col.containsKey(label))
                propName2Col.put(label, col);
        }

        //
        // create columns to properties map
        //
        HashSet<String> foundProperties = new HashSet<String>();
        ColumnDescriptor[] cols = loader.getColumns();
        for (ColumnDescriptor col : cols)
        {
            String name = col.name.toLowerCase();

            //Special column name
            if ("replace".equals(name))
            {
                col.clazz = Boolean.class;
                col.name = name; //Lower case
                continue;
            }

            if (columnMap.containsKey(name))
                name = columnMap.get(name);

            ColumnInfo matchedCol = propName2Col.get(name);
            if (null == matchedCol)
                continue;

            String matchedURI = matchedCol.getPropertyURI();

            if (! (matchedCol instanceof MvColumn))
            {
                if (foundProperties.contains(matchedURI))
                {
                    errors.add("Property '" + name + "' included more than once.");
                }
                foundProperties.add(matchedURI);
            }

            col.name = matchedURI;
            col.clazz = matchedCol.getJavaClass();
            col.errorValues = CONVERSION_ERROR;
            if (matchedCol.isMvEnabled())
            {
                col.setMvEnabled(def.getContainer());
            }
            else if (matchedCol instanceof MvColumn)
            {
                col.setMvIndicator(def.getContainer());
            }
            else
            {
                // explicitly null the MV enabled property.  This is because the types may have been
                // inferred as having MV indicators, after which the user may have elected not to use them.  In
                // this case, it's necessary to explicitly modify 'col' to match 'matchedCol'.
                col.setMvDisabled();
            }
        }

        // make sure that our QC state columns are understood by this tab loader; we'll need to find QCStateLabel columns
        // during import, and map them to values that will be populated in the QCState Id column.  As a result, we need to
        // ensure QC label is in the loader's column set so it's found at import time, and that the QC ID is in the set so
        // we can assign a value to the property before we insert the data.  brittp, 7.23.2008
        loader.ensureColumn(new ColumnDescriptor(DataSetTable.QCSTATE_LABEL_COLNAME, String.class));
        loader.ensureColumn(new ColumnDescriptor(DataSetDefinition.getQCStateURI(), Integer.class));

        return loader.load();
    }

    public List<String> importDatasetData(Study study, User user, DataSetDefinition def, DataLoader loader, long lastModified, Map<String, String> columnMap, List<String> errors, boolean checkDuplicates, boolean ensureObjects, QCState defaultQCState, Logger logger)
            throws IOException, ServletException, SQLException
    {
        List<Map<String, Object>> dataMaps = parseData(user, def, loader, columnMap, errors);
        if (logger != null) logger.debug("parsed " + dataMaps.size() + " rows");
        return importDatasetData(study, user, def, dataMaps, lastModified, errors, checkDuplicates, ensureObjects, defaultQCState, logger);
    }

    public List<String> importDatasetData(Study study, User user, DataSetDefinition def, List<Map<String, Object>> dataMaps, long lastModified, List<String> errors, boolean checkDuplicates, boolean ensureObjects, QCState defaultQCState, Logger logger)
        throws SQLException
    {
        List<String> result = def.importDatasetData(study, user, dataMaps, lastModified, errors, checkDuplicates, ensureObjects, defaultQCState, logger);
        if (logger != null) logger.debug("imported " + result.size() + " rows");
        return result;
    }

    public boolean importDatasetSchemas(StudyImpl study, User user, SchemaReader reader, BindException errors) throws IOException, SQLException
    {
        if (errors.hasErrors())
            return false;

        List<Map<String, Object>> mapsImport = reader.getImportMaps();

        if (!mapsImport.isEmpty())
        {
            List<String> importErrors = new LinkedList<String>();
            final Container c = study.getContainer();

            // Use a factory to ensure domain URI consistency between imported properties and the dataset.  See #7944.
            DomainURIFactory factory = new DomainURIFactory() {
                public String getDomainURI(String name)
                {
                    return StudyManager.getDomainURI(c, name);
                }
            };

            PropertyDescriptor[] pds = OntologyManager.importTypes(factory, reader.getTypeNameColumn(), mapsImport, importErrors, c, true);

            if (!importErrors.isEmpty())
            {
                for (String error : importErrors)
                    errors.reject("importDatasetSchemas", error);
                return false;
            }

            if (pds != null && pds.length > 0)
            {
                Map<Integer, SchemaReader.DataSetImportInfo> datasetInfoMap = reader.getDatasetInfo();
                StudyManager manager = StudyManager.getInstance();

                for (Map.Entry<Integer, SchemaReader.DataSetImportInfo> entry : datasetInfoMap.entrySet())
                {
                    int id = entry.getKey().intValue();
                    SchemaReader.DataSetImportInfo info = entry.getValue();
                    String name = info.name;
                    String label = info.label;

                    // Check for name conflicts
                    DataSet existingDef = manager.getDataSetDefinition(study, label);

                    if (existingDef != null && existingDef.getDataSetId() != id)
                    {
                        errors.reject("importDatasetSchemas", "A different dataset already exists with the label " + label);
                        return false;
                    }

                    existingDef = manager.getDataSetDefinitionByName(study, name);

                    if (existingDef != null && existingDef.getDataSetId() != id)
                    {
                        errors.reject("importDatasetSchemas", "A different dataset already exists with the name " + name);
                        return false;
                    }

                    DataSetDefinition def = manager.getDataSetDefinition(study, id);

                    if (def == null)
                    {
                        def = new DataSetDefinition(study, id, name, label, null, factory.getDomainURI(name));
                        def.setDescription(info.description);
                        def.setVisitDatePropertyName(info.visitDatePropertyName);
                        def.setShowByDefault(!info.isHidden);
                        def.setKeyPropertyName(info.keyPropertyName);
                        def.setCategory(info.category);
                        def.setKeyManagementType(info.keyManagementType);
                        def.setDemographicData(info.demographicData);
                        manager.createDataSetDefinition(user, def);
                    }
                    else
                    {
                        def = def.createMutable();
                        def.setLabel(label);
                        def.setName(name);
                        def.setDescription(info.description);
                        def.setTypeURI(getDomainURI(c, def));
                        def.setVisitDatePropertyName(info.visitDatePropertyName);
                        def.setShowByDefault(!info.isHidden);
                        def.setKeyPropertyName(info.keyPropertyName);
                        def.setCategory(info.category);
                        def.setKeyManagementType(info.keyManagementType);
                        def.setDemographicData(info.demographicData);
                        manager.updateDataSetDefinition(user, def);
                    }
                }
            }
        }

        return true;
    }


    public String getDomainURI(Container c, DataSet def)
    {
        if (null == def)
            return getDomainURI(c, (String)null);
        else
            return getDomainURI(c, def.getName());
    }

    private static String getDomainURI(Container c, String name)
    {
        return new DatasetDomainKind().generateDomainURI(c, name);
    }

    /** NOTE: this is usually handled at import time, this is only useful if DataSetDefinition.visitDatePropertyName changes */
    public void recomputeStudyDataVisitDate(StudyImpl study, Collection<DataSetDefinition> changedDatasets)
    {
        for (DataSetDefinition def : changedDatasets)
        {
            String propertyName = StringUtils.trimToNull(def.getVisitDatePropertyName());
            if (null == propertyName)
                continue;
            if (null == StringUtils.trimToNull(def.getTypeURI()))
                continue;
            PropertyDescriptor pds[] = OntologyManager.getPropertiesForType(def.getTypeURI(), study.getContainer());
            if (pds == null)
                continue;
            PropertyDescriptor pdVisitDate = null;
            for (PropertyDescriptor pd : pds)
            {
                if (propertyName.equalsIgnoreCase(pd.getName()))
                {
                    pdVisitDate = pd;
                    break;
                }
            }
            if (pdVisitDate != null)
            {
                try
                {
                    DbSchema schema = StudySchema.getInstance().getSchema();
                    String sqlUpdate =
                            "UPDATE study.StudyData SET _VisitDate=(SELECT datetimevalue FROM exp.Object O JOIN exp.ObjectProperty OP ON O.ObjectId=OP.ObjectId WHERE O.Container=? AND O.ObjectURI=LSID AND OP.PropertyId=?)\n" +
                            "WHERE Container=? AND DataSetId=?";
                    int count = Table.execute(schema, sqlUpdate, new Object[] {study.getContainer(), pdVisitDate.getPropertyId(), study.getContainer(), def.getDataSetId()} );
                    if (count > 0)
                    {
                        def.unmaterialize();
                        StudyManager.fireDataSetChanged(def);
                    }
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }
        }
    }


    public VisitManager getVisitManager(StudyImpl study)
    {
        switch (study.getTimepointType())
        {
            case VISIT:
                return new SequenceVisitManager(study);
            case CONTINUOUS:
                return new AbsoluteDateVisitManager(study);
            case DATE:
            default:
                return new RelativeDateVisitManager(study);
        }
    }

    private static final String STUDY_FORMAT_STRINGS = "DefaultStudyFormatStrings";
    private static final String DATE_FORMAT_STRING = "DateFormatString";
    private static final String NUMBER_FORMAT_STRING = "NumberFormatString";

    public String getDefaultDateFormatString(Container c)
    {
        return getFormatStrings(c).get(DATE_FORMAT_STRING);
    }

    public String getDefaultNumberFormatString(Container c)
    {
        return getFormatStrings(c).get(NUMBER_FORMAT_STRING);
    }

    private  Map<String, String> getFormatStrings(Container c)
    {
        return PropertyManager.getProperties(c.getId(), STUDY_FORMAT_STRINGS);
    }

    public void setDefaultDateFormatString(Container c, String format)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(c.getId(), STUDY_FORMAT_STRINGS, true);

        if (!StringUtils.isEmpty(format))
            props.put(DATE_FORMAT_STRING, format);
        else if (props.containsKey(DATE_FORMAT_STRING))
            props.remove(DATE_FORMAT_STRING);
        PropertyManager.saveProperties(props);
    }

    public void setDefaultNumberFormatString(Container c, String format)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(c.getId(), STUDY_FORMAT_STRINGS, true);
        if (!StringUtils.isEmpty(format))
            props.put(NUMBER_FORMAT_STRING, format);
        else if (props.containsKey(NUMBER_FORMAT_STRING))
            props.remove(NUMBER_FORMAT_STRING);
        PropertyManager.saveProperties(props);
    }

    public void applyDefaultFormats(Container c, List<DisplayColumn> columns)
    {
        final String defaultDate = StudyManager.getInstance().getDefaultDateFormatString(c);
        final String defaultNumber = StudyManager.getInstance().getDefaultNumberFormatString(c);

        if (!StringUtils.isEmpty(defaultDate) || !StringUtils.isEmpty(defaultNumber))
        {
            for (DisplayColumn dc : columns)
            {
                if (canFormat(dc))
                {
                    Class valueClass = dc.getDisplayValueClass();
                    if (!StringUtils.isEmpty(defaultNumber) && (valueClass.isPrimitive() || Number.class.isAssignableFrom(valueClass)))
                        dc.setFormatString(defaultNumber);
                    else if (!StringUtils.isEmpty(defaultDate) && Date.class.isAssignableFrom(valueClass))
                        dc.setFormatString(defaultDate);
                }
            }
        }
    }

    //Create a fixed point number encoding the date.
    public static double sequenceNumFromDate(Date d)
    {
        Calendar cal = DateUtil.newCalendar(d.getTime());
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    public static SQLFragment sequenceNumFromDateSQL(String dateColumnName)
    {
        // Returns a SQL statement that produces a single number from a date, in the form of YYYYMMDD.
        SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
        SQLFragment sql = new SQLFragment();
        sql.append("(10000 * ").append(dialect.getDatePart(Calendar.YEAR, dateColumnName)).append(") + ");
        sql.append("(100 * ").append(dialect.getDatePart(Calendar.MONTH, dateColumnName)).append(") + ");
        sql.append("(").append(dialect.getDatePart(Calendar.DAY_OF_MONTH, dateColumnName)).append(")");
        return sql;
    }


    private boolean canFormat(DisplayColumn dc)
    {
        final ColumnInfo col = dc.getColumnInfo();
        if (col != null)
            return !col.isFormatStringSet();
        return dc.getFormatString() == null;
    }

    // for Module()
    public static LsidManager.LsidHandler getLsidHandler()
    {
        return new StudyLsidHandler();
    }


    static class StudyLsidHandler implements LsidManager.LsidHandler
    {
        public ExpObject getObject(Lsid lsid)
        {
            throw new UnsupportedOperationException();
        }

        public Container getContainer(Lsid lsid)
        {
            throw new UnsupportedOperationException();
        }

        public String getDisplayURL(Lsid lsid)
        {
            String fullNamespace = lsid.getNamespace();
            if (!fullNamespace.startsWith("Study."))
                return null;
            String studyNamespace = fullNamespace.substring("Study.".length());
            int i = studyNamespace.indexOf("-");
            if (-1 == i)
                return null;
            String type = studyNamespace.substring(0, i);

            if (type.equalsIgnoreCase("Data"))
            {
                try
                {
                    ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(),
                            "SELECT Container, DatasetId, SequenceNum, ParticipantId FROM " + StudySchema.getInstance().getTableInfoStudyData() + " WHERE LSID=?",
                            new Object[] {lsid.toString()});
                    if (!rs.next())
                        return null;
                    String containerId = rs.getString(1);
                    int datasetId = rs.getInt(2);
                    double sequenceNum = rs.getDouble(3);
                    String ptid = rs.getString(4);
                    Container c = ContainerManager.getForId(containerId);
                    ActionURL url = new ActionURL(StudyController.DatasetAction.class, c);
                    url.addParameter(DataSetDefinition.DATASETKEY, String.valueOf(datasetId));
                    url.addParameter(VisitImpl.SEQUENCEKEY, String.valueOf(sequenceNum));
                    url.addParameter("StudyData.participantId~eq", ptid);
                    return url.toString();
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }
/*
            if (type.equalsIgnoreCase("Participant"))
            {
                try
                {
                    ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(),
                            "SELECT Container, ParticipantId FROM " + StudySchema.getInstance().getTableInfoParticipant() + " WHERE IndividualLSID=?",
                            new Object[] {lsid.toString()});
                    if (!rs.next())
                        return null;
                    String containerId = rs.getString(1);
                    String ptid = rs.getString(2);
                    Container c = ContainerManager.getForId(containerId);
                    ActionURL url = new ActionURL("Study", "participant", c);
                    url.addParameter("Participant.participantId~eq", ptid);
                    return url.getURIString();
                }
                catch (SQLException x)
                {
                    throw new RuntimeSQLException(x);
                }
            }
*/
            return null;
        }

        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            return false;
        }
    }

    private String getParticipantCacheName(Container container)
    {
        return container.getId() + "/" + Participant.class.toString();
    }

    private Map<String, Participant> getParticipantMap(Study study) throws SQLException
    {
        Map<String, Participant> participantMap = (Map<String, Participant>) DbCache.get(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()));
        if (participantMap == null)
        {
            SimpleFilter filter = new SimpleFilter("Container", study.getContainer());
            Participant[] participants = Table.select(StudySchema.getInstance().getTableInfoParticipant(), Table.ALL_COLUMNS,
                    filter, new Sort("ParticipantId"), Participant.class);
            participantMap = new LinkedHashMap<String, Participant>();
            for (Participant participant : participants)
                participantMap.put(participant.getParticipantId(), participant);
            DbCache.put(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()), participantMap, CacheManager.HOUR);
        }
        return participantMap;
    }

    public Participant[] getParticipants(Study study) throws SQLException
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        Participant[] participants = new Participant[participantMap.size()];
        int i = 0;
        for (Map.Entry<String, Participant> entry : participantMap.entrySet())
            participants[i++] = entry.getValue();
        return participants;
    }

    public Participant getParticipant(Study study, String participantId) throws SQLException
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        return participantMap.get(participantId);
    }

    public CustomParticipantView getCustomParticipantView(Study study) throws SQLException
    {
        if (study == null)
            return null;
        SimpleFilter containerFilter = new SimpleFilter("Container", study.getContainer().getId());
        return Table.selectObject(StudySchema.getInstance().getTableInfoParticipantView(), Table.ALL_COLUMNS,
                containerFilter, null, CustomParticipantView.class);
    }

    public CustomParticipantView saveCustomParticipantView(Study study, User user, CustomParticipantView view) throws SQLException
    {
        if (view.getRowId() == null)
        {
            view.beforeInsert(user, study.getContainer().getId());
            return Table.insert(user, StudySchema.getInstance().getTableInfoParticipantView(), view);
        }
        else
        {
            view.beforeUpdate(user);
            return Table.update(user, StudySchema.getInstance().getTableInfoParticipantView(), view, view.getRowId());
        }
    }

    public interface ParticipantViewConfig
    {
        String getParticipantId();

        int getDatasetId();

        String getRedirectUrl();

        QCStateSet getQCStateSet();
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config)
    {
        return getParticipantView(container, config, null);
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config, BindException errors)
    {
        StudyImpl study = getStudy(container);
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return new BaseStudyController.StudyJspView<ParticipantViewConfig>(study, "participantData.jsp", config, errors);
        else
            return new BaseStudyController.StudyJspView<ParticipantViewConfig>(study, "participantAll.jsp", config, errors);
    }

    public WebPartView<ParticipantViewConfig> getParticipantDemographicsView(Container container, ParticipantViewConfig config, BindException errors)
    {
        return new BaseStudyController.StudyJspView<ParticipantViewConfig>(getStudy(container), "participantCharacteristics.jsp", config, errors);
    }

    public interface DataSetListener
    {
        void dataSetChanged(DataSet def);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<DataSetListener> _listeners = new CopyOnWriteArrayList<DataSetListener>();

    public static void addDataSetListener(DataSetListener listener)
    {
        _listeners.add(listener);
    }

    public static void fireDataSetChanged(DataSet def)
    {
        for (DataSetListener l : _listeners)
            try
            {
                l.dataSetChanged(def);
            }
            catch (Throwable t)
            {
                _log.error("fireDataSetChanged", t);
            }
    }

    public void reindex(Container c)
    {
        _enumerateDocuments(null, c);
    }
    

    private void unindexDataset(DataSetDefinition ds)
    {
        String docid = "dataset:" + new Path(ds.getContainer().getId(),String.valueOf(ds.getDataSetId())).toString();
        SearchService ss = ServiceRegistry.get(SearchService.class);
        if (null != ss)
            ss.deleteResource(docid);
    }


    public static void indexDatasets(SearchService.IndexTask task, Container c, Date modifiedSince)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss) return;
        ResultSet rs = null;
        try
        {
            SQLFragment f = new SQLFragment("SELECT container, datasetid FROM " + StudySchema.getInstance().getTableInfoDataSet());
            if (null != c)
            {
                f.append(" WHERE container = ?");
                f.add(c);
            }
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), f, 0, false, false);

            ActionURL dataset = new ActionURL(StudyController.DatasetAction.class, null);

            while (rs.next())
            {
                String container = rs.getString(1);
                int id = rs.getInt(2);

                c = ContainerManager.getForId(container);
                if (null == c) continue;
                Study study = StudyManager.getInstance().getStudy(c);
                if (null == study) continue;
                DataSetDefinition dsd = StudyManager.getInstance().getDataSetDefinition(study, id);
                if (null == dsd) continue;

                String docid = "dataset:" + new Path(container,String.valueOf(id)).toString();

                StringBuilder body = new StringBuilder();
                Map<String, Object> props = new HashMap<String, Object>();

                props.put(SearchService.PROPERTY.categories.toString(), datasetCategory.toString());
                props.put(SearchService.PROPERTY.displayTitle.toString(), StringUtils.defaultIfEmpty(dsd.getLabel(),dsd.getName()));
                String name = dsd.getName();
                String label = StringUtils.equals(dsd.getLabel(),name) ? null : dsd.getLabel();
                String description = dsd.getDescription();
                String searchTitle = StringUtilsLabKey.joinNonBlank(" ", name, label, description); 
                props.put(SearchService.PROPERTY.searchTitle.toString(), searchTitle);

                body.append(searchTitle).append("\n");

                String sep = "";

                Domain domain = dsd.getDomain();
                if (null != domain)
                {
                    for (DomainProperty property : domain.getProperties())
                    {
                        String n = StringUtils.trimToEmpty(property.getName());
                        String l = StringUtils.trimToEmpty(property.getLabel());
                        if (n.equals(l))
                            l = "";
                        body.append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
                        sep = ",\n";
                    }
                }

                ActionURL view = dataset.clone().replaceParameter("datasetId", String.valueOf(id));
                view.setExtraPath(container);

                SimpleDocumentResource r = new SimpleDocumentResource(new Path(docid), docid,
                        "text/plain", body.toString().getBytes(),
                        view, props);
                task.addResource(r, SearchService.PRIORITY.item);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }
    

    public static void indexParticipantView(final SearchService.IndexTask task, final Date modifiedSince)
    {
        ResultSet rs = null;
        try
        {
            SQLFragment f = new SQLFragment("SELECT DISTINCT container FROM study.participant");
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), f, 0, false, false);
            while (rs.next())
            {
                final String id = rs.getString(1);
                final Container c = ContainerManager.getForId(id);
                if (null == c)
                    continue;
                task.addRunnable(new Runnable()
                {
                    public void run()
                    {
                        indexParticipantView(task, c, modifiedSince);
                    }
                }, SearchService.PRIORITY.group);
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    public static void indexParticipantView(SearchService.IndexTask task, Container c, Date modifiedSince)
    {
        if (null == c)
        {
            indexParticipantView(task, modifiedSince);
            return;
        }

        ResultSet rs = null;
        try
        {
            Study study = StudyManager.getInstance().getStudy(c);
            if (null == study)
                return;
            String nav = NavTree.toJS(Collections.singleton(new NavTree("study", new ActionURL("project","begin",c.getId()))), null, false).toString();

            SQLFragment f = new SQLFragment("SELECT container, participantid FROM " + StudySchema.getInstance().getTableInfoParticipant());
            if (null != c)
            {
                f.append(" WHERE container = ?");
                f.add(c);
            }
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), f, 0, false, false);

            ActionURL indexURL = new ActionURL(StudyController.IndexParticipantAction.class, c);
            indexURL.setExtraPath(c.getId());
            ActionURL executeURL = new ActionURL(StudyController.ParticipantAction.class, c);
            executeURL.setExtraPath(c.getId());
            
            while (rs.next())
            {
                String id = rs.getString(2);
                String displayTitle = "Study " + study.getLabel() + " -- " +
                        StudyService.get().getSubjectNounSingular(study.getContainer()) + " " + id;
                ActionURL execute = executeURL.clone().addParameter("participantId",String.valueOf(id));
                Path p = new Path(c.getId(),id);
                String docid = "participant:" + p.toString();

                Map<String,Object> props = new HashMap<String,Object>();
                props.put(SearchService.PROPERTY.categories.toString(), subjectCategory.getName());
                props.put(SearchService.PROPERTY.participantId.toString(), id);
                props.put(SearchService.PROPERTY.displayTitle.toString(), displayTitle);
                props.put(SearchService.PROPERTY.searchTitle.toString(), id);
                props.put(SearchService.PROPERTY.navtrail.toString(), nav);

                // need to figure out if all study users can see demographic data or not
                if (1==1)
                {
                    // SimpleDocument
                    SimpleDocumentResource r = new SimpleDocumentResource(
                            p, docid,
                            c.getId(),
                            "text/plain",
                            displayTitle.getBytes(),
                            execute, props
                    );
                    task.addResource(r, SearchService.PRIORITY.item);
                }
                else
                {
                    // ActionResource
                    ActionURL index = indexURL.clone().addParameter("participantId",id);
                    ActionResource r = new ActionResource(subjectCategory, docid, execute, index, props);
                    task.addResource(r, SearchService.PRIORITY.item);
                }
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        catch (Throwable x)
        {
            _log.error("Unexpected error", x);
            if (x instanceof RuntimeException)
                throw (RuntimeException)x;
            throw new RuntimeException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    
    public static void indexParticipants(Container c)
    {
        SearchService ss = ServiceRegistry.get().getService(SearchService.class);
        if (null == ss)
            return;
        
        ResultSet rs = null;
        try
        {
            SQLFragment f = new SQLFragment("SELECT container, participantid FROM " + StudySchema.getInstance().getTableInfoParticipant());
            if (null != c)
            {
                f.append(" WHERE container = ?");
                f.add(c);
            }
            rs = Table.executeQuery(StudySchema.getInstance().getSchema(), f, 0, false, false);
            ss.addParticipantIds(rs);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }


    // make sure we don't over do it with multiple calls to reindex the same study (see reindex())
    // add a level of indirection
    // CONSIDER: add some facility like this to SearchService??
    // NOTE: this needs to be reviewed if we use modifiedSince

    final static WeakHashMap<Container,Runnable> _lastEnumerate = new WeakHashMap<Container,Runnable>();

    public static void _enumerateDocuments(SearchService.IndexTask t, final Container c)
    {
        if (null == c)
            return;

        final SearchService.IndexTask defaultTask = ServiceRegistry.get(SearchService.class).defaultTask();
        final SearchService.IndexTask task = null==t ? defaultTask : t;

        Runnable runEnumerate = new Runnable()
        {
            public void run()
            {
                if (task == defaultTask)
                {
                    synchronized (_lastEnumerate)
                    {
                        Runnable r = _lastEnumerate.get(c);
                        if (this != r)
                            return;
                        _lastEnumerate.remove(c);
                    }
                }
                StudyManager.indexDatasets(task, c, null);
                StudyManager.indexParticipantView(task, c, null);
                StudyManager.indexParticipants(c);
                AssayService.get().indexAssays(task, c);
            }
        };

        if (task == defaultTask)
        {
            synchronized (_lastEnumerate)
            {
                _lastEnumerate.put(c, runEnumerate);
            }
        }
        
        task.addRunnable(runEnumerate, SearchService.PRIORITY.crawl);
    }


    public static class StudyTestCase extends junit.framework.TestCase
    {
        public StudyTestCase()
        {
            super("Study Test Case");
        }

        /* it would be nice to have some tests here ... */
        public void test()
        {
        }

        public static Test suite()
        {
            return new TestSuite(StudyTestCase.class);
        }
    }
}
