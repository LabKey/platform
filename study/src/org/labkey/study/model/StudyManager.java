/*
 * Copyright (c) 2006-2011 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AuditLogEvent;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.Parameter;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableInfoGetter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.module.Module;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.ActionResource;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.study.QueryHelper;
import org.labkey.study.SampleManager;
import org.labkey.study.StudyCache;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
    private final TableInfo _tableInfoUploadLog;

    private final QueryHelper<StudyImpl> _studyHelper;
    private final QueryHelper<VisitImpl> _visitHelper;
    private final QueryHelper<SiteImpl> _siteHelper;
    private final DataSetHelper _dataSetHelper;
    private final QueryHelper<CohortImpl> _cohortHelper;

    private Map<String, Resource> _moduleParticipantViews = null;

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
            }, StudyImpl.class)
        {
            public StudyImpl[] get(final Container c, final SimpleFilter filterArg, final String sortString) throws SQLException
            {
                assert filterArg == null & sortString == null;
                String cacheId = getCacheId(filterArg);
                if (sortString != null)
                    cacheId += "; sort = " + sortString;

                CacheLoader<String,Object> loader = new CacheLoader<String,Object>()
                {
                    @Override
                    public Object load(String key, Object argument)
                    {
                        try
                        {
                            StudyCachable[] objs = Table.executeQuery(StudyManager.getSchema(), "SELECT * FROM study.study WHERE Container = ?", new Object[]{c}, StudyImpl.class);
                            for (StudyCachable obj : objs)
                                obj.lock();
                            return objs;
                        }
                        catch (SQLException x)
                        {
                            throw new RuntimeSQLException(x);
                        }
                    }
                };
                return (StudyImpl[]) StudyCache.get(getTableInfo(), c.getId(), cacheId, loader);
            }

        };

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
        //_tableInfoStudyData = StudySchema.getInstance().getTableInfoStudyData(null);
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
        return getAllStudies(root, user, ReadPermission.class);
    }

    @NotNull
    public Study[] getAllStudies(Container root, User user, Class<? extends Permission> perm) throws SQLException
    {
        FilteredTable t = new FilteredTable(StudySchema.getInstance().getTableInfoStudy(), root, new ContainerFilter.CurrentAndSubfolders(user, perm));
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

        if (oldStudy.getTimepointType() == TimepointType.DATE && !PageFlowUtil.nullSafeEquals(study.getStartDate(), oldStartDate))
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
        indexDataset(null, dataSetDefinition);
    }


    public void updateDataSetDefinition(User user, DataSetDefinition dataSetDefinition) throws SQLException
    {
        DbScope scope = getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            DataSetDefinition old = getDataSetDefinition(dataSetDefinition.getStudy(), dataSetDefinition.getDataSetId());
            if (null == old)
                throw Table.OptimisticConflictException.create(Table.ERROR_DELETED);

            Domain domain = dataSetDefinition.getDomain();

            // Check if the extra key field has changed
            if (domain != null && domain.getStorageTableName() != null && !PageFlowUtil.nullSafeEquals(old.getKeyPropertyName(), dataSetDefinition.getKeyPropertyName()))
            {
                // If so, we need to update the _key column and the LSID

                // Set the _key column to be the value of the selected column
                // Change how we build up tableName
                String tableName = dataSetDefinition.getStorageTableInfo().toString();
                SQLFragment updateKeySQL = new SQLFragment("UPDATE " + tableName + " SET _key = ");
                if (dataSetDefinition.getKeyPropertyName() == null)
                {
                    // No column selected, so set it to be null
                    updateKeySQL.append("NULL");
                }
                else
                {
                    updateKeySQL.append("\"" + dataSetDefinition.getKeyPropertyName().toLowerCase() + "\"");
                }
                Table.execute(getSchema(), updateKeySQL);

                // Now update the LSID column. Note - this needs to be the same as DatasetImportHelper.getURI()
                SQLFragment updateLSIDSQL = new SQLFragment("UPDATE " + tableName + " SET lsid = ");
                updateLSIDSQL.append(dataSetDefinition.getLSIDSQL());
                Table.execute(getSchema(), updateLSIDSQL);
            }   
            Object[] pk = new Object[]{dataSetDefinition.getContainer().getId(), dataSetDefinition.getDataSetId()};
            _dataSetHelper.update(user, dataSetDefinition, pk);

            if (!old.getLabel().equals(dataSetDefinition.getLabel()))
            {
                QueryService.get().updateCustomViewsAfterRename(dataSetDefinition.getContainer(), StudyQuerySchema.SCHEMA_NAME,
                        old.getLabel(), dataSetDefinition.getLabel());
            }
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();

            uncache(dataSetDefinition);
        }
        indexDataset(null, dataSetDefinition);
    }


    public boolean isDataUniquePerParticipant(DataSetDefinition dataSet) throws SQLException
    {
        // don't use dataSet.getTableInfo() since this method is called during updateDatasetDefinition() and may be in an inconsistent state
        TableInfo t = dataSet.getStorageTableInfo();
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT max(n) FROM (select count(*) AS n from ").append(t.getFromSQL("DS")).append(" group by participantid) x");
        Integer maxCount = Table.executeSingleton(getSchema(), sql.getSQL(), sql.getParamsArray(), Integer.class);
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


    // TODO: Should be able to send List<Bean> to bulk insert method, so we don't have to translate like this
    public void importVisitAliases(Study study, User user, List<VisitAlias> aliases) throws IOException, ValidationException, SQLException
    {
        List<Map<String, Object>> maps = new LinkedList<Map<String, Object>>();

        for (VisitAlias alias : aliases)
        {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("Name", alias.getName());
            map.put("SequenceNum", alias.getSequenceNum());
            maps.add(map);
        }

        importVisitAliases(study, user, new MapLoader(maps));
    }


    public int importVisitAliases(final Study study, User user, DataLoader loader) throws SQLException, IOException, ValidationException
    {
        SimpleFilter containerFilter = new SimpleFilter("Container", study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        // TODO: ETL changes should eliminate need for this thing
        OntologyManager.UpdateableTableImportHelper helper = new OntologyManager.UpdateableTableImportHelper() {
            @Override
            public void afterImportObject(Map<String, Object> map) throws SQLException
            {
            }

            @Override
            public void bindAdditionalParameters(Map<String, Object> map, Parameter.ParameterMap target)
            {
                target.put("Container", study.getContainer());
            }

            @Override
            public String beforeImportObject(Map<String, Object> map) throws SQLException
            {
                return null;
            }

            @Override
            public void afterBatchInsert(int currentRow) throws SQLException
            {
            }

            @Override
            public void updateStatistics(int currentRow) throws SQLException
            {
            }
        };

        try
        {
            // We want delete and bulk insert in the same transaction
            scope.ensureTransaction();

            clearVisitAliases(study);
            List<String> keys = OntologyManager.insertTabDelimited(tinfo, study.getContainer(), user, helper, loader.load(), null);

            scope.commitTransaction();

            return keys.size();
        }
        finally
        {
            scope.closeConnection();
        }
    }


    public void clearVisitAliases(Study study) throws SQLException
    {
        SimpleFilter containerFilter = new SimpleFilter("Container", study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            Table.delete(tinfo, containerFilter);
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
    }


    public Map<String, Double> getVisitImportMap(Study study, boolean includeStandardMapping) throws SQLException
    {
        VisitAlias[] aliases = getVisitAliasesArray(study, null);

        Map<String, Double> map = new CaseInsensitiveHashMap<Double>(aliases.length * 3 / 4);

        for (VisitAlias alias : aliases)
            map.put(alias.getName(), alias.getSequenceNum());

        if (includeStandardMapping)
        {
            // TODO
        }

        return map;
    }


    // Return the custom import mapping (optinally provided by the admin), ordered by sequence num.  Ordering is nice
    // for UI and export, but unnecessary for importing data.
    public Collection<VisitAlias> getCustomVisitImportMapping(Study study) throws SQLException
    {
        return Arrays.asList(getVisitAliasesArray(study, new Sort("SequenceNum")));
    }


    private VisitAlias[] getVisitAliasesArray(Study study, @Nullable Sort sort) throws SQLException
    {
        SimpleFilter containerFilter = new SimpleFilter("Container", study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();

        return Table.select(tinfo, tinfo.getColumns("Name, SequenceNum"), containerFilter, sort, VisitAlias.class);
    }


    // Return the standard import mapping (generated from Visit.Label -> Visit.SequenceNumMin), ordered by sequence
    // num for display purposes.  Include VisitAliases that won't be used, but mark them as overridden.
    public Collection<VisitAlias> getStandardVisitImportMapping(Study study) throws SQLException
    {
        List<VisitAlias> list = new LinkedList<VisitAlias>();
        Set<String> labels = new CaseInsensitiveHashSet();
        Map<String, Double> customMap = getVisitImportMap(study, false);

        Visit[] visits = StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM);

        for (Visit visit : visits)
        {
            String label = visit.getLabel();

            if (null != visit.getLabel())
            {
                boolean overridden = labels.contains(label) || customMap.containsKey(label);
                list.add(new VisitAlias(label, visit.getSequenceNumMin(), overridden));

                if (!overridden)
                    labels.add(label);
            }
        }

        return list;
    }


    public static class VisitAlias
    {
        private String _name;
        private double _sequenceNum;
        private boolean _overridden;  // For display purposes -- we show all visits and gray out the ones that are not used

        @SuppressWarnings({"UnusedDeclaration"}) // Constructed by reflection by the Table layer
        public VisitAlias()
        {
        }

        public VisitAlias(String name, double sequenceNum, boolean overridden)
        {
            _name = name;
            _sequenceNum = sequenceNum;
            _overridden = overridden;
        }

        public VisitAlias(String name, double sequenceNum)
        {
            this(name, sequenceNum, false);
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public double getSequenceNum()
        {
            return _sequenceNum;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSequenceNum(double sequenceNum)
        {
            _sequenceNum = sequenceNum;
        }

        public boolean isOverridden()
        {
            return _overridden;
        }
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
            schema.getSchema().getScope().ensureTransaction();

            for (DataSetDefinition def : study.getDataSets())
            {
                TableInfo t = def.getStorageTableInfo();
                if (null == t)
                    continue;

                SQLFragment sqlf = new SQLFragment();
                sqlf.append("DELETE FROM " + t.getSelectName() + " WHERE SequenceNum BETWEEN ? AND ?");
                sqlf.add(visit.getSequenceNumMin());
                sqlf.add(visit.getSequenceNumMax());
                int count = Table.execute(schema.getSchema(), sqlf);
                if (count > 0)
                    StudyManager.fireDataSetChanged(def);
            }

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

            getVisitManager(study).updateParticipantVisits(user, study.getDataSets());
        }
        finally
        {
            schema.getSchema().getScope().closeConnection();
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
            SQLFragment f = new SQLFragment();
            f.append("SELECT COUNT(*) FROM ").append(
                    StudySchema.getInstance().getTableInfoStudyData(study, null).getFromSQL("SD")).append(
                    " WHERE QCState = ?");
            f.add(state.getRowId());
            Integer count = Table.executeSingleton(StudySchema.getInstance().getSchema(), f.getSQL(), f.getParamsArray(), Integer.class);
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

        DataSetDefinition def = getDataSetDefinition(getStudy(container), datasetId);
        TableInfo ds = def.getTableInfo(null, false);

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT sd.LSID AS LSID, v.RowId AS RowId FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = ? AND v.Container = ?\n" +
                "WHERE sd.lsid IN(");
        sql.add(container.getId());
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
                auditKey.append(", Visit ").append(visit != null ? visit.getLabel() : "unknown");
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
            scope.ensureTransaction();
            // TODO fix updating across study data
            SQLFragment sql = new SQLFragment("UPDATE " + def.getStorageTableInfo().getSelectName() + "\n" +
                    "SET QCState = ");
            // do string concatenation, rather that using a parameter, for the new state id because Postgres null
            // parameters are typed which causes a cast exception trying to set the value back to null (bug 6370)
            sql.append(newState != null ? newState.getRowId() : "NULL");
            sql.append(", modified = ?");
            sql.add(new Date());
            sql.append("\nWHERE lsid IN (");
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

            //def.deleteFromMaterialized(user, updateLsids);
            //def.insertIntoMaterialized(user, updateLsids);

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

            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
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
            // update old rows w/o entityid
            if (null != ds && null == ds.getEntityId())
            {
                ds.setEntityId(GUID.makeGUID());
                Table.execute(StudySchema.getInstance().getSchema(), "UPDATE study.dataset SET entityId=? WHERE container=? and datasetid=? and entityid IS NULL",
                        new Object[]{ds.getEntityId(), ds.getContainer().getId(), ds.getDataSetId()});
                _dataSetHelper.clearCache(ds);
                ds = _dataSetHelper.get(s.getContainer(), id, "DataSetId");
                // calling updateDataSetDefinition() during load (getDatasetDefinition()) may causesrecursion problem
                //updateDataSetDefinition(null, ds);
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
            filter.addWhereClause("LOWER(Label) = ?", new Object[]{label.toLowerCase()}, "Label");

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
    public DataSetDefinition getDataSetDefinitionByEntityId(Study s, String entityId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", s.getContainer().getId());
            filter.addCondition("EntityId", entityId);

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
            filter.addWhereClause("LOWER(Name) = ?", new Object[]{name.toLowerCase()}, "Name");

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


    // domainURI -> <Container,DatasetId>
    private static Cache<String, Pair<String, Integer>> domainCache = CacheManager.getCache(1000, CacheManager.DAY, "Domain->Dataset map");

    private CacheLoader<String, Pair<String, Integer>> loader = new CacheLoader<String, Pair<String, Integer>>()
    {
        @Override
        public Pair<String, Integer> load(String domainURI, Object argument)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT container, datasetid FROM study.Dataset WHERE TypeURI=?");
            sql.add(domainURI);
            ResultSet rs = null;
            try
            {
                rs = Table.executeQuery(StudySchema.getInstance().getSchema(),sql);
                if (!rs.next())
                    return null;
                else
                    return new Pair<String, Integer>(rs.getString(1), rs.getInt(2));
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
    };


    @Nullable
    DataSetDefinition getDatasetDefinition(String domainURI)
    {
        for (int retry=0 ; retry < 2 ; retry++)
        {
            Pair<String,Integer> p = domainCache.get(domainURI, null, loader);
            if (null == p)
                return null;

            Container c = ContainerManager.getForId(p.first);
            Study study = StudyManager.getInstance().getStudy(c);
            if (null != c && null != study)
            {
                DataSetDefinition ret = StudyManager.getInstance().getDataSetDefinition(study, p.second);
                if (null != ret && StringUtils.equalsIgnoreCase(ret.getDomain().getTypeURI(), domainURI))
                    return ret;
            }
            domainCache.remove(domainURI);
        }
        return null;
    }




    public List<String> getDatasetLSIDs(User user, DataSetDefinition def) throws ServletException, SQLException
    {
        TableInfo tInfo = def.getTableInfo(user, true);
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
        String uri = def.getTypeURI();
        if (null != uri)
            domainCache.remove(uri);
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

    public int getNumDatasetRows(User user, DataSet dataset)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        TableInfo sdTable = dataset.getTableInfo(user, false);

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS numRows FROM ");
        sql.append(sdTable.getFromSQL("ds"));

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
        return dataset.deleteRows(user, cutoff);
    }

    /**
     * Delete all rows from a dataset or just those newer than the cutoff date.
     */
//    public int purgeDatasetOldSchool(Study study, DataSetDefinition dataset, Date cutoff, User user)
//    {
//        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();
//        Container c = study.getContainer();
//        int count;
//
//        TableInfo data = StudySchema.getInstance().getTableInfoStudyData();
//        SimpleFilter filter = new SimpleFilter();
//        filter.addCondition("Container", c.getId());
//        filter.addCondition("DatasetId", dataset.getDataSetId());
//
//        try
//        {
//            CPUTimer time = new CPUTimer("purge");
//            time.start();
//
//            SQLFragment sub = new SQLFragment("SELECT LSID FROM " + data + " " + "WHERE Container = ? and DatasetId = ?",
//                    c.getId(), dataset.getDataSetId());
//            if (cutoff != null)
//                sub.append(" AND _VisitDate > ?").add(cutoff);
//            OntologyManager.deleteOntologyObjects(StudySchema.getInstance().getSchema(), sub, c, false);
//
//            SQLFragment studyDataFrag = new SQLFragment(
//                    "DELETE FROM " + data + "\n" +
//                    "WHERE Container = ? and DatasetId = ?",
//                    c.getId(), dataset.getDataSetId());
//            if (cutoff != null)
//                studyDataFrag.append(" AND _VisitDate > ?").add(cutoff);
//            count = Table.execute(StudySchema.getInstance().getSchema(), studyDataFrag);
//
//            time.stop();
//            _log.debug("purgeDataset " + dataset.getDisplayString() + " " + DateUtil.formatDuration(time.getTotal()/1000));
//        }
//        catch (SQLException s)
//        {
//            throw new RuntimeSQLException(s);
//        }
//        finally
//        {
//            dataset.unmaterialize();
//            StudyManager.fireDataSetChanged(dataset);
//        }
//        return count;
//    }



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
        _dataSetHelper.clearCache(study.getContainer());

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

        StorageProvisioner.drop(ds.getDomain());

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
        deleteAllStudyData(c, null, true, true);
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

        HashSet<TableInfo> deletedTables = new HashSet<TableInfo>();
        SimpleFilter containerFilter = new SimpleFilter("Container", c.getId());

        try
        {
            scope.ensureTransaction();

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
            // Table.delete(StudySchema.getInstance().getTableInfoStudyData(null), containerFilter);
            //assert deletedTables.add(StudySchema.getInstance().getTableInfoStudyData(null));
            Table.delete(StudySchema.getInstance().getTableInfoParticipantVisit(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantVisit());
            Table.delete(StudySchema.getInstance().getTableInfoVisitAliases(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitAliases());
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

            // dataset tables
            for (DataSetDefinition dsd : dsds)
            {
                fireDataSetChanged(dsd);
            }

            scope.commitTransaction();

        }
        finally
        {
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
        {
            if (t.getName().equalsIgnoreCase("studydata") || t.getName().equalsIgnoreCase("studydatatemplate"))
            {
                continue; // fixme.
            }
            if (!deletedTableNames.contains(t.getName()))
            {
                if (!deleteStudyDesigns && isStudyDesignTable(t))
                    continue;

                missed.append(" ");
                missed.append(t.getName());
            }
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
        Object[] params = new Object[lsids.size()];
        String comma = "";
        int i = 0;
        for (String lsid : lsids)
        {
            whereClause.append(comma);
            whereClause.append("?");
            params[i++] = lsid;
            comma = ",";
        }
        whereClause.append(")");
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(whereClause.toString(), params);
        // We can't use the table layer to map results to our bean class because of the unfortunately named
        // "_VisitDate" column in study.StudyData.
        ResultSet rs = null;
        try
        {
            List<ParticipantDataset> pds = new ArrayList<ParticipantDataset>();
            TableInfo sdti = StudySchema.getInstance().getTableInfoStudyData(StudyManager.getInstance().getStudy(container), null);
            rs = Table.select(sdti, Table.ALL_COLUMNS, filter, new Sort("DatasetId"));
            DataSetDefinition dataset = null;
            while (rs.next())
            {
                ParticipantDataset pd = new ParticipantDataset();
                pd.setContainer(container);
                int datasetId = rs.getInt("DatasetId");
                if (dataset == null || datasetId != dataset.getDataSetId())
                    dataset = getDataSetDefinition(getStudy(container), datasetId);
                pd.setDataSetId(datasetId);
                pd.setLsid(rs.getString("LSID"));
                if (!dataset.isDemographicData())
                {
                    pd.setSequenceNum(rs.getDouble("SequenceNum"));
                    pd.setVisitDate(rs.getTimestamp("_VisitDate"));
                }
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


    public static final String CONVERSION_ERROR = "Conversion Error";

    private List<Map<String, Object>> parseData(User user,
                                   DataSetDefinition def,
                                   DataLoader loader,
                                   Map<String, String> columnMap,
                                   List<String> errors)
            throws ServletException, IOException
    {
        TableInfo tinfo = def.getTableInfo(user, false);

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

            if (!matchedCol.isMvIndicatorColumn())
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
            else if (matchedCol.isMvIndicatorColumn())
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

            if (matchedCol.getName().equalsIgnoreCase("createdby") || matchedCol.getName().equalsIgnoreCase("modifiedby"))
            {
                // might be email names instead of userid
                col.clazz = String.class;
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

    public boolean importDatasetSchemas(StudyImpl study, final User user, SchemaReader reader, BindException errors) throws IOException, SQLException
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
                    return StudyManager.getDomainURI(c, user, name);
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

                List<List<ConditionalFormat>> formats = reader.getConditionalFormats();
                if (formats != null)
                {
                    assert formats.size() == pds.length;
                    for (int i = 0; i < pds.length; i++)
                    {
                        List<ConditionalFormat> pdFormats = formats.get(i);
                        if (!pdFormats.isEmpty())
                        {
                            PropertyService.get().saveConditionalFormats(user, pds[i], pdFormats);
                        }
                    }
                }

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
                        def.setTypeURI(getDomainURI(c, user, def));
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


    public String getDomainURI(Container c, User u, DataSet def)
    {
        if (null == def)
            return getDomainURI(c, u, (String)null);
        else
            return getDomainURI(c, u, def.getName());
    }


    private static String getDomainURI(Container c, User u, String name)
    {
        return DatasetDomainKind.generateDomainURI(name, c);
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
            // TODO fix getDisplayUrl
            if (true) throw new RuntimeException("not integrated with hard tables");

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
                            "SELECT Container, DatasetId, SequenceNum, ParticipantId FROM " + /*StudySchema.getInstance().getTableInfoStudyData(null) +*/ " WHERE LSID=?",
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

        if (_moduleParticipantViews != null)
        {
            Set<Module> activeModules = study.getContainer().getActiveModules();
            Set<String> activeModuleNames = new HashSet<String>();
            for (Module module : activeModules)
                activeModuleNames.add(module.getName());
            for (Map.Entry<String, Resource> entry : _moduleParticipantViews.entrySet())
            {
                if (activeModuleNames.contains(entry.getKey()) && entry.getValue().exists())
                {
                    try
                    {
                        String body = IOUtils.toString(entry.getValue().getInputStream());
                        return CustomParticipantView.createModulePtidView(body);
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException("Unable to load participant view from " + entry.getValue().getPath(), e);
                    }
                }
            }
        }

        SimpleFilter containerFilter = new SimpleFilter("Container", study.getContainer().getId());
        return Table.selectObject(StudySchema.getInstance().getTableInfoParticipantView(), Table.ALL_COLUMNS,
                containerFilter, null, CustomParticipantView.class);
    }

    public CustomParticipantView saveCustomParticipantView(Study study, User user, CustomParticipantView view) throws SQLException
    {
        if (view.isModuleParticipantView())
            throw new IllegalArgumentException("Module-defined participant views should not be saved to the database.");
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

                indexDataset(task, dsd);
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

    private static void indexDataset(SearchService.IndexTask task, DataSetDefinition dsd)
    {
        if (null == task)
            task = ServiceRegistry.get(SearchService.class).defaultTask();
        String docid = "dataset:" + new Path(dsd.getContainer().getId(), String.valueOf(dsd.getDataSetId())).toString();

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

        ActionURL view = new ActionURL(StudyController.DatasetAction.class, null);
        view.replaceParameter("datasetId", String.valueOf(dsd.getDataSetId()));
        view.setExtraPath(dsd.getContainer().getId());

        SimpleDocumentResource r = new SimpleDocumentResource(new Path(docid), docid,
                "text/plain", body.toString().getBytes(),
                view, props);
        task.addResource(r, SearchService.PRIORITY.item);
    }


    public static void indexParticipantView(final SearchService.IndexTask task)
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
                        indexParticipantView(task, c, null);
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


    public static void indexParticipantView(final SearchService.IndexTask task, final Container c, List<String> ptids)
    {
        if (null == c)
        {
            if (null != ptids)
                throw new IllegalArgumentException();
            indexParticipantView(task);
            return;
        }

        if (null != ptids && ptids.size() == 0)
            return;

        final int BATCH_SIZE = 500;
        if (null != ptids && ptids.size() > BATCH_SIZE)
        {
            ArrayList<String> list = new ArrayList<String>(BATCH_SIZE);
            for (String ptid : ptids)
            {
                list.add(ptid);
                if (list.size() == BATCH_SIZE)
                {
                    final ArrayList<String> l = list;
                    Runnable r = new Runnable(){ @Override public void run() {
                        indexParticipantView(task, c, l);
                    }};
                    task.addRunnable(r, SearchService.PRIORITY.bulk);
                    list = new ArrayList<String>(BATCH_SIZE);
                }
            }
            indexParticipantView(task, c, list);
            return;
        }


        ResultSet rs = null;
        try
        {
            Study study = StudyManager.getInstance().getStudy(c);
            if (null == study)
                return;
            String nav = NavTree.toJS(Collections.singleton(new NavTree("study", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c))), null, false).toString();

            SQLFragment f = new SQLFragment("SELECT container, participantid FROM " + StudySchema.getInstance().getTableInfoParticipant());
            String prefix = " WHERE ";
            if (null != c)
            {
                f.append(prefix).append(" container = ?");
                f.add(c);
                prefix = " AND ";
            }
            if (null != ptids)
            {
                f.append(prefix).append(" participantid IN (");
                String marker="?";
                for (String ptid : ptids)
                {
                    f.append(marker);
                    f.add(ptid);
                    marker = ",?";
                }
                f.append(")");
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

    public void registerParticipantView(Module module, Resource ptidView)
    {
        if (_moduleParticipantViews == null)
            _moduleParticipantViews = new HashMap<String, Resource>();
        _moduleParticipantViews.put(module.getName(), ptidView);
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







    public static class StudyTestCase extends Assert
    {
        TestContext _context = null;
        StudyManager _manager = StudyManager.getInstance();

        Container _c = null;
        Study _study = null;


//        @BeforeClass
        public void createStudy() throws SQLException
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            String name = GUID.makeHash();
            _c = ContainerManager.createContainer(junit,name);
            StudyImpl s = new StudyImpl(_c, "Junit Study");
            s.setTimepointType(TimepointType.DATE);
            s.setStartDate(new Date(DateUtil.parseDateTime("2001-01-01")));
            s.setSubjectColumnName("SubjectID");
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            _study = StudyManager.getInstance().createStudy(_context.getUser(), s);

            MvUtil.assignMvIndicators(_c,
                    new String[] {"X", "Y", "Z"},
                    new String[] {"XXX", "YYY", "ZZZ"});
        }


        int counterDatasetId = 100;

        DataSet createDataset(String name) throws Exception
        {
            int id = counterDatasetId++;
            _manager.createDataSetDefinition(_context.getUser(), _study.getContainer(), id);
            DataSetDefinition dd = _manager.getDataSetDefinition(_study, id);
            dd = dd.createMutable();

            dd.setName(name);
            dd.setLabel(name);
            dd.setCategory("Category");
            dd.setEntityId(GUID.makeGUID());
            dd.setKeyPropertyName("Measure");
            String domainURI = StudyManager.getInstance().getDomainURI(_study.getContainer(), null, dd);
            dd.setTypeURI(domainURI);
            OntologyManager.ensureDomainDescriptor(domainURI, dd.getName(), _study.getContainer());
            StudyManager.getInstance().updateDataSetDefinition(null, dd);

            // validator
            Lsid lsidValidator = DefaultPropertyValidator.createValidatorURI(PropertyValidatorType.Range);
            IPropertyValidator pvLessThan100 = PropertyService.get().createValidator(lsidValidator.toString());
            pvLessThan100.setName("lessThan100");
            pvLessThan100.setExpressionValue("~lte=100.0");

            // define columns
            Domain domain = dd.getDomain();

            DomainProperty measure = domain.addProperty();
            measure.setName("Measure");
            measure.setPropertyURI(domain.getTypeURI()+"#"+measure.getName());
            measure.setRangeURI(PropertyType.STRING.getTypeUri());
            measure.setRequired(true);

            DomainProperty value = domain.addProperty();
            value.setName("Value");
            value.setPropertyURI(domain.getTypeURI()+"#"+value.getName());
            value.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            value.setMvEnabled(true);

            // Missing values and validators don't work together, so I need another column
            DomainProperty number = domain.addProperty();
            number.setName("Number");
            number.setPropertyURI(domain.getTypeURI()+"#"+number.getName());
            number.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            number.addValidator(pvLessThan100);

            // save
            domain.save(_context.getUser());
            
            return _study.getDataSet(id);
        }


        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _testDatsetUpdateService();
                _testImportDatasetData();
            }
            catch (BatchValidationException x)
            {
                List<ValidationException> l = x.getRowErrors();
                if (null != l && l.size() > 0)
                    throw l.get(0);
                throw x;
            }
            catch (Throwable t)
            {
                throw t;
            }
            finally
            {
                tearDown();
            }
        }


        private void _testDatsetUpdateService() throws Throwable
        {
            int counter = 0;
            ResultSet rs = null;

            try
            {
                StudyQuerySchema ss = new StudyQuerySchema((StudyImpl)_study, _context.getUser(), false);
                DataSet def = createDataset("A");
                TableInfo tt = ss.getTable(def.getName());
                QueryUpdateService qus = tt.getUpdateService();
                assertNotNull(qus);

                Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
                Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
                List rows = new ArrayList();

                // insert one row
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                rs = Table.select(tt, Table.ALL_COLUMNS, null, null);
                assertTrue(rs.next());

                // duplicate row
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("duplicate key");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("Duplicates were found"));
                }

                // different participant
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counter), "Value", 2.0));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);

                // different date
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan2, "Measure", "Test"+(counter), "Value", "X"));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);

                // different measure
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "X"));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);

                // duplicates in batch
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counter), "Value", 1.0));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("duplicates");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("Duplicates were found in the database or imported data"));
                }

                // missing participantid
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", null, "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("measure is null");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: All dataset rows must include a value for SubjectID
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("value for SubjectID"));
                }

                // missing date
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", null, "Measure", "Test"+(++counter), "Value", 1.0));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("measure is null");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Row 1 does not contain required field date.
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("does not contain required field date"));
                }

                // missing required property field
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("measure is null");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Row 1 does not contain required field Measure.
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("does not contain required field Measure"));
                }

                // legal MV indicator
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "X"));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);

                // illegal MV indicator
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "N/A"));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("measure is illegal QC value");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Could not convert 'N/A' for field Value, should be of type Double
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("should be of type Double"));
                }

                // conversion test
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "100"));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);

                
                // validation test
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1, "Number", 101));
                try
                {
                    qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
                    fail("should fail validation test");
                }
                catch (BatchValidationException x)
                {
                    //study:Label: Value '101.0' for field 'Number' is invalid.
                    assertTrue(-1 != x.getRowErrors().get(0).getMessage().indexOf("is invalid"));
                }
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counter), "Value", 1, "Number", 99));
                qus.insertRows(_context.getUser(), _study.getContainer(), rows, null);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }

        
        private void _import(DataSet def, final List rows, List<String> errors) throws Exception
        {
            DataLoader dl = new MapLoader(rows);
            Map<String,String> columnMap = new CaseInsensitiveHashMap<String>();

            for (ColumnInfo c : def.getTableInfo(_context.getUser()).getColumns())
            {
                columnMap.put(c.getName(), c.getPropertyURI());
            }
            
            StudyManager.getInstance().importDatasetData(
                    _study, _context.getUser(),
                    (DataSetDefinition)def, dl, 0, columnMap,
                    errors, true, false, null, null);
        }


        private void _testImportDatasetData() throws Throwable
        {
            int counter=0;
            ResultSet rs = null;

            try
            {
                StudyQuerySchema ss = new StudyQuerySchema((StudyImpl)_study, _context.getUser(), false);
                DataSet def = createDataset("B");
                TableInfo tt = ss.getTable(def.getName());

                Date Jan1 = new Date(DateUtil.parseDateTime("1/1/2011"));
                Date Jan2 = new Date(DateUtil.parseDateTime("2/1/2011"));
                List rows = new ArrayList();
                List<String> errors = new ArrayList<String>(){
                    @Override
                    public boolean add(String s)
                    {
                        return super.add(s);    //To change body of overridden methods use File | Settings | File Templates.
                    }
                };

                // insert one row
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                _import(def, rows, errors);
                rs = Table.select(tt, Table.ALL_COLUMNS, null, null);
                assertTrue(rs.next());

                // duplicate row
                _import(def, rows, errors);
                //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
                assertTrue(-1 != errors.get(0).indexOf("Duplicates were found"));

                // different participant
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counter), "Value", 2.0));
                _import(def, rows, errors);

                // different date
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan2, "Measure", "Test"+(counter), "Value", "X"));
                _import(def, rows, errors);

                // different measure
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "X"));
                _import(def, rows, errors);

                // duplicates in batch
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counter), "Value", 1.0));
                _import(def, rows, errors);
                //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
                assertTrue(-1 != errors.get(0).indexOf("Duplicates were found in the database or imported data"));

                // missing participantid
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", null, "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1.0));
                _import(def, rows, errors);
                //Row 1 does not contain required field SubjectID.
                assertTrue(-1 != errors.get(0).indexOf("required field SubjectID"));

                // missing date
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", null, "Measure", "Test"+(++counter), "Value", 1.0));
                _import(def, rows, errors);
                //study:Label: Row 1 does not contain required field date.
                assertTrue(-1 != errors.get(0).indexOf("does not contain required field date"));

                // missing required property field
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0));
                _import(def, rows, errors);
                //study:Label: Row 1 does not contain required field Measure.
                assertTrue(-1 != errors.get(0).indexOf("does not contain required field Measure"));

                // legal MV indicator
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "X"));
                _import(def, rows, errors);

                // illegal MV indicator
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "N/A"));
                _import(def, rows, errors);
                //Row 1 data type error for field Value.
                assertTrue(-1 != errors.get(0).indexOf("data type error for field Value"));

                // conversion test
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", "100"));
                _import(def, rows, errors);

                // validation test
                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counter), "Value", 1, "Number", 101));
                _import(def, rows, errors);
                //study:Label: Value '101.0' for field 'Number' is invalid.
                assertTrue(-1 != errors.get(0).indexOf("is invalid"));

                rows.clear(); errors.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counter), "Value", 1, "Number", 99));
                _import(def, rows, errors);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }


//        @AfterClass
        public void tearDown()
        {
            if (null != _study)
            {

            }
            if (null != _c)
            {
                ContainerManager.delete(_c, _context.getUser());
            }
        }
    }
}
