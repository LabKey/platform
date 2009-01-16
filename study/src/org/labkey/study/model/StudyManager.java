/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.security.ACL;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.common.tools.DataLoader;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.CPUTimer;
import org.labkey.study.QueryHelper;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyCache;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.visitmanager.DateVisitManager;
import org.labkey.study.visitmanager.SequenceVisitManager;
import org.labkey.study.visitmanager.VisitManager;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.CopyOnWriteArrayList;


public class StudyManager
{
    private static Logger _log = Logger.getLogger(StudyManager.class);
    
    private static StudyManager _instance;
    private static final Object MANAGED_KEY_LOCK = new Object();
    private static final String SCHEMA_NAME = "study";
    private final TableInfo _tableInfoVisitMap;
    private final TableInfo _tableInfoParticipant;
    private final TableInfo _tableInfoStudyData;
    private final TableInfo _tableInfoUploadLog;

    private final QueryHelper<Study> _studyHelper;
    private final QueryHelper<Visit> _visitHelper;
    private final QueryHelper<Site> _siteHelper;
    private final QueryHelper<DataSetDefinition> _dataSetHelper;
    private final QueryHelper<Cohort> _cohortHelper;

    private static final String LSID_REQUIRED = "LSID_REQUIRED";


    private StudyManager()
    {
        // prevent external construction with a private default constructor
        _studyHelper = new QueryHelper<Study>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoStudy();
                }
            }, Study.class);

        _visitHelper = new QueryHelper<Visit>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoVisit();
                }
            }, Visit.class);

        _siteHelper = new QueryHelper<Site>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoSite();
                }
            }, Site.class);

        _cohortHelper = new QueryHelper<Cohort>(new TableInfoGetter()
            {
                public TableInfo getTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoCohort();
                }
            }, Cohort.class);

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
        _dataSetHelper = new QueryHelper<DataSetDefinition>(dataSetGetter, DataSetDefinition.class)
        {
            public void clearCache(DataSetDefinition def)
            {
                super.clearCache(def);
                def.unmaterialize();
            }
        };
        _tableInfoVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
        _tableInfoParticipant = StudySchema.getInstance().getTableInfoParticipant();
        _tableInfoStudyData = StudySchema.getInstance().getTableInfoStudyData();
        _tableInfoUploadLog = StudySchema.getInstance().getTableInfoUploadLog();
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

    public QueryHelper<Visit> getVisitHelper()
    {
        return _visitHelper;
    }

    public synchronized Study getStudy(Container c)
    {
        try
        {
            Study study;
            boolean retry = true;

            while (true)
            {
                Study[] studies = _studyHelper.get(c);
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

    public Study[] getAllStudies() throws SQLException
    {
        return Table.select(StudySchema.getInstance().getTableInfoStudy(), Table.ALL_COLUMNS, null, null, Study.class);
    }

    public Study createStudy(User user, Study study) throws SQLException
    {
        assert null != study.getContainer();
        if (study.getLsid() == null)
            study.setLsid(createLsid(study, study.getContainer().getRowId()));
        study = _studyHelper.create(user, study);

        Container c = study.getContainer();
        ACL acl = new ACL();
        acl.setPermission(Group.groupAdministrators, ACL.PERM_READ);
        acl.setPermission(Group.groupUsers, 0);
        acl.setPermission(Group.groupGuests, 0);
        Integer groupId = SecurityManager.getGroupId(c.getProject(), "Users", false);
        if (null != groupId)
            acl.setPermission(groupId.intValue(), ACL.PERM_READ);
        SecurityManager.updateACL(study.getContainer(), study.getEntityId(), c.getAcl());

        return study;
    }

    public void updateStudy(User user, Study study) throws SQLException
    {
        Study oldStudy = getStudy(study.getContainer());
        Date oldStartDate = oldStudy.getStartDate();
        _studyHelper.update(user, study, new Object[] { study.getContainer() });
        if (oldStudy.isDateBased()  && !oldStartDate.equals(study.getStartDate()))
        {
            // start date has changed, and datasets may use that value. Uncache.
            DateVisitManager visitManager = (DateVisitManager) getVisitManager(study);
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
        createDataSetDefinition(user, new DataSetDefinition(getStudy(container), dataSetId, "" + dataSetId, null, null));
    }

    public void createDataSetDefinition(User user, DataSetDefinition dataSetDefinition) throws SQLException
    {
        if (dataSetDefinition.getDataSetId() <= 0)
            throw new IllegalArgumentException("datasetId must be greater than zero.");
        _dataSetHelper.create(user, dataSetDefinition);
    }

    public void updateDataSetDefinition(User user, DataSetDefinition dataSetDefinition) throws SQLException
    {
        Object[] pk = new Object[]{dataSetDefinition.getContainer().getId(), dataSetDefinition.getDataSetId()};
        _dataSetHelper.update(user, dataSetDefinition, pk);
    }

    public boolean isDataUniquePerParticipant(DataSetDefinition dataSetDefinition) throws SQLException
    {
        String sql = "SELECT max(n) FROM (select count(*) AS n from study.studydata WHERE container=? AND datasetid=? GROUP BY participantid) x";
        Integer maxCount = Table.executeSingleton(getSchema(), sql, new Object[] {dataSetDefinition.getContainer().getId(), dataSetDefinition.getDataSetId()}, Integer.class);
        return maxCount == null || maxCount.intValue() <= 1;
    }


    public int createVisit(Study study, User user, Visit visit) throws SQLException
    {
        if (visit.getContainer() != null && !visit.getContainer().getId().equals(study.getContainer().getId()))
            throw new IllegalStateException("Visit container does not match study");
        visit.setContainer(study.getContainer());

        if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            throw new IllegalStateException("SequenceNumMin must be less than or equal to SequenceNumMax");
        Visit[] visits = getVisits(study);
        for (Visit existingVisit : visits)
        {
            if (existingVisit.getSequenceNumMin() > existingVisit.getSequenceNumMax())
                throw new IllegalStateException("Corrupt existing visit " + existingVisit.getLabel() +
                        ": SequenceNumMin must be less than or equal to SequenceNumMax");
            boolean disjoint = visit.getSequenceNumMax() < existingVisit.getSequenceNumMin() ||
                               visit.getSequenceNumMin() > existingVisit.getSequenceNumMax();
            if (!disjoint)
                throw new IllegalStateException("New visit " + visit.getLabel() + " overlaps existing visit " + existingVisit.getLabel());
        }

        visit = _visitHelper.create(user, visit);

        if (visit.getRowId() == 0)
            throw new IllegalStateException("Visit rowId has not been set properly");

        return visit.getRowId();
    }

    public void ensureVisit(Study study, User user, double visitId, Visit.Type type, String label) throws SQLException
    {
        Visit[] visits = getVisits(study);
        for (Visit visit : visits)
        {
            // check to see if our new visitId is within the range of an existing visit:
            if (visit.getSequenceNumMin() <= visitId && visit.getSequenceNumMax() >= visitId)
                return;
        }
        createVisit(study, user, visitId,  type, label);
    }


    public void createVisit(Study study, User user, double visitId, Visit.Type type, String label) throws SQLException
    {
        Visit visit = new Visit(study.getContainer(), visitId, label, type);
        createVisit(study, user, visit);
    }

    public void createCohort(Study study, User user, Cohort cohort) throws SQLException
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

        cohort.setLsid(createLsid(cohort, cohort.getRowId()));
        _cohortHelper.update(user, cohort);
    }


    public void deleteVisit(Study study, Visit visit) throws SQLException
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
            Table.delete(schema.getTableInfoVisit(), new Object[] {study.getContainer(), visit.getRowId()}, null);
            _visitHelper.clearCache(visit);

            schema.getSchema().getScope().commitTransaction();

            for (DataSetDefinition def : getDataSetDefinitions(study))
                def.unmaterialize();
        }
        finally
        {
            if (schema.getSchema().getScope().isTransactionActive())
                schema.getSchema().getScope().rollbackTransaction();
        }
    }


    public void updateVisit(User user, Visit visit) throws SQLException
    {
        Object[] pk = new Object[]{visit.getContainer().getId(), visit.getRowId()};
        _visitHelper.update(user, visit, pk);
    }

    public void updateCohort(User user, Cohort cohort) throws SQLException
    {
        _cohortHelper.update(user, cohort);
    }

    public void updateParticipant(User user, Participant participant) throws SQLException
    {
        Table.update(user,
                _tableInfoParticipant,
                participant,
                new Object[] {participant.getContainer().getId(), participant.getParticipantId()}, 
                null);
    }


    public Site[] getSites(Container container)
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


    public Site getSite(Container container, int id) throws SQLException
    {
        return _siteHelper.get(container, id);
    }

    public void createSite(User user, Site site) throws SQLException
    {
        _siteHelper.create(user, site);
    }

    public void updateSite(User user, Site site) throws SQLException
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


    public Visit[] getVisits(Study study)
    {
        return getVisits(study, null, null);
    }

    public Visit[] getVisits(Study study, Cohort cohort, User user)
    {
        try
        {
            SimpleFilter filter = null;
            if (cohort != null)
            {
                filter = new SimpleFilter("Container", study.getContainer().getId());
                if (showCohorts(study.getContainer(), user))
                    filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
            }
            return _visitHelper.get(study.getContainer(), filter, "DisplayOrder,SequenceNumMin");
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


    public Visit getVisitForRowId(Study study, int rowId)
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
                DbCache.put(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(container), states, Cache.HOUR);
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
            Study study = getStudy(state.getContainer());
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
        return Table.update(user, StudySchema.getInstance().getTableInfoQCState(), state, state.getRowId(), null);
    }

    public void deleteQCState(QCState state) throws SQLException
    {
        QCState[] preDeleteStates = getQCStates(state.getContainer());
        DbCache.remove(StudySchema.getInstance().getTableInfoQCState(), getQCStateCacheName(state.getContainer()));
        Table.delete(StudySchema.getInstance().getTableInfoQCState(), state.getRowId(), null);

        // removing our last QC state affects the columns in our materialized datasets
        // (removing a QC State column), so we unmaterialize them here:
        if (preDeleteStates.length == 1)
            clearCaches(state.getContainer(), true);

    }

    @Nullable
    public QCState getDefaultQCState(Study study)
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

    private Map<String, Visit> getVisitsForDataRows(Container container, int datasetId, Collection<String> dataLsids)
    {
        Map<String, Visit> visits = new HashMap<String, Visit>();
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
        List<String> errors = new ArrayList<String>();
        Study study = getStudy(container);
        DataSetDefinition def = getDataSetDefinition(study, datasetId);

        Map<String, Visit> lsidVisits = null;
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

            StringBuilder auditKey = new StringBuilder("Participant ");
            auditKey.append(row.get("ParticipantId"));
            if (!def.isDemographicData())
            {
                Visit visit = lsidVisits.get(lsid);
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

            clearCaches(container, true);

            if (transactionOwner && errors.size() == 0)
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
        Study study = StudyManager.getInstance().getStudy(container);

        if (study.isManualCohortAssignment())
        {
            // If we're not reading from a dataset for cohort definition,
            // we use the container's permission
            if (user.isAdministrator())
                return true;
            return SecurityManager.getACL(container).hasPermission(user, ACL.PERM_READ);
        }

        // Automatic cohort assignment -- can the user read the source dataset?
        Integer cohortDatasetId = study.getParticipantCohortDataSetId();
        if (user.isAdministrator())
            return cohortDatasetId != null;
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
            Study study = StudyManager.getInstance().getStudy(container);

            if (study.isManualCohortAssignment())
            {
                if (!SecurityManager.getACL(container).hasPermission(user, ACL.PERM_READ))
                    throw new IllegalStateException("User does not have permission to view cohort information");
            }

            // Automatic cohort assignment -- check the source dataset for permissions
            Integer cohortDatasetId = study.getParticipantCohortDataSetId();
            if (cohortDatasetId != null)
            {
                DataSetDefinition def = getDataSetDefinition(study, cohortDatasetId.intValue());
                if (def != null)
                {
                    if (!def.canRead(user))
                        throw new IllegalStateException("User does not have permissions to view cohort information.");
                }
            }
        }
    }

    public Cohort[] getCohorts(Container container, User user)
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

    public Cohort getCohortForParticipant(Container container, User user, String participantId) throws SQLException
    {
        assertCohortsViewable(container, user);
        Participant participant = getParticipant(getStudy(container), participantId);
        if (participant != null && participant.getCohortId() != null)
            return _cohortHelper.get(container, participant.getCohortId().intValue());
        return null;
    }

    public Cohort getCohortForRowId(Container container, User user, int rowId)
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

    private boolean isCohortInUse(Cohort cohort, TableInfo table)
    {
        try
        {
            Integer count = Table.executeSingleton(StudySchema.getInstance().getSchema(), "SELECT COUNT(*) FROM " +
                    table + " WHERE Container = ? AND CohortId = ?",
                    new Object[] { cohort.getContainer().getId(), cohort.getRowId() }, Integer.class);
            return count != null && count.intValue() > 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    public boolean isCohortInUse(Cohort cohort)
    {
        return isCohortInUse(cohort, StudySchema.getInstance().getTableInfoDataSet()) ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoParticipant()) ||
                isCohortInUse(cohort, StudySchema.getInstance().getTableInfoVisit());
    }

    public void deleteCohort(Cohort cohort) throws SQLException
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


    public Visit getVisitForSequence(Study study, double seqNum)
    {
        Visit[] visits = getVisits(study);
        for (Visit v : visits)
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

    public DataSetDefinition[] getDataSetDefinitions(Study study, Cohort cohort)
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
    public DataSetDefinition getDataSetDefinitionByName(Study s, String name)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", s.getContainer().getId());
            filter.addCondition("Name", name);

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


    /** @deprecated */
    public DataSetDefinition getDataSetDefinition(Container c, int id) throws SQLException
    {
        return _dataSetHelper.get(c, id, "DataSetId");
    }

    private final static String selectSummaryStats =  "SELECT " +
                    "(SELECT count(study.Participant.participantid) FROM study.Participant WHERE study.participant.container=study.study.Container) AS participantCount," +
                    "(SELECT count(study.specimen.rowid) FROM study.Specimen WHERE study.specimen.container=study.study.Container) AS specimenCount," +
                    "(SELECT count(rowid) FROM study.site WHERE study.site.container=study.study.Container) AS siteCount" +
                    "FROM study.study WHERE study.study.container=?";

    public Study.SummaryStatistics getSummaryStatistics(Container c) throws SQLException
    {
        Study.SummaryStatistics summary = Table.executeSingleton(StudySchema.getInstance().getSchema(), selectSummaryStats, new Object[] {c.getId()}, Study.SummaryStatistics.class);
        return summary;
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

    List<VisitDataSet> getMapping(Visit visit)
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


    public List<VisitDataSet> getMapping(DataSetDefinition dataSet)
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
                    new Object[] { container.getId(), visitId, dataSetId}, null);
        }
        else if ((VisitDataSetType.OPTIONAL == type && vds.isRequired()) ||
                 (VisitDataSetType.REQUIRED == type && !vds.isRequired()))
        {
            Map<String,Object> required = new HashMap<String, Object>(1);
            required.put("Required", VisitDataSetType.REQUIRED == type ? Boolean.TRUE : Boolean.FALSE);
            Table.update(user, _tableInfoVisitMap, required,
                    new Object[]{container.getId(), visitId, dataSetId}, null);
        }
    }

    public int getNumDatasetRows(DataSetDefinition dataset)
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


    public void deleteDatasetRows(Study study, DataSetDefinition dataset, Collection<String> rowLSIDs)
    {
        Container c = study.getContainer();

        TableInfo data = StudySchema.getInstance().getTableInfoStudyData();
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", c.getId());
        filter.addCondition("DatasetId", dataset.getDataSetId());
        filter.addInClause("LSID", rowLSIDs);

        DbScope scope =  StudySchema.getInstance().getSchema().getScope();
        boolean startTransaction = !scope.isTransactionActive();
        try
        {
            if (startTransaction)
                scope.beginTransaction();

            char sep = ' ';
            StringBuilder sb = new StringBuilder();
            for (int i=0; i<rowLSIDs.size(); i++)
            {
                sb.append(sep);
                sb.append('?');
                sep = ',';
            }
            List<Object> paramList = new ArrayList<Object>(rowLSIDs);
            OntologyManager.deleteOntologyObjects(StudySchema.getInstance().getSchema(), new SQLFragment(sb.toString(), paramList), c, false);
            Table.delete(data, filter);

            if (startTransaction)
            {
                startTransaction = false;
                scope.commitTransaction();
            }
        }
        catch (SQLException s)
        {
            throw new RuntimeSQLException(s);
        }
        finally
        {
            if (startTransaction)
                scope.rollbackTransaction();
            else
                dataset.unmaterialize();
        }
    }


    public int purgeDataset(Study study, DataSetDefinition dataset)
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
            OntologyManager.deleteOntologyObjects(StudySchema.getInstance().getSchema(), sub, c, false);
            count = Table.execute(StudySchema.getInstance().getSchema(),
                    "DELETE FROM " + data + "\n" +
                    "WHERE Container = ? and DatasetId = ?",
                    new Object[] {c.getId(), dataset.getDataSetId()});
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
        }
        return count;
    }


    /** delete a dataset definition along with associated type, data, visitmap entries */
    public void deleteDataset(Study study, User user, DataSetDefinition ds) throws SQLException
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

        SecurityManager.removeACL(study.getContainer(), ds.getEntityId());
    }


    /** delete a dataset type and data */
    public void deleteDatasetType(Study study, User user,  DataSetDefinition ds) throws SQLException
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        if (null == ds)
            return;

        purgeDataset(study, ds);

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
        _studyHelper.clearCache(c);
        _visitHelper.clearCache(c);
        _siteHelper.clearCache(c);
        if (unmaterializeDatasets)
            for (DataSetDefinition def : getDataSetDefinitions(getStudy(c)))
                uncache(def);
        _dataSetHelper.clearCache(c);

        StudyCache.clearCache(StudySchema.getInstance().getTableInfoParticipant(), c.getId());
    }

    public void deleteAllStudyData(Container c) throws SQLException
    {
        deleteAllStudyData(c, null, false, true);
    }

    public void deleteAllStudyData(Container c, User user, boolean deleteDatasetData, boolean deleteStudyDesigns) throws SQLException
    {
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
                for (DataSetDefinition dsd : getDataSetDefinitions(getStudy(c)))
                        deleteDataset(getStudy(c), user, dsd);
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

            if (localTransaction)
            {
                scope.commitTransaction();
                localTransaction = false;
            }

        }
        finally
        {
            if (localTransaction)
                scope.rollbackTransaction();
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
        return Table.select(_tableInfoStudyData, Table.ALL_COLUMNS, filter, null, ParticipantDataset.class);
    }


    /**
     * After changing permissions on the study, we have to scrub the dataset acls to
     * remove any groups that no longer have read permission.
     *
     * UNDONE: move StudyManager into model package (so we can have protected access)
     * @param study
     */
    protected void scrubDatasetAcls(Study study)
    {
        ACL acl = study.getACL();
        int[] restrictedGroups = acl.getGroups(ACL.PERM_READOWN, null);
        DataSetDefinition[] defs = getDataSetDefinitions(study);
        for (DataSetDefinition def : defs)
        {
            ACL aclOld = def.getACL();
            ACL aclScrubbed = aclOld.scrub(restrictedGroups);
            if (aclOld == aclScrubbed)
                continue;
            def.updateACL(aclScrubbed);
        }
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

    public SnapshotBean createSnapshot(User user, SnapshotBean bean) throws SQLException, ServletException
    {
        DbScope scope = getSchema().getScope();
        Study study = getStudy(bean.getContainer());
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
                        Table.snapshot(snapshotInfo.getTableInfo(), schemaName + "." + snapshotInfo.destTableName);
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
        private Map<String, Map<String,TableSnapshotInfo>> tableSnapshotInfo = new HashMap<String, Map<String, TableSnapshotInfo>>();

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
            String[] categories = tableSnapshotInfo.keySet().toArray(new String[0]);
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
                listSnapshotInfo.put(listName, new TableSnapshotInfo(listMap.get(listName).getTable(user, listName)));

            tableSnapshotInfo.put("Lists", listSnapshotInfo);

            Study study = StudyManager.getInstance().getStudy(container);
            if (null == study)
                return;

            Map<String,TableSnapshotInfo> datasetSnapshotInfo = new HashMap<String, TableSnapshotInfo>();
            DataSetDefinition [] datasets = study.getDataSets();
            for (DataSetDefinition dsd : datasets)
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
            if (null == categoryTables)
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
        Map<String,String> props = PropertyManager.getProperties(container.getId(), "snapshot", false);
        if (null == props)
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

    public SnapshotBean getLastSnapshotInfo(User user, Container container)
    {
        SnapshotBean bean = new SnapshotBean();
        applySavedSnapshotInfo(container, bean);

        return bean;
    }

    private void saveSnapshotInfo(SnapshotBean bean)
    {
        Map<String,String> props = PropertyManager.getWritableProperties(0, bean.getContainer().getId(), "snapshot", true);
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


    private static final String CONVERSION_ERROR = "Conversion Error";

    private Map<String, Object>[] parseData(DataSetDefinition def,
                                   DataLoader loader,
                                   Map<String, String> columnMap,
                                   List<String> errors)
            throws ServletException, IOException
    {
        TableInfo tinfo = def.getTableInfo(null, false, false);

        // We're going to lower-case the keys ourselves later,
        // so this needs to be case-insensitive
        if (!(columnMap instanceof CaseInsensitiveHashMap))
        {
            columnMap = new CaseInsensitiveHashMap<String>(columnMap);
        }

        Map<String,ColumnInfo> propName2Col = new CaseInsensitiveHashMap<ColumnInfo>();
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
            String label = col.getCaption();
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

            if (! (matchedCol instanceof QcColumn))
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
            if (matchedCol.isQcEnabled())
            {
                col.qcEnabled = true;
                col.qcContainer = def.getContainer();
            }
            else if (matchedCol instanceof QcColumn)
            {
                col.qcIndicator = true;
                col.qcContainer = def.getContainer();
            }
        }

        // make sure that our QC state columns are understood by this tab loader; we'll need to find QCStateLabel columns
        // during import, and map them to values that will be populated in the QCState Id column.  As a result, we need to
        // ensure QC label is in the loader's column set so it's found at import time, and that the QC ID is in the set so
        // we can assign a value to the property before we insert the data.  brittp, 7.23.2008
        loader.ensureColumn(new ColumnDescriptor(DataSetTable.QCSTATE_LABEL_COLNAME, String.class));
        loader.ensureColumn(new ColumnDescriptor(DataSetDefinition.getQCStateURI(), Integer.class));

        Map<String, Object>[] maps = (Map<String, Object>[]) loader.load();

        return maps;
    }


    /**
     * If all the dups can be replaced, delete them. If not return the ones that should NOT be replaced
     * and do not delete anything
     *
     */
    HashMap<String,Map> checkAndDeleteDups(Study study, DataSetDefinition def, Map[] rows) throws SQLException, ServletException
    {
        if (null == rows || rows.length == 0)
            return null;

        Container c = study.getContainer();
        DatasetImportHelper helper = new DatasetImportHelper(null, c, def, 0);

        // duplicate keys found that should be deleted
        Set<String> deleteSet = new HashSet<String>();

        // duplicate keys found in error
        LinkedHashMap<String,Map> noDeleteMap = new LinkedHashMap<String,Map>();
        
        StringBuffer sbIn = new StringBuffer();
        String sep = "";
        Map<String, Map> uriMap = new HashMap<String, Map>();
        for (Map m : rows)
        {
            String uri = helper.getURI(m);
            if (null != uriMap.put(uri, m))
                noDeleteMap.put(uri,m);
            sbIn.append(sep).append("'").append(uri).append("'");
            sep = ", ";
        }

        TableInfo tinfo = StudySchema.getInstance().getTableInfoStudyData();
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause("LSID IN (" + sbIn + ")", new Object[]{});

        Map[] results = Table.select(tinfo, Table.ALL_COLUMNS, filter, null, Map.class);
        for (Map orig : results)
        {
            String lsid = (String) orig.get("LSID");
            Map newMap = uriMap.get(lsid);
            boolean replace = Boolean.TRUE.equals(newMap.get("replace"));
            if (replace)
            {
                deleteSet.add(lsid);
            }
            else
            {
                noDeleteMap.put(lsid, newMap);
            }
        }

        // If we have duplicates, and we don't have an auto-keyed dataset,
        // then we cannot proceed.
        if (noDeleteMap.size() > 0 && !def.isKeyPropertyManaged())
            return noDeleteMap;

        if (deleteSet.size() == 0)
            return null;

        SimpleFilter deleteFilter = new SimpleFilter();
        StringBuffer sbDelete = new StringBuffer();
        sep = "";
        for (String s : deleteSet)
        {
            sbDelete.append(sep).append("'").append(s).append("'");
            sep = ", ";
        }
        deleteFilter.addWhereClause("LSID IN (" + sbDelete + ")", new Object[]{});
        Table.delete(StudySchema.getInstance().getTableInfoStudyData(), deleteFilter);
        OntologyManager.deleteOntologyObjects(c, deleteSet.toArray(new String[0]));

        return null;
    }

    public String[] importDatasetData(Study study, User user, DataSetDefinition def,
                                      DataLoader loader, long lastModified,
                                      Map<String,String> columnMap, List<String> errors,
                                      boolean checkDuplicates,
                                      QCState defaultQCState)
        throws IOException, ServletException, SQLException
    {
        Map<String, Object>[] dataMaps = parseData(def, loader, columnMap, errors);
        return importDatasetData(study, user, def, dataMaps, lastModified, errors, checkDuplicates, defaultQCState);
    }


    public String[] importDatasetTSV(Study study, User user, DataSetDefinition def, String tsv, long lastModified,
                                     Map<String, String> columnMap, List<String> errors, boolean checkDuplicates, QCState defaultQCState)
            throws IOException, ServletException, SQLException
    {
        DataLoader loader = new TabLoader(tsv, true);
        return importDatasetData(study, user, def, loader, lastModified, columnMap, errors, checkDuplicates, defaultQCState);
    }

    /**
     * dataMaps have keys which are property URIs, and values which have already been converted.
     */
    public String[] importDatasetData(Study study, User user, DataSetDefinition def, Map<String, Object>[] dataMaps, long lastModified,
                                      List<String> errors, boolean checkDuplicates, QCState defaultQCState)
            throws IOException, ServletException, SQLException
    {
        if (dataMaps.length == 0)
            return new String[0];

        Container c = study.getContainer();
        TableInfo tinfo = def.getTableInfo(null, false, false);
        String[] imported = new String[0];

        Map<String, QCState> qcStateLabels =  new CaseInsensitiveHashMap<QCState>();

        boolean needToHandleQCState = tinfo.getColumn(DataSetTable.QCSTATE_ID_COLNAME) != null;

        if (needToHandleQCState)
        {
            for (QCState state : StudyManager.getInstance().getQCStates(study.getContainer()))
                qcStateLabels.put(state.getLabel(), state);
            if (tinfo.getColumn(DataSetTable.QCSTATE_ID_COLNAME) != null)
            {
                for (Map<String,Object> dataMap : dataMaps)
                {
                    String qcStateLabel = (String) dataMap.get(DataSetTable.QCSTATE_LABEL_COLNAME);
                    // We have a non-null QC state column value.  We need to check to see if this is a known state,
                    // and mark it for addition if not.
                    if (qcStateLabel != null && qcStateLabel.length() > 0 && !qcStateLabels.containsKey(qcStateLabel))
                        qcStateLabels.put(qcStateLabel, null);
                }
            }
        }

        //
        // Try to collect errors early.
        // Try not to be too repetitive, stop each loop after one error
        //

        for (ColumnInfo col : tinfo.getColumns())
        {
            // lsid is generated
            if (col.getName().equalsIgnoreCase("lsid"))
                continue;
            
            for (int i = 0; i < dataMaps.length; i++)
            {
                Map<String,Object> dataMap = dataMaps[i];
                Object val = dataMap.get(col.getPropertyURI());
                if (null == val && !col.isNullable())
                {
                    // Demographic data gets special handling for visit or date fields, depending on the type of study,
                    // since there is usually only one entry for demographic data per dataset
                    if (def.isDemographicData())
                    {
                        if (study.isDateBased())
                        {
                            if (col.getName().equalsIgnoreCase("Date"))
                            {
                                // Yuck! The Map we get here isn't really a Map,
                                // so we need to construct a copy and update our entry
                                dataMap = new CaseInsensitiveHashMap<Object>(dataMap);
                                dataMap.put(col.getPropertyURI(), study.getStartDate());
                                dataMaps[i] = dataMap;
                                continue;
                            }
                        }
                        else
                        {
                            if (col.getName().equalsIgnoreCase("SequenceNum"))
                            {
                                // See above
                                dataMap = new CaseInsensitiveHashMap<Object>(dataMap);

                                // We introduce a sentinel, 0, as our SequenceNum
                                dataMap.put(col.getPropertyURI(), 0);
                                dataMaps[i] = dataMap;
                                continue;
                            }
                        }
                    }

                    errors.add("Row " + (i + 1) + " does not contain required field " + col.getName() + ".");
                    break;
                }
                else if (val == CONVERSION_ERROR)
                {
                    errors.add("Row " + (i+1) + " data type error for field " + col.getName() + "."); // + " '" + String.valueOf(val) + "'.");
                    break;
                }
            }
        }

        if (errors.size() > 0)
            return imported;

        String keyPropertyURI = null;
        String keyPropertyName = def.getKeyPropertyName();
        if (keyPropertyName != null)
        {
            ColumnInfo col = tinfo.getColumn(keyPropertyName);
            if (null != col)
                keyPropertyURI = col.getPropertyURI();
        }
        
        if (checkDuplicates)
        {
            String participantIdURI = DataSetDefinition.getParticipantIdURI();
            String visitSequenceNumURI = DataSetDefinition.getSequenceNumURI();
            String visitDateURI = DataSetDefinition.getVisitDateURI();

            HashMap<String,Map> failedReplaceMap = checkAndDeleteDups(study, def, dataMaps);
            if (null != failedReplaceMap && failedReplaceMap.size() > 0)
            {
                StringBuilder error = new StringBuilder();
                error.append("Only one row is allowed for each Participant");
                if (!def.isDemographicData())
                {
                     error.append(study.isDateBased() ? "/Date" : "/Visit");
                    if (def.getKeyPropertyName() != null)
                        error.append("/").append(def.getKeyPropertyName()).append(" Triple.  ");
                    else
                        error.append(" Pair.  ");
                }
                else if (def.getKeyPropertyName() != null)
                {
                    error.append("/").append(def.getKeyPropertyName()).append(" Pair.  ");
                }
                error.append("Duplicates were found in the database or imported data.");
                errors.add(error.toString());
                for (Map.Entry<String,Map> e : failedReplaceMap.entrySet())
                {
                    Map m = e.getValue();
                    String err = "Duplicate: Participant = " + m.get(participantIdURI);
                    if (!def.isDemographicData())
                    {
                        if (study.isDateBased())
                            err = err + "Date = " + m.get(visitDateURI);
                        else
                            err = err + ", VisitSequenceNum = " + m.get(visitSequenceNumURI);
                    }
                    if (keyPropertyURI != null)
                        err += ", " + keyPropertyName + " = " + m.get(keyPropertyURI);
                    errors.add(err);
                }
            }
        }
        if (errors.size() > 0)
            return imported;

        DbScope scope =  ExperimentService.get().getSchema().getScope();
        boolean startedTransaction = false;
        DatasetImportHelper helper = null;

        try
        {
            // If additional keys are managed by the server, we need to synchronize around
            // increments, as we're imitating a sequence. If they aren't managed, no need for
            // synchro, so we'll use a new object.
            Object lock = MANAGED_KEY_LOCK;
            if (!def.isKeyPropertyManaged())
                lock = new Object();

            synchronized (lock)
            {
                if (!scope.isTransactionActive())
                {
                    startedTransaction = true;
                    scope.beginTransaction();
                }

                if (needToHandleQCState)
                {
                    // We first insert new QC states for any previously unknown QC labels found in the data:
                    Map<String, QCState> iterableStates = new HashMap<String, QCState>(qcStateLabels);
                    for (Map.Entry<String, QCState> state : iterableStates.entrySet())
                    {
                        if (state.getValue() == null)
                        {
                            QCState newState = new QCState();
                            // default to public data:
                            newState.setPublicData(true);
                            newState.setLabel(state.getKey());
                            newState.setContainer(study.getContainer());
                            newState = insertQCState(user, newState);
                            qcStateLabels.put(state.getKey(), newState);
                        }
                    }

                    // All QC states should now be stored in the database.  Next we iterate the row maps,
                    // swapping in the appropriate row id for each QC label, and applying the default QC state
                    // to null QC rows if appropriate:
                    String qcStatePropertyURI = DataSetDefinition.getQCStateURI();
                    for (Map<String, Object> dataMap : dataMaps)
                    {
                        // only update the QC state ID if it isn't already explicitly specified:
                        if (dataMap.get(qcStatePropertyURI) == null)
                        {
                            Object currentStateObj = dataMap.get(DataSetTable.QCSTATE_LABEL_COLNAME);
                            String currentStateLabel = currentStateObj != null ? currentStateObj.toString() : null;
                            if (currentStateLabel != null)
                            {
                                QCState state = qcStateLabels.get(currentStateLabel);
                                assert state != null : "QC State " + currentStateLabel + " was expected but not found.";
                                dataMap.put(qcStatePropertyURI, state.getRowId());
                            }
                            else if (defaultQCState != null)
                                dataMap.put(qcStatePropertyURI, defaultQCState.getRowId());
                        }
                    }
                }
                //
                // Use OntologyManager for bulk insert
                //
                // CONSIDER: it would nice if we could use the Table/TableInfo methods here

                // Need to generate keys if the server manages them
                if (def.isKeyPropertyManaged())
                {
                    int currentKey = getMaxKeyValue(def, user);
                    // Sadly, may have to create new maps, since TabLoader's aren't modifyable
                    for (int i=0;i<dataMaps.length;i++)
                    {
                        // Only insert if there isn't already a value
                        if (dataMaps[i].get(keyPropertyURI) == null)
                        {
                            currentKey++;
                            Map data = new HashMap(dataMaps[i]);
                            data.put(keyPropertyURI, currentKey);
                            dataMaps[i] = data;
                        }
                    }
                }

                String typeURI = def.getTypeURI();
                PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(typeURI, c);
                helper = new DatasetImportHelper(scope.getConnection(), c, def, lastModified);
                imported = OntologyManager.insertTabDelimited(c, null, helper, pds, dataMaps, true);

                if (startedTransaction)
                {
                    scope.commitTransaction();
                    startedTransaction = false;
                }
            }
            _dataSetHelper.clearCache(def);
        }
        catch (ValidationException ve)
        {
            for (ValidationError error : ve.getErrors())
                errors.add(error.getMessage());
        }
        finally
        {
            if (helper != null)
                helper.done();
            if (startedTransaction)
            {
                scope.rollbackTransaction();
                imported = new String[0];
            }
        }
        return imported;
    }

    /**
     * Gets the current highest key value for a server-managed key field.
     * If no data is returned, this method returns 0.
     */
    private int getMaxKeyValue(DataSetDefinition dataset, User user) throws SQLException
    {
        TableInfo tInfo;
        try
        {
            tInfo = dataset.getTableInfo(user);
        }
        catch (ServletException se)
        {
            throw new SQLException("Could not get tableInfo: " + se.getMessage());
        }
        String keyName = tInfo.getColumn(dataset.getKeyPropertyName()).getSelectName();
        Integer newKey = Table.executeSingleton(tInfo.getSchema(),
                "SELECT COALESCE(MAX(" + keyName + "), 0) FROM " + tInfo,
                new Object[0],
                Integer.class
                );
        return newKey.intValue();
    }


    /** NOTE: this is usually handled at import time, this is only useful
     * if DataSetDefinition.visitDatePropertyName changes
     *
     * @param study
     */
    public void recomputeStudyDataVisitDate(Study study)
    {
        DataSetDefinition[] defs = study.getDataSets();
        for (DataSetDefinition def : defs)
            recomputeStudyDataVisitDate(study, def);
    }


    private void recomputeStudyDataVisitDate(Study study, DataSetDefinition def)
    {
        String propertyName = StringUtils.trimToNull(def.getVisitDatePropertyName());
        if (null == propertyName)
            return;
        if (null == StringUtils.trimToNull(def.getTypeURI()))
            return;
        PropertyDescriptor pds[] = OntologyManager.getPropertiesForType(def.getTypeURI(), study.getContainer());
        if (pds == null)
            return;
        PropertyDescriptor pdVisitDate = null;
        for (PropertyDescriptor pd : pds)
        {
            if (propertyName.equalsIgnoreCase(pd.getName()))
            {
                pdVisitDate = pd;
                break;
            }
        }
        if (pdVisitDate == null)
            return;

        try
        {
            DbSchema schema = StudySchema.getInstance().getSchema();
            String sqlUpdate =
                    "UPDATE study.StudyData SET _VisitDate=(SELECT datetimevalue FROM exp.Object O JOIN exp.ObjectProperty OP ON O.ObjectId=OP.ObjectId WHERE O.Container=? AND O.ObjectURI=LSID AND OP.PropertyId=?)\n" +
                    "WHERE Container=? AND DataSetId=?";
            Table.execute(schema, sqlUpdate, new Object[] {study.getContainer(), pdVisitDate.getPropertyId(), study.getContainer(), def.getDataSetId()} );
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }


    public VisitManager getVisitManager(Study study)
    {
        if (!study.isDateBased())
            return new SequenceVisitManager(study);
        else
            return new DateVisitManager(study);
    }

    private static final String STUDY_FORMAT_STRINGS = "DefaultStudyFormatStrings";
    private static final String DATE_FORMAT_STRING = "DateFormatString";
    private static final String NUMBER_FORMAT_STRING = "NumberFormatString";

    public String getDefaultDateFormatString(Container c)
    {
        return (String)getFormatStrings(c).get(DATE_FORMAT_STRING);
    }

    public String getDefaultNumberFormatString(Container c)
    {
        return (String)getFormatStrings(c).get(NUMBER_FORMAT_STRING);
    }

    private  Map<String, String> getFormatStrings(Container c)
    {
        Map<String, String> formatStrings = PropertyManager.getProperties(c.getId(), STUDY_FORMAT_STRINGS, false);
        if (formatStrings == null)
            return Collections.emptyMap();

        return formatStrings;
    }

    public void setDefaultDateFormatString(Container c, String format)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(0, c.getId(), STUDY_FORMAT_STRINGS, true);

        if (!StringUtils.isEmpty(format))
            props.put(DATE_FORMAT_STRING, format);
        else if (props.containsKey(DATE_FORMAT_STRING))
            props.remove(DATE_FORMAT_STRING);
        PropertyManager.saveProperties(props);
    }

    public void setDefaultNumberFormatString(Container c, String format)
    {
        Map<String, String> props = PropertyManager.getWritableProperties(0, c.getId(), STUDY_FORMAT_STRINGS, true);
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

    private static final long MILLIS_PER_DAY = 1000 * 60 * 60 * 24;
    //Create a fixed point number encoding the date.
    public static double sequenceNumFromDate(Date d)
    {
        Calendar cal = DateUtil.newCalendar(d.getTime());
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
    }

    private boolean canFormat(DisplayColumn dc)
    {
        final ColumnInfo col = dc.getColumnInfo();
        if (col != null)
            return !col.isFormatStringSet();
        return dc.getFormatString() == null;
    }

    static class DatasetImportHelper implements OntologyManager.ImportHelper
    {
        final String _containerId;
        final int _datasetId;
        final String _urnPrefix;
        final Connection _conn;
        PreparedStatement _stmt = null;
        final Long _lastModified;
        final String _visitDatePropertyURI;
        final String _keyPropertyURI;
        final Study _study;
        final DataSetDefinition _dataset;

        TableInfo tinfo = StudySchema.getInstance().getTableInfoStudyData();

        DatasetImportHelper(Connection conn, Container c, DataSetDefinition dataset, long lastModified) throws SQLException, ServletException
        {
            _containerId = c.getId();
            _study = StudyManager.getInstance().getStudy(c);
            _datasetId = dataset.getDataSetId();
            _dataset = StudyManager.getInstance().getDataSetDefinition(_study, _datasetId);
            _urnPrefix = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + c.getRowId() + ":" + _datasetId + ".";
            _conn = conn;
            _lastModified = lastModified;
            if (null != conn)
            {
                _stmt = conn.prepareStatement(
                        "INSERT INTO " + tinfo + " (Container, DatasetId, ParticipantId, SequenceNum, LSID, _VisitDate, Created, Modified, SourceLsid, _key, QCState) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?, ?)");
                _stmt.setString(1, _containerId);
                _stmt.setInt(2, _datasetId);
            }

            String visitDatePropertyURI = null;
            String keyPropertyURI = null;
            for (ColumnInfo col : dataset.getTableInfo(null, false, false).getColumns())
            {
                if (col.getName().equalsIgnoreCase(dataset.getVisitDatePropertyName()))
                    visitDatePropertyURI = col.getPropertyURI();
                if (col.getName().equalsIgnoreCase(dataset.getKeyPropertyName()))
                    keyPropertyURI = col.getPropertyURI();
            }

            _visitDatePropertyURI = null == visitDatePropertyURI ? visitDateURI : visitDatePropertyURI;
            _keyPropertyURI = keyPropertyURI;
        }


        static double toDouble(Object i)
        {
            if (i == null)
                return 0;
            else if (i instanceof Number)
                return ((Number) i).doubleValue();
            throw new IllegalArgumentException("Unexpected type " + i.getClass() + ": " + i);
        }


        static String participantURI = DataSetDefinition.getParticipantIdURI();
        static String visitSequenceNumURI = DataSetDefinition.getSequenceNumURI();
        static String visitDateURI = DataSetDefinition.getVisitDateURI();
        static String createdURI = DataSetDefinition.getCreatedURI();
        static String modifiedURI = DataSetDefinition.getModifiedURI();
        static String sourceLsidURI = DataSetDefinition.getSourceLsidURI();
        static String qcStateURI = DataSetDefinition.getQCStateURI();


        public String getURI(Map map)
        {
            String ptid = String.valueOf(map.get(participantURI));
            double visit;
            if (_study.isDateBased())
            {
                Date date = (Date) map.get(visitDateURI);
                if (null != date)
                    visit = sequenceNumFromDate(date);
                else
                    visit = Visit.DEMOGRAPHICS_VISIT;
            }
            else
                visit = toDouble(map.get(DataSetDefinition.getSequenceNumURI()));
            String uri = _urnPrefix + visit + "." + ptid;
            if (null != _keyPropertyURI)
            {
                Object key = map.get(_keyPropertyURI);
                if (null != key)
                    uri += "." + String.valueOf(key);
            }
            return uri;
        }


        public String beforeImportObject(Map map) throws SQLException
        {
            if (null == _stmt)
                throw new IllegalStateException("No connection provided");

            String uri = getURI(map);
            String ptid = String.valueOf(map.get(participantURI));
            double visit;
            if (_study.isDateBased())
            {
                Date date = (Date) map.get(visitDateURI);
                if (null != date)
                    visit = sequenceNumFromDate(date);
                else
                    visit = Visit.DEMOGRAPHICS_VISIT;
            }
            else
                visit = toDouble(map.get(visitSequenceNumURI));
            Object key = null == _keyPropertyURI ? null : map.get(_keyPropertyURI);

            Object created = map.get(createdURI);
            Long timeCreated = null == created ? _lastModified : toMs(created);
            Object modified = map.get(modifiedURI);
            Long timeModified = null == modified ? _lastModified : toMs(modified);
            Long visitDate = toMs(map.get(_visitDatePropertyURI));
            assert !_study.isDateBased() || null != visitDate;
            String sourceLsid = (String) map.get(sourceLsidURI);
            Integer qcState = (Integer) map.get(qcStateURI);

            _stmt.setString(3, ptid);
            _stmt.setDouble(4, visit);
            _stmt.setString(5, uri); // LSID
            _stmt.setTimestamp(6, null == visitDate ? null : new Timestamp(visitDate));
            _stmt.setTimestamp(7, null == timeCreated ? null : new Timestamp(timeCreated));
            _stmt.setTimestamp(8, null == timeModified ? null : new Timestamp(timeModified));
            _stmt.setString(9, sourceLsid);
            _stmt.setString(10, key == null ? "" : String.valueOf(key));
            if (qcState != null)
                _stmt.setInt(11, qcState.intValue());
            else
                _stmt.setNull(11, Types.INTEGER);
            _stmt.execute();
            return uri;
        }


        private Long toMs(Object date)
        {
            if (null == date)
                return null;
            if (date instanceof String)
            {
                try{ return DateUtil.parseDateTime((String)date);}
                catch (ConversionException x) { return null; }
            }
            if (date instanceof Date)
                return ((Date)date).getTime();
            return null;
        }


        public void afterImportObject(String lsid, ObjectProperty[] props) throws SQLException
        {
        }


        public void done()
        {
            try
            {
                if (null != _stmt)
                    _stmt.close();
                _stmt = null;
            }
            catch (SQLException x)
            {
                logError(x);
            }
        }
    }


    private static void logError(Exception x)
    {
        Logger.getLogger(StudyManager.class).error("unexpected error", x);
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
                    ActionURL url = new ActionURL("Study", "dataset", c);
                    url.addParameter(DataSetDefinition.DATASETKEY, String.valueOf(datasetId));
                    url.addParameter(Visit.SEQUENCEKEY, String.valueOf(sequenceNum));
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
    }

    public Participant[] getParticipants(Study study) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", study.getContainer());
        return Table.select(StudySchema.getInstance().getTableInfoParticipant(), Table.ALL_COLUMNS,
                filter, new Sort("ParticipantId"), Participant.class);
    }

    public Participant getParticipant(Study study, String participantId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", study.getContainer());
        filter.addCondition("ParticipantId", participantId);
        return Table.selectObject(StudySchema.getInstance().getTableInfoParticipant(), Table.ALL_COLUMNS,
                filter, new Sort("ParticipantId"), Participant.class);
    }

    public void clearParticipantCohorts(User user, Study study) throws SQLException, ServletException
    {
        // null out cohort for all participants in this container:
        Table.execute(StudySchema.getInstance().getSchema(),
                "UPDATE study.Participant SET CohortId = NULL WHERE Container = ?", new Object[] { study.getContainer().getId() });
    }

    public void updateParticipantCohorts(User user, Study study) throws SQLException, ServletException
    {
        if (study.isManualCohortAssignment() ||
                study.getParticipantCohortDataSetId() == null ||
                study.getParticipantCohortProperty() == null)
        {
            return;
        }
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        boolean transactionOwner = !scope.isTransactionActive();
        try
        {
            if (transactionOwner)
                scope.beginTransaction();
            DataSetDefinition dataset = study.getDataSet(study.getParticipantCohortDataSetId());
            TableInfo tableParticipant = StudySchema.getInstance().getTableInfoParticipant();
            TableInfo cohortDatasetTinfo = dataset.getTableInfo(null, false, true);
            //TODO: Use Property URI & Make sure this is set properly
            ColumnInfo cohortLabelCol = cohortDatasetTinfo.getColumn(study.getParticipantCohortProperty());
            if (null != cohortLabelCol)
            {
                clearParticipantCohorts(user, study);

                // Find the set of cohorts specified in our dataset
                SQLFragment sqlFragment = new SQLFragment();
                sqlFragment.append("SELECT DISTINCT " + cohortLabelCol.getSelectName() + " FROM " + cohortDatasetTinfo +
                        "\nWHERE " + cohortLabelCol.getSelectName() + " IS NOT NULL AND " + cohortLabelCol.getSelectName() + " NOT IN\n" +
                        " (SELECT Label FROM " + StudySchema.getInstance().getTableInfoCohort() + " WHERE Container = ?)");
                sqlFragment.add(study.getContainer().getId());

                Set<String> newCohortLabels = new HashSet<String>();
                Table.TableResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sqlFragment);
                try
                {
                    while (rs.next())
                    {
                        newCohortLabels.add(rs.getString(1));
                    }
                }
                finally
                {
                    rs.close();
                }

                for (String cohortLabel : newCohortLabels)
                {
                    Cohort cohort = new Cohort();
                    cohort.setLabel(cohortLabel);
                    StudyManager.getInstance().createCohort(study, user, cohort);
                }

                // update participants table to reference correct cohorts:
                String datasetSelect = "SELECT MAX(" + cohortLabelCol.getSelectName() + ") FROM (\n" +
                        "\tSELECT " + cohortLabelCol.getSelectName() + ", " + cohortDatasetTinfo.getAliasName() + ".ParticipantId FROM " + cohortDatasetTinfo + ", (\n" +
                        "\t\tSELECT ParticipantId, max(sequencenum) AS SequenceNum FROM " + cohortDatasetTinfo + " GROUP BY ParticipantId\n" +
                        "\t) As LastVisit \n" +
                        "\tWHERE " + cohortDatasetTinfo.getAliasName() + ".ParticipantId = LastVisit.ParticipantId AND " +
                        cohortDatasetTinfo.getAliasName() + ".SequenceNum = LastVisit.SequenceNum\n" +
                        ") AS DupEliminationTable GROUP BY ParticipantId HAVING ParticipantId = " + tableParticipant + ".ParticipantId";

                String cohortIdSelect = "SELECT RowId FROM " + StudySchema.getInstance().getTableInfoCohort() +
                        " WHERE Label = (" + datasetSelect + ") AND Container = ?";

                String sql = "UPDATE " + tableParticipant + " SET CohortId = (\n" + cohortIdSelect + "\n) WHERE Container = ? AND (" +
                        tableParticipant + ".CohortId IS NULL OR NOT " + tableParticipant + ".CohortId = \n(" + cohortIdSelect + "))";

                Table.execute(getSchema(), sql, new Object[] {
                        study.getContainer().getId(),
                        study.getContainer().getId(),
                        study.getContainer().getId() });
                if (transactionOwner)
                    scope.commitTransaction();
            }
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

    public CustomParticipantView getCustomParticipantView(Study study) throws SQLException
    {
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
            return Table.update(user, StudySchema.getInstance().getTableInfoParticipantView(), view, view.getRowId(), null);
        }
    }

    /**
     * Returns a domain URI for use by Ontology Manager
     */
    public String getDomainURI(Container container, Class<? extends ExtensibleStudyEntity> clazz)
    {
        return new Lsid(clazz.getSimpleName(),
                "Folder-" + container.getRowId(),
                clazz.getSimpleName()).toString();
    }

    /**
     * Creates and lsid for this individual extensible object
     */
    public String createLsid(ExtensibleStudyEntity o, int uniqueId)
    {
        return new Lsid(o.getClass().getSimpleName(), "Folder-" + o.getContainer().getRowId(), Integer.toString(uniqueId)).toString();
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
        return new BaseStudyController.StudyJspView<ParticipantViewConfig>(getStudy(container), "participantAll.jsp", config, errors);
    }

    public WebPartView<ParticipantViewConfig> getParticipantDemographicsView(Container container, ParticipantViewConfig config, BindException errors)
    {
        return new BaseStudyController.StudyJspView<ParticipantViewConfig>(getStudy(container), "participantCharacteristics.jsp", config, errors);
    }

    public interface UnmaterializeListener
    {
        void dataSetUnmaterialized(DataSetDefinition def);
    }

    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<UnmaterializeListener> _listeners = new CopyOnWriteArrayList<UnmaterializeListener>();

    public static void addUnmaterializeListener(UnmaterializeListener listener)
    {
        _listeners.add(listener);
    }

    private static List<UnmaterializeListener> getListeners()
    {
        return _listeners;
    }
    
    public static void fireUnmaterialized(DataSetDefinition def)
    {
        List<UnmaterializeListener> list = getListeners();
        for (UnmaterializeListener l : list)
            try
            {
                l.dataSetUnmaterialized(def);
            }
            catch (Throwable t)
            {
                _log.error("fireUnmaterialized", t);
            }
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
