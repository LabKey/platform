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

package org.labkey.study.model;

import gwt.client.org.labkey.study.dataset.client.model.GWTDataset;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.DbCache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.*;
import org.labkey.api.data.DbScope.CommitTaskOption;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.BeanDataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.DomainURIFactory;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptor;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptorsList;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryListener;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.search.SearchService.IndexTask;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.AssaySpecimenConfig;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.study.QueryHelper;
import org.labkey.study.SpecimenManager;
import org.labkey.study.StudyCache;
import org.labkey.study.StudySchema;
import org.labkey.study.StudyServiceImpl;
import org.labkey.study.assay.AssayManager;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.controllers.DatasetServiceImpl;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.importer.SchemaReader;
import org.labkey.study.importer.StudyImportContext;
import org.labkey.study.importer.StudyReload;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.StudyPersonnelDomainKind;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.query.studydesign.StudyProductAntigenDomainKind;
import org.labkey.study.query.studydesign.StudyProductDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentDomainKind;
import org.labkey.study.query.studydesign.StudyTreatmentProductDomainKind;
import org.labkey.study.visitmanager.AbsoluteDateVisitManager;
import org.labkey.study.visitmanager.RelativeDateVisitManager;
import org.labkey.study.visitmanager.SequenceVisitManager;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.writer.DatasetDataWriter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class StudyManager
{
    public static final SearchService.SearchCategory datasetCategory = new SearchService.SearchCategory("dataset", "Study Dataset");
    public static final SearchService.SearchCategory subjectCategory = new SearchService.SearchCategory("subject", "Study Subject");
    public static final SearchService.SearchCategory assayCategory = new SearchService.SearchCategory("assay", "Study Assay");

    private static final Logger _log = Logger.getLogger(StudyManager.class);
    private static final StudyManager _instance = new StudyManager();
    private static final StudySchema SCHEMA = StudySchema.getInstance();

    private final QueryHelper<StudyImpl> _studyHelper;
    private final QueryHelper<VisitImpl> _visitHelper;
    //    private final QueryHelper<LocationImpl> _locationHelper;
    private final QueryHelper<AssaySpecimenConfigImpl> _assaySpecimenHelper;
    private final DatasetHelper _datasetHelper;
    private final QueryHelper<CohortImpl> _cohortHelper;
    private final BlockingCache<Container, Set<PropertyDescriptor>> _sharedProperties;

    private static final String LSID_REQUIRED = "LSID_REQUIRED";


    protected StudyManager()
    {
        // prevent external construction with a private default constructor
        _studyHelper = new QueryHelper<StudyImpl>(() -> StudySchema.getInstance().getTableInfoStudy(), StudyImpl.class)
        {
            public List<StudyImpl> get(final Container c, SimpleFilter filterArg, final String sortString)
            {
                assert filterArg == null && sortString == null;
                String cacheId = getCacheId(filterArg);
                if (sortString != null)
                    cacheId += "; sort = " + sortString;

                final Set<Container> siblingsWithNoStudies = new HashSet<>();
                final Set<Study> siblingsStudies = new HashSet<>();

                CacheLoader<String, Object> loader = (key, argument) ->
                {
                    // Bulk-load the study for the current container and its siblings, instead of issuing separate
                    // requests for each container. See issue 19632
                    SQLFragment selectSQL = new SQLFragment("SELECT * FROM study.Study WHERE Container IN (SELECT ? AS EntityId ");
                    selectSQL.add(c);
                    if (c.getParent() != null)
                    {
                        selectSQL.append(" UNION SELECT EntityId FROM ");
                        selectSQL.append(CoreSchema.getInstance().getTableInfoContainers(), "c");
                        selectSQL.append(" WHERE Parent = ?");
                        selectSQL.add(c.getParent());
                    }
                    selectSQL.append(")");
                    List<StudyImpl> objs = new SqlSelector(StudySchema.getInstance().getSchema(), selectSQL).getArrayList(StudyImpl.class);

                    // The match, if any, for the container that's being queried directly
                    StudyImpl result = null;
                    // Keep track of all of the containers that DON'T have a study so we can cache them as a miss
                    if (c.getParent() != null)
                    {
                        siblingsWithNoStudies.addAll(ContainerManager.getChildren(c.getParent()));
                        // No need to reprocess the original container
                        siblingsWithNoStudies.remove(c);
                    }

                    for (StudyImpl obj : objs)
                    {
                        obj.lock();

                        if (obj.getContainer().equals(c))
                        {
                            result = obj;
                        }
                        else
                        {
                            // Found a study for this container
                            siblingsWithNoStudies.remove(obj.getContainer());

                            // Remember the hit
                            siblingsStudies.add(obj);
                        }
                    }

                    // Return the specific hit/miss for the originally queried container
                    return result == null ? Collections.emptyList() : Collections.singletonList(result);
                };

                List<StudyImpl> result = (List<StudyImpl>) StudyCache.get(getTableInfo(), c, cacheId, loader);

                // Make sure the misses are cached
                for (Container studylessChild : siblingsWithNoStudies)
                {
                    StudyCache.get(getTableInfo(), studylessChild, getCacheId(filterArg), new CacheLoader<String, Object>()
                    {
                        @Override
                        public Object load(String key, @Nullable Object argument)
                        {
                            return Collections.emptyList();
                        }
                    });
                }

                // Make sure the sibling hits are cached
                for (final Study study : siblingsStudies)
                {
                    StudyCache.get(getTableInfo(), study.getContainer(), getCacheId(filterArg), new CacheLoader<String, Object>()
                    {
                        @Override
                        public Object load(String key, @Nullable Object argument)
                        {
                            return Collections.singletonList(study);
                        }
                    });
                }

                return result;
            }

            @Override
            public void clearCache(Container c)
            {
                super.clearCache(c);
                clearCachedStudies();
            }

            @Override
            public void clearCache(StudyImpl obj)
            {
                super.clearCache(obj);
                clearCachedStudies();
            }
        };

        _visitHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoVisit();
            }
        }, VisitImpl.class);

//        _locationHelper = new QueryHelper<>(new TableInfoGetter()
//        {
//            public TableInfo getTableInfo()
//            {
//                return StudySchema.getInstance().getTableInfoSite();
//            }
//        }, LocationImpl.class);

        _assaySpecimenHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoAssaySpecimen();
            }
        }, AssaySpecimenConfigImpl.class);

        _cohortHelper = new QueryHelper<>(new TableInfoGetter()
        {
            public TableInfo getTableInfo()
            {
                return StudySchema.getInstance().getTableInfoCohort();
            }
        }, CohortImpl.class);

        /* Whenever we explicitly invalidate a dataset, unmaterialize it as well
         * this is probably a little overkill, e.g. name change doesn't need to unmaterialize
         * however, this is the best choke point
         */
        _datasetHelper = new DatasetHelper();

        // Cache of PropertyDescriptors found in the Shared container for datasets in the given study Container.
        // The shared properties cache will be cleared when the _datasetHelper cache is cleared.
        _sharedProperties = CacheManager.getBlockingCache(1000, CacheManager.UNLIMITED, "StudySharedProperties",
                (key, argument) ->
                {
                    Container sharedContainer = ContainerManager.getSharedContainer();
                    assert key != sharedContainer;

                    List<DatasetDefinition> defs = _datasetHelper.get(key);
                    if (defs == null)
                        return Collections.emptySet();

                    Set<PropertyDescriptor> set = new LinkedHashSet<>();
                    for (DatasetDefinition def : defs)
                    {
                        Domain domain = def.getDomain();
                        if (domain == null)
                            continue;

                        for (DomainProperty dp : domain.getProperties())
                            if (dp.getContainer().equals(sharedContainer))
                                set.add(dp.getPropertyDescriptor());
                    }
                    return Collections.unmodifiableSet(set);
                }
        );

        ViewCategoryManager.addCategoryListener(new CategoryListener(this));
    }

    public void updateStudySnapshot(StudySnapshot snapshot, User user)
    {
        // For now, "refresh" is the only field that can be updated (plus the Modified fields, which get handled automatically)
        Map<String, Object> map = new HashMap<>();
        map.put("refresh", snapshot.isRefresh());

        Table.update(user, StudySchema.getInstance().getTableInfoStudySnapshot(), map, snapshot.getRowId());
    }

    private class DatasetHelper
    {
        // NOTE: We really don't want to have multiple instances of DatasetDefinitions in-memory, only return the
        // datasets that are cached under container.containerId/ds.entityId

        private QueryHelper<DatasetDefinition> helper = new QueryHelper<DatasetDefinition>(
                () -> StudySchema.getInstance().getTableInfoDataset(),
                DatasetDefinition.class)
        {
            @Override
            public void clearCache(Container c)
            {
                super.clearCache(c);
            }

            @Override
            public void clearCache(DatasetDefinition obj)
            {
                super.clearCache(obj.getContainer());
            }
        };


        private DatasetHelper()
        {
        }

        public TableInfo getTableInfo()
        {
            return StudySchema.getInstance().getTableInfoDataset();
        }

        private void clearProperties(DatasetDefinition def)
        {
            StudyManager.this._sharedProperties.remove(def.getContainer());
        }

        public void clearCache(Container c)
        {
            helper.clearCache(c);
        }

        public void clearCache(DatasetDefinition def)
        {
            helper.clearCache(def.getContainer());
            clearProperties(def);
        }

        public DatasetDefinition create(User user, DatasetDefinition obj)
        {
            return helper.create(user, obj);
        }

        public DatasetDefinition update(User user, DatasetDefinition obj, Object... pk)
        {
            return helper.update(user, obj, pk);
        }

        public List<DatasetDefinition> get(Container c)
        {
            return toSharedInstance(helper.get(c));
        }

        public List<DatasetDefinition> get(Container c, SimpleFilter filter)
        {
            return toSharedInstance(helper.get(c, filter));
        }

        public List<DatasetDefinition> get(Container c, @Nullable SimpleFilter filterArg, @Nullable String sortString)
        {
            return toSharedInstance(helper.get(c, filterArg, sortString));
        }

        public DatasetDefinition get(Container c, int rowId)
        {
            return toSharedInstance(helper.get(c, rowId, "DatasetId"));
        }

        private List<DatasetDefinition> toSharedInstance(List<DatasetDefinition> in)
        {
            TableInfo t = getTableInfo();
            ArrayList<DatasetDefinition> ret = new ArrayList<>(in.size());
            for (DatasetDefinition dsIn : in)
            {
                DatasetDefinition dsRet = (DatasetDefinition) StudyCache.getCached(t, dsIn.getContainer(), dsIn.getEntityId());
                if (null == dsRet)
                {
                    dsRet = dsIn;
                    StudyCache.cache(t, dsIn.getContainer(), dsIn.getEntityId(), dsIn);
                }
                ret.add(dsRet);
            }
            return ret;
        }

        private DatasetDefinition toSharedInstance(DatasetDefinition dsIn)
        {
            if (null == dsIn)
                return null;
            TableInfo t = getTableInfo();
            DatasetDefinition dsRet = (DatasetDefinition) StudyCache.getCached(t, dsIn.getContainer(), dsIn.getEntityId());
            if (null == dsRet)
            {
                dsRet = dsIn;
                StudyCache.cache(t, dsIn.getContainer(), dsIn.getEntityId(), dsIn);
            }
            return dsRet;
        }
    }


    public static StudyManager getInstance()
    {
        return _instance;
    }


    @Nullable
    public StudyImpl getStudy(@NotNull Container c)
    {
        StudyImpl study;
        boolean retry = true;

        while (true)
        {
            List<StudyImpl> studies = _studyHelper.get(c);
            if (studies == null || studies.size() == 0)
                return null;
            else if (studies.size() > 1)
                throw new IllegalStateException("Only one study is allowed per container");
            else
                study = studies.get(0);

            // UNDONE: There is a subtle bug in QueryHelper caching, cached objects shouldn't hold onto Container objects
            assert (study.getContainer().getId().equals(c.getId()));
            Container freshestContainer = ContainerManager.getForId(c.getId());
            if (study.getContainer() == freshestContainer)
                break;

            if (!retry) // we only get one retry
                break;

            _log.debug("Clearing cached study for " + c + " as its container reference didn't match the current object from ContainerManager " + freshestContainer);

            _studyHelper.clearCache(c);
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

    private static final String CACHE_KEY = StudyManager.class.getName() + "||cachedStudies";

    /** @return all studies in the whole server, unfiltered by permissions */
    @NotNull
    public Set<? extends StudyImpl> getAllStudies()
    {
        Set<StudyImpl> ret = (Set)CacheManager.getSharedCache().get(CACHE_KEY);
        if (ret == null)
        {
            ret = Collections.unmodifiableSet(new LinkedHashSet<>(new TableSelector(StudySchema.getInstance().getTableInfoStudy(), null, new Sort("Label")).getArrayList(StudyImpl.class)));
            CacheManager.getSharedCache().put(CACHE_KEY, ret);
        }

        return ret;
    }

    private void clearCachedStudies()
    {
        CacheManager.getSharedCache().remove(CACHE_KEY);
    }

    /** @return all studies under the given root in the container hierarchy (inclusive), unfiltered by permissions */
    @NotNull
    public Set<? extends StudyImpl> getAllStudies(@NotNull Container root)
    {
        Set<StudyImpl> result = new LinkedHashSet<>();
        for (StudyImpl study : getAllStudies())
        {
            if (study.getContainer().equals(root) || study.getContainer().isDescendant(root))
            {
                result.add(study);
            }
        }
        return Collections.unmodifiableSet(result);
    }


    /** @return all studies under the given root in the container hierarchy (inclusive), to which the user has at least read permission */
    @NotNull
    public Set<? extends StudyImpl> getAllStudies(@NotNull Container root, @NotNull User user)
    {
        return getAllStudies(root, user, ReadPermission.class);
    }

    /** @return all studies under the given root in the container hierarchy (inclusive), to which the user has at least the specified permission */
    @NotNull
    public Set<? extends StudyImpl> getAllStudies(@NotNull Container root, @NotNull User user, @NotNull Class<? extends Permission> perm)
    {
        Set<StudyImpl> result = new LinkedHashSet<>();
        for (StudyImpl study : getAllStudies())
        {
            if (study.getContainer().hasPermission(user, perm) &&
                    (study.getContainer().equals(root) || study.getContainer().isDescendant(root)))
            {
                result.add(study);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public StudyImpl createStudy(User user, StudyImpl study)
    {
        Container container = study.getContainer();
        assert null != container;
        assert null != user;
        if (study.getLsid() == null)
            study.initLsid();

        if (study.getProtocolDocumentEntityId() == null)
            study.setProtocolDocumentEntityId(GUID.makeGUID());

        if (study.getAlternateIdDigits() == 0)
            study.setAlternateIdDigits(StudyManager.ALTERNATEID_DEFAULT_NUM_DIGITS);

        try (Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
        {
            StudySchema.getInstance().getTableInfoSite(container, user);    // This provisioned table is needed for creating the study
            study = _studyHelper.create(user, study);

            //note: we no longer copy the container's policy to the study upon creation
            //instead, we let it inherit the container's policy until the security type
            //is changed to one of the advanced options.

            // Force provisioned specimen tables to be created
            StudySchema.getInstance().getTableInfoSpecimenPrimaryType(container, user);
            StudySchema.getInstance().getTableInfoSpecimenDerivative(container, user);
            StudySchema.getInstance().getTableInfoSpecimenAdditive(container, user);
            StudySchema.getInstance().getTableInfoSpecimen(container, user);
            StudySchema.getInstance().getTableInfoVial(container, user);
            StudySchema.getInstance().getTableInfoSpecimenEvent(container, user);
            transaction.commit();
        }
        ContainerManager.notifyContainerChange(container.getId(), ContainerManager.Property.StudyChange);
        return study;
    }

    public void updateStudy(@Nullable User user, StudyImpl study)
    {
        StudyImpl oldStudy = getStudy(study.getContainer());
        Date oldStartDate = oldStudy.getStartDate();
        _studyHelper.update(user, study, study.getContainer());

        if (oldStudy.getTimepointType() == TimepointType.DATE && !Objects.equals(study.getStartDate(), oldStartDate))
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

        if (oldStudy.getSecurityType() != study.getSecurityType())
        {
            String comment = "Dataset security type changed from " + oldStudy.getSecurityType() + " to " + study.getSecurityType();
            StudyService.get().addStudyAuditEvent(study.getContainer(), user, comment);
        }
    }

    public void createDatasetDefinition(User user, Container container, int datasetId)
    {
        createDatasetDefinition(user, new DatasetDefinition(getStudy(container), datasetId));
    }

    public void createDatasetDefinition(User user, DatasetDefinition datasetDefinition)
    {
        if (datasetDefinition.getDatasetId() <= 0)
            throw new IllegalArgumentException("datasetId must be greater than zero.");
        DbScope scope = StudySchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            ensureViewCategory(user, datasetDefinition);
            _datasetHelper.create(user, datasetDefinition);
            // This method call has the side effect of ensuring that we have a domain. If we don't create it here,
            // we're open to a race condition if another thread tries to do something with the dataset's table
            // and ends up attempting to create the domain as well
            datasetDefinition.getStorageTableInfo();

            transaction.commit();
        }
        indexDataset(null, datasetDefinition);
    }

    /**
     * Temporary shim until we can redo the dataset category UI
     */
    private void ensureViewCategory(User user, DatasetDefinition def)
    {
        ViewCategory category = null;

        if (def.getCategoryId() != null)
            category = ViewCategoryManager.getInstance().getCategory(def.getContainer(), def.getCategoryId());

        if (category == null && def.getCategory() != null)
        {
            // the imported category name may be encoded to contain subcategory info
            String[] parts = ViewCategoryManager.getInstance().decode(def.getCategory());
            category = ViewCategoryManager.getInstance().ensureViewCategory(def.getContainer(), user, parts);
        }

        if (category != null)
        {
            def.setCategoryId(category.getRowId());
            def.setCategory(category.getLabel());
        }
    }

    public void updateDatasetDefinition(User user, DatasetDefinition datasetDefinition, List<String> errors)
    {
        try
        {
            updateDatasetDefinition(user, datasetDefinition);
        }
        catch (IllegalArgumentException ex)
        {
            errors.add(ex.getMessage());
        }
    }


    /* most users should call the List<String> errors version to avoid uncaught exceptions */
    @Deprecated
    public boolean updateDatasetDefinition(User user, final DatasetDefinition datasetDefinition)
    {
        if (datasetDefinition.isShared())
        {
            // check if we're updating the dataset property overrides in a sub-folder
            if (!datasetDefinition.getContainer().equals(datasetDefinition.getDefinitionContainer()))
            {
                return updateDatasetPropertyOverrides(user, datasetDefinition);
            }
        }

        DbScope scope = StudySchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            DatasetDefinition old = getDatasetDefinition(datasetDefinition.getStudy(), datasetDefinition.getDatasetId());
            if (null == old)
                throw OptimisticConflictException.create(Table.ERROR_DELETED);

            // make sure we reload domain and tableinfo
            Domain domain = datasetDefinition.refreshDomain();

            // Check if the extra key field has changed
            boolean isProvisioned = domain != null && domain.getStorageTableName() != null;
            boolean isKeyChanged =
                            old.isDemographicData() != datasetDefinition.isDemographicData() ||
                            !StringUtils.equals(old.getKeyPropertyName(), datasetDefinition.getKeyPropertyName()) ||
                            old.getUseTimeKeyField() != datasetDefinition.getUseTimeKeyField();
            boolean isSharedChanged = old.getDataSharingEnum() != datasetDefinition.getDataSharingEnum();
            if (isProvisioned && isSharedChanged)
            {
                // let's not change the shared setting if there are existing rows
                if (new TableSelector(datasetDefinition.getStorageTableInfo()).exists())
                {
                    throw new IllegalArgumentException("Can't change data sharing setting if there are existing data rows.");
                }
            }
            if (isProvisioned && isKeyChanged)
            {
                TableInfo storageTableInfo = datasetDefinition.getStorageTableInfo();

                // If so, we need to update the _key column and the LSID

                // Set the _key column to be the value of the selected column
                // Change how we build up tableName
                String tableName = storageTableInfo.toString();
                SQLFragment updateKeySQL = new SQLFragment("UPDATE " + tableName + " SET _key = ");
                if (datasetDefinition.getUseTimeKeyField())
                {
                    ColumnInfo col = storageTableInfo.getColumn("Date");
                    if (null == col)
                    {
                        throw new IllegalArgumentException("Cannot find 'Date' column in table: " + tableName);
                    }
                    SQLFragment colFrag = col.getValueSql(tableName);
                    updateKeySQL.append(storageTableInfo.getSqlDialect().getISOFormat(colFrag));
                }
                else if (datasetDefinition.getKeyPropertyName() == null)
                {
                    // No column selected, so set it to be null
                    updateKeySQL.append("NULL");
                }
                else
                {
                    ColumnInfo col = storageTableInfo.getColumn(datasetDefinition.getKeyPropertyName());
                    if (null == col)
                    {
                        throw new IllegalArgumentException("Cannot find 'key' column: " + datasetDefinition.getKeyPropertyName() + " in table: " + tableName);
                    }
                    SQLFragment colFrag = col.getValueSql(tableName);
                    if (col.getJdbcType() == JdbcType.TIMESTAMP)
                        colFrag = storageTableInfo.getSqlDialect().getISOFormat(colFrag);
                    updateKeySQL.append(colFrag);
                }

                try
                {
                    new SqlExecutor(StudySchema.getInstance().getSchema()).setLogLevel(Level.OFF).execute(updateKeySQL);

                    // Now update the LSID column. Note - this needs to be the same as DatasetImportHelper.getURI()
                    SQLFragment updateLSIDSQL = new SQLFragment("UPDATE " + tableName + " SET lsid = ");
                    updateLSIDSQL.append(datasetDefinition.generateLSIDSQL());
                    new SqlExecutor(StudySchema.getInstance().getSchema()).execute(updateLSIDSQL);
                }
                catch (DataIntegrityViolationException x)
                {
                    _log.debug("Old Dataset: " + old.getName());
                    _log.debug("    Demographic: " + old.isDemographicData());
                    _log.debug("    Key: " + old.getKeyPropertyName());
                    _log.debug("New Dataset: " + datasetDefinition.getName());
                    _log.debug("    Demographic: " + datasetDefinition.isDemographicData());
                    _log.debug("    Key: " + datasetDefinition.getKeyPropertyName());

                    if (datasetDefinition.isDemographicData())
                        throw new IllegalArgumentException("Can not change dataset type to demographic for dataset " + datasetDefinition.getName());
                    else
                        throw new IllegalArgumentException("Changing the dataset key would result in duplicate keys for dataset " + datasetDefinition.getName());
                }
            }
            Object[] pk = new Object[]{datasetDefinition.getContainer().getId(), datasetDefinition.getDatasetId()};
            ensureViewCategory(user, datasetDefinition);
            ensureDatasetDefinitionDomain(user, datasetDefinition);
            _datasetHelper.update(user, datasetDefinition, pk);

            if (!old.getName().equals(datasetDefinition.getName()))
            {
                QueryChangeListener.QueryPropertyChange change = new QueryChangeListener.QueryPropertyChange<>(
                        QueryService.get().getUserSchema(user, datasetDefinition.getContainer(), StudyQuerySchema.SCHEMA_NAME).getQueryDefForTable(datasetDefinition.getName()),
                        QueryChangeListener.QueryProperty.Name,
                        old.getName(),
                        datasetDefinition.getName()
                );

                QueryService.get().fireQueryChanged(user, datasetDefinition.getContainer(), null, new SchemaKey(null, StudyQuerySchema.SCHEMA_NAME),
                        QueryChangeListener.QueryProperty.Name, Collections.singleton(change));
            }
            transaction.addCommitTask(() -> {
                // And post-commit to make sure that no other threads have reloaded the cache in the meantime
                uncache(datasetDefinition);
            }, CommitTaskOption.POSTCOMMIT, CommitTaskOption.IMMEDIATE);
            transaction.commit();
        }
        indexDataset(null, datasetDefinition);
        return true;
    }

    /**
     * Shared dataset may save some of its properties in the current container rather than the dataset definition container.
     * Currently allowed overrides:
     * <ul>
     *     <li>isShownByDefault</li>
     * </ul>
     * @return true if successful.
     */
    private boolean updateDatasetPropertyOverrides(User user, final DatasetDefinition datasetDefinition)
    {
        if (!datasetDefinition.isShared() || datasetDefinition.getContainer().isProject())
        {
            throw new IllegalArgumentException("Dataset property overrides can only be applied to shared datasets and in sub-containers");
        }

        DbScope scope = StudySchema.getInstance().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            DatasetDefinition old = getDatasetDefinition(datasetDefinition.getStudy(), datasetDefinition.getDatasetId());
            if (null == old)
                throw OptimisticConflictException.create(Table.ERROR_DELETED);

            DatasetDefinition original = getDatasetDefinition(datasetDefinition.getDefinitionStudy(), datasetDefinition.getDatasetId());

            // make sure we reload domain and tableinfo
            Domain domain = datasetDefinition.refreshDomain();

            // Error if any other properties have been changed
            if (!Objects.equals(old.getLabel(), datasetDefinition.getLabel()))
            {
                throw new IllegalArgumentException("Shared dataset label can't be changed");
            }
            if (!Objects.equals(old.getCategoryId(), datasetDefinition.getCategoryId()))
            {
                throw new IllegalArgumentException("Shared dataset category can't be changed");
            }
            if (!Objects.equals(old.getCohortId(), datasetDefinition.getCohortId()))
            {
                throw new IllegalArgumentException("Shared dataset cohort can't be changed");
            }

            // track added and removed properties against the shared dataset in the definition container
            Map<String,String> add = new HashMap<>();
            List<String> remove = new LinkedList<>();
            if (datasetDefinition.isShowByDefault() != original.isShowByDefault())
                add.put("showByDefault", String.valueOf(datasetDefinition.isShowByDefault()));
            else
                remove.add("showByDefault");


            // update the override map
            Container c = datasetDefinition.getContainer();
            String category = "dataset-overrides:" + datasetDefinition.getDatasetId();
            PropertyManager.PropertyMap map = null;
            if (!add.isEmpty())
            {
                map = PropertyManager.getWritableProperties(c, category, true);
                map.putAll(add);
            }

            if (!remove.isEmpty())
            {
                if (map == null)
                    map = PropertyManager.getWritableProperties(c, category, false);

                if (map != null)
                {
                    for (String key : remove)
                        map.remove(key);
                }
            }

            // persist change -- if overrides are no longer needed, just remove it
            if (map != null)
            {
                if (map.isEmpty())
                    map.delete();
                else
                    map.save();
            }

            transaction.addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    // And post-commit to make sure that no other threads have reloaded the cache in the meantime
                    uncache(datasetDefinition);
                }
            }, CommitTaskOption.POSTCOMMIT, CommitTaskOption.IMMEDIATE);
            transaction.commit();
        }

        return true;
    }

    public void deleteDatasetPropertyOverrides(User user, Container c, BindException errors)
    {
        if (c.isProject())
        {
            errors.reject(ERROR_MSG, "can't delete dataset property override from project-level study");
            return;
        }

        Study study = getStudy(c);
        if (study == null)
        {
            errors.reject(ERROR_MSG, "study not found");
            return;
        }

        if (study.isDataspaceStudy())
        {
            errors.reject(ERROR_MSG, "can't delete dataset property override from a shared study");
            return;
        }

        Study sharedStudy = getSharedStudy(study);
        if (sharedStudy == null)
        {
            errors.reject(ERROR_MSG, "not a sub-study of a shared study");
            return;
        }

        DbScope scope = StudySchema.getInstance().getScope();
        try (Transaction transaction = scope.ensureTransaction())
        {
            for (DatasetDefinition dataset : getDatasetDefinitions(study))
            {
                if (dataset.isInherited())
                    deleteDatasetPropertyOverrides(user, dataset);
            }
            transaction.commit();
        }
    }

    private boolean deleteDatasetPropertyOverrides(User user, final DatasetDefinition datasetDefinition)
    {
        if (!datasetDefinition.isInherited() || datasetDefinition.getContainer().isProject())
        {
            throw new IllegalArgumentException("Dataset property overrides can only be applied to shared datasets and in sub-containers");
        }

        DbScope scope = StudySchema.getInstance().getScope();
        try (Transaction transaction = scope.ensureTransaction())
        {
            Container c = datasetDefinition.getContainer();
            String category = "dataset-overrides:" + datasetDefinition.getDatasetId();
            PropertyManager.getNormalStore().deletePropertySet(c, category);

            transaction.addCommitTask(new Runnable()
            {
                @Override
                public void run()
                {
                    // And post-commit to make sure that no other threads have reloaded the cache in the meantime
                    uncache(datasetDefinition);
                }
            }, CommitTaskOption.POSTCOMMIT, CommitTaskOption.IMMEDIATE);
            transaction.commit();
        }

        return true;
    }

    public boolean isDataUniquePerParticipant(DatasetDefinition dataset)
    {
        // don't use dataset.getTableInfo() since this method is called during updateDatasetDefinition`() and may be in an inconsistent state
        TableInfo t = dataset.getStorageTableInfo();
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT MAX(n) FROM (SELECT COUNT(*) AS n FROM ").append(t.getFromSQL("DS")).append(" GROUP BY ParticipantId) x");
        Integer maxCount = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getObject(Integer.class);
        return maxCount == null || maxCount <= 1;
    }


    public static class VisitCreationException extends RuntimeException
    {
        public VisitCreationException(String message)
        {
            super(message);
        }
    }


    public VisitImpl createVisit(Study study, User user, VisitImpl visit)
    {
        return createVisit(study, user, visit, null);
    }


    public VisitImpl createVisit(Study study, User user, VisitImpl visit, @Nullable List<VisitImpl> existingVisits)
    {
        Study visitStudy = getStudyForVisits(study);

        if (visit.getContainer() != null && !visit.getContainer().getId().equals(visitStudy.getContainer().getId()))
            throw new VisitCreationException("Visit container does not match study");
        visit.setContainer(visitStudy.getContainer());

        if (visit.getSequenceNumMin() > visit.getSequenceNumMax())
            throw new VisitCreationException("SequenceNumMin must be less than or equal to SequenceNumMax");

        if (null == existingVisits)
            existingVisits = getVisits(study, Visit.Order.SEQUENCE_NUM);

        int prevDisplayOrder = 0;
        int prevChronologicalOrder = 0;

        for (VisitImpl existingVisit : existingVisits)
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
            {
                String visitLabel = visit.getLabel() != null ? visit.getLabel() : ""+visit.getSequenceNumMin();
                String existingVisitLabel = existingVisit.getLabel() != null ? existingVisit.getLabel() : ""+existingVisit.getSequenceNumMin();
                throw new VisitCreationException("New visit " + visitLabel + " overlaps existing visit " + existingVisitLabel);
            }
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

        return visit;
    }


    public VisitImpl ensureVisit(Study study, User user, double sequencenum, Visit.Type type, boolean saveIfNew)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        VisitImpl result = ensureVisitWithoutSaving(study, sequencenum, type, visits);
        if (saveIfNew && result.getRowId() == 0)
        {
            // Insert it into the database if it's new
            return createVisit(study, user, result, visits);
        }
        return result;
    }


    public boolean ensureVisits(Study study, User user, Set<Double> sequencenums, @Nullable Visit.Type type)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        boolean created = false;
        for (double sequencenum : sequencenums)
        {
            VisitImpl result = ensureVisitWithoutSaving(study, sequencenum, type, visits);
            if (result.getRowId() == 0)
            {
                createVisit(study, user, result, visits);
                created = true;
            }
        }
        return created;
    }


    private VisitImpl ensureVisitWithoutSaving(Study study, double sequencenum, @Nullable Visit.Type type, List<VisitImpl> existingVisits)
    {
        // Remember the SequenceNums closest to the requested id in case we need to create one
        double nextVisit = Double.POSITIVE_INFINITY;
        double previousVisit = Double.NEGATIVE_INFINITY;
        for (VisitImpl visit : existingVisits)
        {
            if (visit.getSequenceNumMin() <= sequencenum && visit.getSequenceNumMax() >= sequencenum)
                return visit;
            // check to see if our new sequencenum is within the range of an existing visit:
            // Check if it's the closest to the requested id, either before or after
            if (visit.getSequenceNumMin() < nextVisit && visit.getSequenceNumMin() > sequencenum)
            {
                nextVisit = visit.getSequenceNumMin();
            }
            if (visit.getSequenceNumMax() > previousVisit && visit.getSequenceNumMax() < sequencenum)
            {
                previousVisit = visit.getSequenceNumMax();
            }
        }
        double visitIdMin = sequencenum;
        double visitIdMax = sequencenum;
        String label = null;
        if (!study.getTimepointType().isVisitBased())
        {
            // Do special handling for data-based studies
            if (study.getDefaultTimepointDuration() == 1 || sequencenum != Math.floor(sequencenum) || sequencenum < 0)
            {
                // See if there's a fractional part to the number
                if (sequencenum != Math.floor(sequencenum))
                {
                    label = "Day " + sequencenum;
                }
                else
                {
                    // If not, drop the decimal from the default name
                    label = "Day " + (int) sequencenum;
                }
            }
            else
            {
                // Try to create a timepoint that spans the default number of days
                // For example, if duration is 7 days, do timepoints for days 0-6, 7-13, 14-20, etc
                int intervalNumber = (int) sequencenum / study.getDefaultTimepointDuration();
                visitIdMin = intervalNumber * study.getDefaultTimepointDuration();
                visitIdMax = (intervalNumber + 1) * study.getDefaultTimepointDuration() - 1;

                // Scale the timepoint to be smaller if there are existing timepoints that overlap
                // on its desired day range
                if (previousVisit != Double.NEGATIVE_INFINITY)
                {
                    visitIdMin = Math.max(visitIdMin, previousVisit + 1);
                }
                if (nextVisit != Double.POSITIVE_INFINITY)
                {
                    visitIdMax = Math.min(visitIdMax, nextVisit - 1);
                }

                // Default label is "Day X"
                label = "Day " + (int) visitIdMin + " - " + (int) visitIdMax;
                if ((int) visitIdMin == (int) visitIdMax)
                {
                    // Single day timepoint, so don't use the range
                    label = "Day " + (int) visitIdMin;
                }
                else if (visitIdMin == intervalNumber * study.getDefaultTimepointDuration() &&
                        visitIdMax == (intervalNumber + 1) * study.getDefaultTimepointDuration() - 1)
                {
                    // The timepoint is the full span for the default duration, so see if we
                    // should call it "Week" or "Month"
                    if (study.getDefaultTimepointDuration() == 7)
                    {
                        label = "Week " + (intervalNumber + 1);
                    }
                    else if (study.getDefaultTimepointDuration() == 30 || study.getDefaultTimepointDuration() == 31)
                    {
                        label = "Month " + (intervalNumber + 1);
                    }
                }
            }

        }

        // create visit in shared study
        Study visitStudy = getStudyForVisits(study);
        return new VisitImpl(visitStudy.getContainer(), visitIdMin, visitIdMax, label, type);
    }

    public void importVisitAliases(Study study, User user, List<VisitAlias> aliases) throws ValidationException
    {
        DataIteratorBuilder it = new BeanDataIterator.Builder(VisitAlias.class, aliases);
        importVisitAliases(study, user, it);
    }


    public int importVisitAliases(final Study study, User user, DataIteratorBuilder loader) throws ValidationException
    {
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        // We want delete and bulk insert in the same transaction
        try (Transaction transaction = scope.ensureTransaction())
        {
            clearVisitAliases(study);

            DataIteratorContext context = new DataIteratorContext();
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(tinfo, loader, study.getContainer(), user, context);
            DataIteratorBuilder insert = ((UpdateableTableInfo) tinfo).persistRows(etl, context);
            Pump p = new Pump(insert, context);
            p.run();

            if (context.getErrors().hasErrors())
                throw context.getErrors().getRowErrors().get(0);

            transaction.commit();

            return p.getRowCount();
        }
    }


    public void clearVisitAliases(Study study)
    {
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();
        DbScope scope = tinfo.getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            Table.delete(tinfo, containerFilter);
            transaction.commit();
        }
    }


    public Map<String, Double> getVisitImportMap(Study study, boolean includeStandardMapping)
    {
        Collection<VisitAlias> customMapping = getCustomVisitImportMapping(study);
        List<VisitImpl> visits = includeStandardMapping ? StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM) : Collections.emptyList();

        Map<String, Double> map = new CaseInsensitiveHashMap<>((customMapping.size() + visits.size()) * 3 / 4);

//        // allow prepended "visit"
//        for (Visit visit : visits)
//        {
//            if (null == visit.getLabel())
//                continue;
//            String label = "visit " + visit.getLabel();
//            // Use the **first** instance of each label
//            if (!map.containsKey(label))
//                map.put(label, visit.getSequenceNumMin());
//        }

        // Load up standard label -> min sequence number mapping first
        for (Visit visit : visits)
        {
            String label = visit.getLabel();

            // Use the **first** instance of each label
            if (null != label && !map.containsKey(label))
                map.put(label, visit.getSequenceNumMin());
        }

        // Now load custom mapping, overwriting any existing standard labels
        for (VisitAlias alias : customMapping)
            map.put(alias.getName(), alias.getSequenceNum());

        return map;
    }


    // Return the custom import mapping (optinally provided by the admin), ordered by sequence num then row id (which
    // maintains import order in the case where multiple names map to the same sequence number).
    public Collection<VisitAlias> getCustomVisitImportMapping(Study study)
    {
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitAliases();

        return new TableSelector(tinfo, tinfo.getColumns("Name, SequenceNum"), containerFilter, new Sort("SequenceNum,RowId")).getCollection(VisitAlias.class);
    }


    // Return the standard import mapping (generated from Visit.Label -> Visit.SequenceNumMin), ordered by sequence
    // num for display purposes.  Include VisitAliases that won't be used, but mark them as overridden.
    public Collection<VisitAlias> getStandardVisitImportMapping(Study study)
    {
        List<VisitAlias> list = new LinkedList<>();
        Set<String> labels = new CaseInsensitiveHashSet();
        Map<String, Double> customMap = getVisitImportMap(study, false);

        List<VisitImpl> visits = StudyManager.getInstance().getVisits(study, Visit.Order.SEQUENCE_NUM);

        for (Visit visit : visits)
        {
            String label = visit.getLabel();

            if (null != label)
            {
                boolean overridden = labels.contains(label) || customMap.containsKey(label);
                list.add(new VisitAlias(label, visit.getSequenceNumMin(), visit.getSequenceString(), overridden));

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
        private String _sequenceString;
        private boolean _overridden;  // For display purposes -- we show all visits and gray out the ones that are not used

        @SuppressWarnings({"UnusedDeclaration"}) // Constructed via reflection by the Table layer
        public VisitAlias()
        {
        }

        public VisitAlias(String name, double sequenceNum, @Nullable String sequenceString, boolean overridden)
        {
            _name = name;
            _sequenceNum = sequenceNum;
            _sequenceString = sequenceString;
            _overridden = overridden;
        }

        public VisitAlias(String name, double sequenceNum)
        {
            this(name, sequenceNum, null, false);
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

        public String getSequenceNumString()
        {
            return VisitImpl.formatSequenceNum(_sequenceNum);
        }

        public String getSequenceString()
        {
            if (null == _sequenceString)
                return getSequenceNumString();
            else
                return _sequenceString;
        }
    }


    public Map<String, VisitTag> importVisitTags(Study study, User user, List<VisitTag> visitTags) throws ValidationException
    {
        // Import, don't overwrite existing
        final Map<String, VisitTag> allVisitTagMap = new HashMap<>();
        final Map<String, VisitTag> newVisitTagMap = new HashMap<>();
        for (VisitTag visitTag : visitTags)
        {
            newVisitTagMap.put(visitTag.getName(), visitTag);
        }

        Container container = getStudyForVisitTag(study).getContainer();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(container);
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTag();
        if (null == tinfo)
            throw new IllegalStateException("Study Import/Export expected TableInfo.");

        TableSelector selector = new TableSelector(tinfo, containerFilter, null);
        selector.forEach(visitTag -> {
            allVisitTagMap.put(visitTag.getName(), visitTag);
            if (newVisitTagMap.containsKey(visitTag.getName()))
                newVisitTagMap.remove(visitTag.getName());
        }, VisitTag.class);

        List<VisitTag> newVisitTags = new ArrayList<>();
        newVisitTags.addAll(newVisitTagMap.values());
        DataIteratorBuilder loader = new BeanDataIterator.Builder(VisitTag.class, newVisitTags);
        DbScope scope = tinfo.getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            DataIteratorContext context = new DataIteratorContext();
            context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            StandardDataIteratorBuilder etl = StandardDataIteratorBuilder.forInsert(tinfo, loader, container, user, context);
            DataIteratorBuilder insert = ((UpdateableTableInfo) tinfo).persistRows(etl, context);
            Pump p = new Pump(insert, context);
            p.run();

            BatchValidationException errors = context.getErrors();
            if (errors.hasErrors())
                throw errors.getRowErrors().get(0);

            transaction.commit();
        }
        allVisitTagMap.putAll(newVisitTagMap);
        return allVisitTagMap;
    }


    public Integer createVisitTagMapEntry(User user, Container container, String visitTagName, @NotNull Integer visitId, @Nullable Integer cohortId)
    {
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTagMap();
        Map<String, Object> map = new CaseInsensitiveHashMap<>();
        map.put("visitTag", visitTagName);
        map.put("visitId", visitId);
        map.put("cohortId", cohortId);
        map.put("containerId", container.getId());
        map = Table.insert(user, tinfo, map);
        return (Integer) map.get("RowId");
    }

    @Nullable
    public String checkSingleUseVisitTag(VisitTag visitTag, @Nullable Integer cohortId, @NotNull List<VisitTagMapEntry> visitTagMapEntries,
                                         @Nullable Integer oldRowId, Container container, User user)
    {
        for (VisitTagMapEntry visitTagMapEntry : visitTagMapEntries)
            if ((null == oldRowId || !oldRowId.equals(visitTagMapEntry.getRowId())) &&
                    ((null == cohortId && null == visitTagMapEntry.getCohortId()) || null != cohortId && cohortId.equals(visitTagMapEntry.getCohortId())))
            {
                Cohort cohort = null != cohortId ? getCohortForRowId(container, user, cohortId) : null;
                return "Single use visit tag '" + visitTag.getCaption() +
                        "' may not be used for more than one visit for the same cohort '" + (null != cohort ? cohort.getLabel() : "<null>") + "'.";
            }
        return null;
    }

    public Map<String, VisitTag> getVisitTags(Study study)
    {
        // TODO: Use QueryHelper?
        final Map<String, VisitTag> visitTags = new HashMap<>();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTag();
        new TableSelector(tinfo, containerFilter, null).forEach(visitTag -> visitTags.put(visitTag.getName(), visitTag), VisitTag.class);
        return visitTags;
    }

    public
    @Nullable
    VisitTag getVisitTag(Study study, String visitTagName)
    {
        final List<VisitTag> visitTags = new ArrayList<>();
        SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
        filter.addCondition(FieldKey.fromString("Name"), visitTagName);
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTag();
        new TableSelector(tinfo, filter, null).forEach(visitTags::add, VisitTag.class);

        if (visitTags.isEmpty())
            return null;
        if (visitTags.size() > 1)
            throw new IllegalStateException("Expected only one visit tag with given name.");
        return visitTags.get(0);
    }

    public Map<Integer, List<VisitTagMapEntry>> getVisitTagMapMap(Study study)
    {
        final Map<Integer, List<VisitTagMapEntry>> visitTagMapMap = new HashMap<>();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTagMap();
        new TableSelector(tinfo, containerFilter, null).forEach(visitTagMapEntry -> {
            if (!visitTagMapMap.containsKey(visitTagMapEntry.getVisitId()))
                visitTagMapMap.put(visitTagMapEntry.getVisitId(), new ArrayList<>());
            visitTagMapMap.get(visitTagMapEntry.getVisitId()).add(visitTagMapEntry);
        }, VisitTagMapEntry.class);

        return visitTagMapMap;
    }

    public Map<String, List<VisitTagMapEntry>> getVisitTagToVisitTagMapEntries(Study study)
    {
        final Map<String, List<VisitTagMapEntry>> visitTagToVisitTagMapEntries = new HashMap<>();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTagMap();
        new TableSelector(tinfo, containerFilter, null).forEach(visitTagMapEntry -> {
            if (!visitTagToVisitTagMapEntries.containsKey(visitTagMapEntry.getVisitTag()))
                visitTagToVisitTagMapEntries.put(visitTagMapEntry.getVisitTag(), new ArrayList<>());
            visitTagToVisitTagMapEntries.get(visitTagMapEntry.getVisitTag()).add(visitTagMapEntry);
        }, VisitTagMapEntry.class);

        return visitTagToVisitTagMapEntries;
    }

    public List<VisitTagMapEntry> getVisitTagMapEntries(Study study, String visitTagName)
    {
        final List<VisitTagMapEntry> visitTagMapEntries = new ArrayList<>();
        SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
        filter.addCondition(FieldKey.fromString("VisitTag"), visitTagName);
        TableInfo tinfo = StudySchema.getInstance().getTableInfoVisitTagMap();
        new TableSelector(tinfo, filter, null).forEach(visitTagMapEntries::add, VisitTagMapEntry.class);

        return visitTagMapEntries;
    }

    public static String makeVisitTagMapKey(String visitTagName, int visitId, @Nullable Integer cohortId)
    {
        return visitTagName + "/" + visitId + "/" + cohortId;
    }


    public void createCohort(Study study, User user, CohortImpl cohort)
    {
        if (cohort.getContainer() != null && !cohort.getContainer().equals(study.getContainer()))
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


    public void deleteVisit(StudyImpl study, VisitImpl visit, User user)
    {
        deleteVisits(study, Collections.singleton(visit), user, false);
    }

    /*
        Delete multiple visits; more efficient than calling deleteVisit() in a loop.
    */
    public void deleteVisits(StudyImpl study, Collection<VisitImpl> visits, User user, boolean unused)
    {
        // Short circuit on empty
        if (visits.isEmpty())
            return;

        // Extract visit rowIds
        Collection<Integer> visitIds = CollectionUtils.collect(visits, VisitImpl::getRowId);

        StudySchema schema = StudySchema.getInstance();
        SQLFragment visitInClause = new SQLFragment();
        schema.getSqlDialect().appendInClauseSql(visitInClause, visitIds);

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            if (!unused)
            {
                for (DatasetDefinition def : study.getDatasets())
                {
                    TableInfo t = def.getStorageTableInfo();
                    if (null == t)
                        continue;

                    SQLFragment sqlf = new SQLFragment();
                    sqlf.append("DELETE FROM ");
                    sqlf.append(t.getSelectName());
                    if (schema.getSqlDialect().isSqlServer())
                        sqlf.append(" WITH (UPDLOCK)");
                    sqlf.append(" WHERE LSID IN (SELECT LSID FROM ");
                    sqlf.append(t.getSelectName());
                    sqlf.append(" d, ");
                    sqlf.append(StudySchema.getInstance().getTableInfoParticipantVisit(), "pv");
                    sqlf.append(" WHERE d.ParticipantId = pv.ParticipantId AND d.SequenceNum = pv.SequenceNum AND pv.Container = ?");
                    sqlf.add(study.getContainer());
                    sqlf.append(" AND pv.VisitRowId ").append(visitInClause).append(')');

                    int count = new SqlExecutor(schema.getSchema()).execute(sqlf);
                    if (count > 0)
                        StudyManager.datasetModified(def, user, true);
                }

                for (VisitImpl visit : visits)
                {
                    // Delete samples first because we may need ParticipantVisit to figure out which samples
                    SpecimenManager.getInstance().deleteSamplesForVisit(visit);

                    TreatmentManager.getInstance().deleteTreatmentVisitMapForVisit(study.getContainer(), visit.getRowId());
                    deleteAssaySpecimenVisits(study.getContainer(), visit.getRowId());
                }
            }

            SQLFragment sqlFragParticipantVisit = new SQLFragment("DELETE FROM " + schema.getTableInfoParticipantVisit() + "\n" +
                    "WHERE Container = ?").add(study.getContainer());
            sqlFragParticipantVisit.append(" AND VisitRowId ").append(visitInClause);
            new SqlExecutor(schema.getSchema()).execute(sqlFragParticipantVisit);

            SQLFragment sqlFragVisitMap = new SQLFragment("DELETE FROM " + schema.getTableInfoVisitMap() + "\n" +
                    "WHERE Container = ?").add(study.getContainer());
            sqlFragVisitMap.append(" AND VisitRowId ").append(visitInClause);
            new SqlExecutor(schema.getSchema()).execute(sqlFragVisitMap);

            // UNDONE broken _visitHelper.delete(visit);
            try
            {
                Study visitStudy = getStudyForVisits(study);

                for (VisitImpl visit : visits)
                {
                    try
                    {
                        Table.delete(schema.getTableInfoVisit(), new Object[]{visitStudy.getContainer(), visit.getRowId()});
                    }
                    finally
                    {
                        _visitHelper.clearCache(visit);
                    }
                }
            }
            catch (OptimisticConflictException x)
            {
                /* ignore */
            }

            transaction.commit();

            getVisitManager(study).updateParticipantVisits(user, study.getDatasets());
        }
    }


    public void updateVisit(User user, VisitImpl visit)
    {
        _visitHelper.update(user, visit, visit.getContainer().getId(), visit.getRowId());
    }

    public void updateCohort(User user, CohortImpl cohort)
    {
        _cohortHelper.update(user, cohort);
    }

    public void updateParticipant(User user, Participant participant)
    {
        Table.update(user,
                SCHEMA.getTableInfoParticipant(),
                participant,
                new Object[]{participant.getContainer().getId(), participant.getParticipantId()}
        );
    }


    public List<LocationImpl> getSites(Container container)
    {
//        return _locationHelper.get(container, "Label");
        return getLocations(container, null, null);
    }

    public List<LocationImpl> getLocations(final Container container, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        final List<LocationImpl> locations = new ArrayList<>();
        getLocationsSelector(container, filter, sort).forEachMap(map -> locations.add(new LocationImpl(container, map)));
        return locations;
    }

    public List<LocationImpl> getValidRequestingLocations(Container container)
    {
        Study study = getStudy(container);
        List<LocationImpl> validLocations = new ArrayList<>();
        List<LocationImpl> locations = getSites(container);
        for (LocationImpl location : locations)
        {
            if (isSiteValidRequestingLocation(study, location))
            {
                validLocations.add(location);
            }
        }
        return validLocations;
    }

    public boolean isSiteValidRequestingLocation(Container container, int id)
    {
        Study study = getStudy(container);
        LocationImpl location = getLocation(container, id);
        return isSiteValidRequestingLocation(study, location);
    }

    private boolean isSiteValidRequestingLocation(Study study, LocationImpl location)
    {
        if (null == location)
            return false;

        if (location.isRepository() && study.isAllowReqLocRepository())
        {
            return true;
        }
        if (location.isClinic() && study.isAllowReqLocClinic())
        {
            return true;
        }
        if (location.isSal() && study.isAllowReqLocSal())
        {
            return true;
        }
        if (location.isEndpoint() && study.isAllowReqLocEndpoint())
        {
            return true;
        }
        if (!location.isRepository() && !location.isClinic() && !location.isSal() && !location.isEndpoint())
        {   // It has no location type, so allow it
            return true;
        }
        return false;
    }

    @Nullable
    public LocationImpl getLocation(Container container, int id)
    {
//        return _locationHelper.get(container, id);
        List<LocationImpl> locations = getLocations(container, new SimpleFilter(FieldKey.fromParts("RowId"), id), null);
        if (!locations.isEmpty())
            return locations.get(0);
        return null;
    }

    public List<LocationImpl> getLocationsByLabel(Container container, String label)
    {
//        return _locationHelper.get(container, new SimpleFilter(FieldKey.fromParts("Label"), label));
        return getLocations(container, new SimpleFilter(FieldKey.fromParts("Label"), label), null);
    }

    public void createSite(User user, LocationImpl location)
    {
//        _locationHelper.create(user, location);
        Table.insert(user, getTableInfoLocations(location.getContainer()), location);
    }

    public void updateSite(User user, LocationImpl location)
    {
//        _locationHelper.update(user, location);
        Table.update(user, getTableInfoLocations(location.getContainer()), location, location.getRowId());
    }

    private boolean isLocationInUse(LocationImpl loc, TableInfo table, String... columnNames)
    {
        List<Object> params = new ArrayList<>();
        params.add(loc.getContainer().getId());

        StringBuilder cols = new StringBuilder("(");
        String or = "";
        for (String columnName : columnNames)
        {
            cols.append(or).append(columnName).append(" = ?");
            params.add(loc.getRowId());
            or = " OR ";
        }
        cols.append(")");

        String containerColumn = " = ? AND ";
        if (table.getName().contains("_vial") || table.getName().equals("_specimenevent"))
        {
            //vials and events use a column called fr_container instead of normal container.
            containerColumn = "FR_Container" + containerColumn;
        }
        else if (table.getName().contains("_specimen"))
        {
            params.remove(0);
            containerColumn = "";
        }
        else
        {
            containerColumn = "Container" + containerColumn;
        }
        return new SqlSelector(StudySchema.getInstance().getSchema(), new SQLFragment("SELECT * FROM " +
                table + " WHERE " + containerColumn + cols.toString(), params)).exists();
    }

    public boolean isLocationInUse(LocationImpl loc)
    {
        return isLocationInUse(loc, StudySchema.getInstance().getTableInfoSampleRequest(), "DestinationSiteId") ||
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoSampleRequestRequirement(), "SiteId") ||
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoParticipant(), "EnrollmentSiteId", "CurrentSiteId") ||
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoAssaySpecimen(), "LocationId") ||
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoVial(loc.getContainer()), "CurrentLocation", "ProcessingLocation") ||
                //vials and events use a column called fr_container instead of normal container.
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoSpecimen(loc.getContainer()), "originatinglocationid", "ProcessingLocation") ||
                //Doesn't have a container or fr_container column
                isLocationInUse(loc, StudySchema.getInstance().getTableInfoSpecimenEvent(loc.getContainer()), "LabId", "OriginatingLocationId");
        //vials and events use a column called fr_container instead of normal container.
    }

    public void deleteLocation(LocationImpl location) throws ValidationException
    {
        StudySchema schema = StudySchema.getInstance();
        if (!isLocationInUse(location))
        {
            try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
            {
                Container container = location.getContainer();

                TreatmentManager.getInstance().deleteTreatmentVisitMapForCohort(container, location.getRowId());

//                _locationHelper.delete(location);
                Table.delete(getTableInfoLocations(container), new SimpleFilter(FieldKey.fromString("RowId"), location.getRowId()));

                transaction.commit();
            }
        }
        else
        {
            throw new ValidationException("Locations currently in use cannot be deleted");
        }
    }

    private TableInfo getTableInfoLocations(Container container)
    {
        return StudySchema.getInstance().getTableInfoSite(container);
    }

    private TableSelector getLocationsSelector(Container container, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        return new TableSelector(getTableInfoLocations(container), filter, sort);
    }

    public List<AssaySpecimenConfigImpl> getAssaySpecimenConfigs(Container container, String sortCol)
    {
        return _assaySpecimenHelper.get(container, sortCol);
    }

    public List<VisitImpl> getVisitsForAssaySchedule(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        List<Integer> visitRowIds = new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);

        return getSortedVisitsByRowIds(container, visitRowIds);
    }

    public List<VisitImpl> getSortedVisitsByRowIds(Container container, List<Integer> visitRowIds)
    {
        List<VisitImpl> visits = new ArrayList<>();
        Study study = getStudy(container);
        if (study != null)
        {
            for (VisitImpl v : getVisits(study, Visit.Order.DISPLAY))
            {
                if (visitRowIds.contains(v.getRowId()))
                    visits.add(v);
            }
        }
        return visits;
    }

    public List<Integer> getAssaySpecimenVisitIds(Container container, AssaySpecimenConfig assaySpecimenConfig)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("AssaySpecimenId"), assaySpecimenConfig.getRowId());

        return new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(),
                Collections.singleton("VisitId"), filter, new Sort("VisitId")).getArrayList(Integer.class);
    }

    public void deleteAssaySpecimenVisits(Container container, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitId"), rowId);
        Table.delete(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), filter);
    }

    public String getStudyDesignAssayLabelByName(Container container, String name)
    {
        return getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignAssays(), name);
    }

    public String getStudyDesignLabLabelByName(Container container, String name)
    {
        return getStudyDesignLabelByName(container, StudySchema.getInstance().getTableInfoStudyDesignLabs(), name);
    }

    public String getStudyDesignLabelByName(Container container, TableInfo tableInfo, String name)
    {
        // first look in the current container for the StudyDesign record, then look for it at the project level
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        String label = new TableSelector(tableInfo, Collections.singleton("Label"), filter, null).getObject(String.class);
        if (label == null && !container.isProject())
        {
            filter = SimpleFilter.createContainerFilter(container.getProject());
            filter.addCondition(FieldKey.fromParts("Name"), name);
            label = new TableSelector(tableInfo, Collections.singleton("Label"), filter, null).getObject(String.class);
        }

        return label;
    }

    public void createVisitDatasetMapping(User user, Container container, int visitId, int datasetId, boolean isRequired)
    {
        VisitDataset vds = new VisitDataset(container, datasetId, visitId, isRequired);
        Table.insert(user, SCHEMA.getTableInfoVisitMap(), vds);
    }

    public VisitDataset getVisitDatasetMapping(Container container, int visitRowId, int datasetId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("VisitRowId"), visitRowId);
        filter.addCondition(FieldKey.fromParts("DataSetId"), datasetId);

        Boolean required = new TableSelector(SCHEMA.getTableInfoVisitMap().getColumn("Required"), filter, null).getObject(Boolean.class);

        return (null != required ? new VisitDataset(container, datasetId, visitRowId, required) : null);
    }


    public List<VisitImpl> getVisits(Study study, Visit.Order order)
    {
        return getVisits(study, null, null, order);
    }

    public List<VisitImpl> getVisits(Study study, @Nullable CohortImpl cohort, @Nullable User user, Visit.Order order)
    {
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return Collections.emptyList();

        SimpleFilter filter = null;

        Study visitStudy = getStudyForVisits(study);

        if (cohort != null)
        {
            filter = SimpleFilter.createContainerFilter(visitStudy.getContainer());
            if (showCohorts(study.getContainer(), user))
                filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[]{cohort.getRowId()});
        }

        return _visitHelper.get(visitStudy.getContainer(), filter, order.getSortColumns());
    }

    public void clearParticipantVisitCaches(Study study)
    {
        _visitHelper.clearCache(study.getContainer());

        // clear shared study
        Study visitStudy = getStudyForVisits(study);
        if (!study.equals(visitStudy))
            _visitHelper.clearCache(visitStudy.getContainer());

        DbCache.clear(StudySchema.getInstance().getTableInfoParticipant());
        for (StudyImpl substudy : StudyManager.getInstance().getAncillaryStudies(study.getContainer()))
            clearParticipantVisitCaches(substudy);
    }


    public VisitImpl getVisitForRowId(Study study, int rowId)
    {
        Study visitStudy = getStudyForVisits(study);

        return _visitHelper.get(visitStudy.getContainer(), rowId, "RowId");
    }

    private final DatabaseCache<List<QCState>> _qcStateCache = new DatabaseCache<>(StudySchema.getInstance().getScope(), 1000, "QCStates");

    @NotNull
    public List<QCState> getQCStates(Container container)
    {
        return _qcStateCache.get(container.getId(), container, new CacheLoader<String, List<QCState>>()
        {
            @Override
            public List<QCState> load(String key, @Nullable Object argument)
            {
                Container container = (Container) argument;
                SimpleFilter filter = SimpleFilter.createContainerFilter(container);
                return Collections.unmodifiableList(new TableSelector(StudySchema.getInstance().getTableInfoQCState(), filter, new Sort("Label")).getArrayList(QCState.class));
            }
        });
    }

    public boolean showQCStates(Container container)
    {
        return !getQCStates(container).isEmpty();
    }

    public boolean isQCStateInUse(QCState state)
    {
        StudyImpl study = getStudy(state.getContainer());
        if (safeIntegersEqual(study.getDefaultAssayQCState(), state.getRowId()) ||
                safeIntegersEqual(study.getDefaultDirectEntryQCState(), state.getRowId()) ||
                safeIntegersEqual(study.getDefaultPipelineQCState(), state.getRowId()))
        {
            return true;
        }
        SQLFragment f = new SQLFragment();
        f.append("SELECT * FROM ").append(
                StudySchema.getInstance().getTableInfoStudyData(study, null).getFromSQL("SD")).append(
                " WHERE QCState = ? AND Container = ?");
        f.add(state.getRowId());
        f.add(state.getContainer());

        return new SqlSelector(StudySchema.getInstance().getSchema(), f).exists();
    }

    public QCState insertQCState(User user, QCState state)
    {
        List<QCState> preInsertStates = getQCStates(state.getContainer());
        _qcStateCache.remove(state.getContainer().getId());
        QCState newState = Table.insert(user, StudySchema.getInstance().getTableInfoQCState(), state);
        // switching from zero to more than zero QC states affects the columns in our materialized datasets
        // (adding a QC State column), so we unmaterialize them here:
        if (preInsertStates.isEmpty())
            clearCaches(state.getContainer(), true);
        return newState;
    }

    public QCState updateQCState(User user, QCState state)
    {
        _qcStateCache.remove(state.getContainer().getId());
        return Table.update(user, StudySchema.getInstance().getTableInfoQCState(), state, state.getRowId());
    }

    public void deleteQCState(QCState state)
    {
        List<QCState> preDeleteStates = getQCStates(state.getContainer());
        _qcStateCache.remove(state.getContainer().getId());
        Table.delete(StudySchema.getInstance().getTableInfoQCState(), state.getRowId());

        // removing our last QC state affects the columns in our materialized datasets
        // (removing a QC State column), so we unmaterialize them here:
        if (preDeleteStates.size() == 1)
            clearCaches(state.getContainer(), true);

    }

    @Nullable
    public QCState getDefaultQCState(StudyImpl study)
    {
        Integer defaultQcStateId = study.getDefaultDirectEntryQCState();
        QCState defaultQCState = null;
        if (defaultQcStateId != null)
            defaultQCState = StudyManager.getInstance().getQCStateForRowId(
                    study.getContainer(), defaultQcStateId);
        return defaultQCState;
    }

    public QCState getQCStateForRowId(Container container, int rowId)
    {
        for (QCState state : getQCStates(container))
        {
            if (state.getRowId() == rowId && state.getContainer().equals(container))
                return state;
        }
        return null;
    }

    private Map<String, VisitImpl> getVisitsForDataRows(DatasetDefinition def, Collection<String> dataLsids)
    {
        final Map<String, VisitImpl> visits = new HashMap<>();

        if (dataLsids == null || dataLsids.isEmpty())
            return visits;

        final Study study = def.getStudy();
        final Study visitStudy = getStudyForVisits(study);

        TableInfo ds = def.getTableInfo(null, false);

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT sd.LSID AS LSID, v.RowId AS RowId FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = ? AND v.Container = ?\n" +
                "WHERE sd.lsid ");
        sql.add(def.getContainer().getId());
        // shared visit container
        sql.add(visitStudy.getContainer().getId());

        StudySchema.getInstance().getSqlDialect().appendInClauseSql(sql, dataLsids);

        new SqlSelector(StudySchema.getInstance().getSchema(), sql).forEach(rs -> {
            String lsid = rs.getString("LSID");
            int visitId = rs.getInt("RowId");
            visits.put(lsid, getVisitForRowId(study, visitId));
        });

        return visits;
    }

    public List<VisitImpl> getVisitsForDataset(Container container, int datasetId)
    {
        List<VisitImpl> visits = new ArrayList<>();

        DatasetDefinition def = getDatasetDefinition(getStudy(container), datasetId);
        TableInfo ds = def.getTableInfo(null, false);

        final Study study = def.getStudy();
        final Study visitStudy = getStudyForVisits(study);

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT v.RowId AS RowId FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "JOIN study.ParticipantVisit pv ON \n" +
                "\tsd.SequenceNum = pv.SequenceNum AND\n" +
                "\tsd.ParticipantId = pv.ParticipantId\n" +
                "JOIN study.Visit v ON\n" +
                "\tpv.VisitRowId = v.RowId AND\n" +
                "\tpv.Container = ? AND v.Container = ?\n");
        sql.add(container.getId());
        // shared visit container
        sql.add(visitStudy.getContainer().getId());

        SqlSelector selector = new SqlSelector(StudySchema.getInstance().getSchema(), sql);
        for (Integer rowId : selector.getArray(Integer.class))
        {
            visits.add(getVisitForRowId(study, rowId));
        }
        return visits;
    }

    public List<Double> getUndefinedSequenceNumsForDataset(Container container, int datasetId)
    {
        DatasetDefinition def = getDatasetDefinition(getStudy(container), datasetId);
        TableInfo ds = def.getTableInfo(null, false);
        Study visitStudy = getStudyForVisits(def.getStudy());

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT DISTINCT sd.SequenceNum FROM ").append(ds.getFromSQL("sd")).append("\n" +
                "LEFT JOIN study.Visit v ON\n" +
                "\tsd.SequenceNum >= v.SequenceNumMin AND sd.SequenceNum <=v.SequenceNumMax AND v.Container = ?\n" +
                "WHERE v.RowId IS NULL"
        );
        // shared visit container
        sql.add(visitStudy.getContainer().getId());

        SqlSelector selector = new SqlSelector(StudySchema.getInstance().getSchema(), sql);
        return selector.getArrayList(Double.class);
    }

    public void updateDataQCState(Container container, User user, int datasetId, Collection<String> lsids, QCState newState, String comments)
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        Study study = getStudy(container);
        DatasetDefinition def = getDatasetDefinition(study, datasetId);

        Map<String, VisitImpl> lsidVisits = null;
        if (!def.isDemographicData())
            lsidVisits = getVisitsForDataRows(def, lsids);
        List<Map<String, Object>> rows = def.getDatasetRows(user, lsids);
        if (rows.isEmpty())
            return;

        Map<String, String> oldQCStates = new HashMap<>();
        Map<String, String> newQCStates = new HashMap<>();

        Set<String> updateLsids = new HashSet<>();
        for (Map<String, Object> row : rows)
        {
            String lsid = (String) row.get("lsid");

            Integer oldStateId = (Integer) row.get(DatasetTableImpl.QCSTATE_ID_COLNAME);
            QCState oldState = null;
            if (oldStateId != null)
                oldState = getQCStateForRowId(container, oldStateId);

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

        try (Transaction transaction = scope.ensureTransaction())
        {
            // TODO fix updating across study data
            SQLFragment sql = new SQLFragment("UPDATE " + def.getStorageTableInfo().getSelectName() + "\n" +
                    "SET QCState = ");
            // do string concatenation, rather that using a parameter, for the new state id because Postgres null
            // parameters are typed which causes a cast exception trying to set the value back to null (bug 6370)
            sql.append(newState != null ? newState.getRowId() : "NULL");
            sql.append(", modified = ?");
            sql.add(new Date());
            sql.append("\nWHERE lsid ");
            StudySchema.getInstance().getSqlDialect().appendInClauseSql(sql, updateLsids);

            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);

            //def.deleteFromMaterialized(user, updateLsids);
            //def.insertIntoMaterialized(user, updateLsids);

            String auditComment = "QC state was changed for " + updateLsids.size() + " record" +
                    (updateLsids.size() == 1 ? "" : "s") + ".  User comment: " + comments;

            DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(container.getId(), auditComment);

            if (container.getProject() != null)
                event.setProjectId(container.getProject().getId());
            event.setDatasetId(datasetId);
            event.setHasDetails(true);
            event.setOldRecordMap(AbstractAuditTypeProvider.encodeForDataMap(oldQCStates));
            event.setNewRecordMap(AbstractAuditTypeProvider.encodeForDataMap(newQCStates));

            AuditLogService.get().addEvent(user, event);
            clearCaches(container, false);

            transaction.commit();
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

    public boolean showCohorts(Container container, @Nullable User user)
    {
        if (user == null)
            return false;

        if (user.hasRootAdminPermission())
            return true;

        StudyImpl study = StudyManager.getInstance().getStudy(container);

        if (study == null)
            return false;

        Integer cohortDatasetId = study.getParticipantCohortDatasetId();
        if (study.isManualCohortAssignment() || null == cohortDatasetId || -1 == cohortDatasetId)
        {
            // If we're not reading from a dataset for cohort definition,
            // we use the container's permission
            return SecurityPolicyManager.getPolicy(container).hasPermission(user, ReadPermission.class);
        }

        // Automatic cohort assignment -- can the user read the source dataset?
        DatasetDefinition def = getDatasetDefinition(study, cohortDatasetId);

        if (def != null)
            return def.canRead(user);

        return false;
    }

    public void assertCohortsViewable(Container container, User user)
    {
        if (!showCohorts(container, user))
            throw new UnauthorizedException("User does not have permission to view cohort information");
    }

    public List<CohortImpl> getCohorts(Container container, User user)
    {
        assertCohortsViewable(container, user);
        return _cohortHelper.get(container, "Label");
    }

    public CohortImpl getCurrentCohortForParticipant(Container container, User user, String participantId)
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
        return _cohortHelper.get(container, rowId);
    }

    public CohortImpl getCohortByLabel(Container container, User user, String label)
    {
        assertCohortsViewable(container, user);
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("Label"), label);

        List<CohortImpl> cohorts = _cohortHelper.get(container, filter);
        if (cohorts != null && cohorts.size() == 1)
            return cohorts.get(0);

        return null;
    }

    private boolean isCohortInUse(CohortImpl cohort, Container c, TableInfo table, String... columnNames)
    {
        List<Object> params = new ArrayList<>();
        params.add(c.getId());

        StringBuilder cols = new StringBuilder("(");
        String or = "";
        for (String columnName : columnNames)
        {
            cols.append(or).append(columnName).append(" = ?");
            params.add(cohort.getRowId());
            or = " OR ";
        }
        cols.append(")");

        return new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT * FROM " +
                table + " WHERE Container = ? AND " + cols.toString(), params).exists();
    }

    public boolean isCohortInUse(CohortImpl cohort)
    {
        Container c = cohort.getContainer();
        Study visitStudy = getStudyForVisits(getStudy(c));

        return isCohortInUse(cohort, c, StudySchema.getInstance().getTableInfoDataset(), "CohortId") ||
                isCohortInUse(cohort, c, StudySchema.getInstance().getTableInfoParticipant(), "CurrentCohortId", "InitialCohortId") ||
                isCohortInUse(cohort, c, StudySchema.getInstance().getTableInfoParticipantVisit(), "CohortId") ||
                isCohortInUse(cohort, visitStudy.getContainer(), StudySchema.getInstance().getTableInfoVisit(), "CohortId");
    }

    public void deleteCohort(CohortImpl cohort)
    {
        StudySchema schema = StudySchema.getInstance();

        try (Transaction transaction = schema.getSchema().getScope().ensureTransaction())
        {
            Container container = cohort.getContainer();

            TreatmentManager.getInstance().deleteTreatmentVisitMapForCohort(container, cohort.getRowId());

            _cohortHelper.delete(cohort);

            // delete extended properties
            String lsid = cohort.getLsid();
            Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, lsid);
            if (resourceProperties != null && !resourceProperties.isEmpty())
            {
                OntologyManager.deleteOntologyObject(lsid, container, false);
            }

            transaction.commit();
        }
    }


    public VisitImpl getVisitForSequence(Study study, double seqNum)
    {
        List<VisitImpl> visits = getVisits(study, Visit.Order.SEQUENCE_NUM);
        for (VisitImpl v : visits)
        {
            if (seqNum >= v.getSequenceNumMin() && seqNum <= v.getSequenceNumMax())
                return v;
        }
        return null;
    }

    public List<DatasetDefinition> getDatasetDefinitions(Study study)
    {
        return getDatasetDefinitions(study, null);
    }


    public List<DatasetDefinition> getDatasetDefinitions(Study study, @Nullable CohortImpl cohort, String... types)
    {
        List<DatasetDefinition> local = getDatasetDefinitionsLocal(study, cohort, types);
        List<DatasetDefinition> shared = Collections.emptyList();
        List<DatasetDefinition> combined;

        Study sharedStudy = getSharedStudy(study);
        if (null != sharedStudy)
            shared = getDatasetDefinitionsLocal(sharedStudy, cohort, types);

        if (shared.isEmpty())
            combined = local;
        else
        {
            // NOTE: it's confusing that both ID and name are unique, manage page should warn about funny inconsistencies
            // NOTE: here we'll have LOCAL datasets hide SHARED datasets by both id and name until I have a better idea
            CaseInsensitiveHashSet names = new CaseInsensitiveHashSet();
            HashSet<Integer> ids = new HashSet<>();

            combined = new ArrayList<>(local.size() + shared.size());
            for (DatasetDefinition dsd : local)
            {
                combined.add(dsd);
                names.add(dsd.getName());
                ids.add(dsd.getDatasetId());
            }
            for (DatasetDefinition dsd : shared)
            {
                if (!names.contains(dsd.getName()) && !ids.contains(dsd.getDatasetId()))
                {
                    DatasetDefinition wrapped = dsd.createLocalDatasetDefintion((StudyImpl) study);
                    combined.add(wrapped);
                }
            }
        }

        // sort by display order, category, and dataset ID
        combined.sort((o1, o2) ->
        {
            if (o1.getDisplayOrder() != 0 || o2.getDisplayOrder() != 0)
                return o1.getDisplayOrder() - o2.getDisplayOrder();

            if (StringUtils.equals(o1.getCategory(), o2.getCategory()))
                return o1.getDatasetId() - o2.getDatasetId();

            if (o1.getCategory() != null && o2.getCategory() == null)
                return -1;
            if (o1.getCategory() == null && o2.getCategory() != null)
                return 1;
            if (o1.getCategory() != null && o2.getCategory() != null)
                return o1.getCategory().compareTo(o2.getCategory());

            return o1.getDatasetId() - o2.getDatasetId();
        });

        return Collections.unmodifiableList(combined);
    }


    /**
     * Get the list of datasets that are 'shadowed' by the list of local dataset definitions or for any local dataset in the study.
     * This is pretty much the inverse of getDatasetDefinitions()
     * This can be used in the management/admin UI to warn about shadowed datasets
     */
    public List<DatasetDefinition> getShadowedDatasets(@NotNull Study study, @Nullable List<DatasetDefinition> local)
    {
        if (study.getContainer().isProject())
            return Collections.emptyList();

        Study sharedStudy = getSharedStudy(study);
        if (null == sharedStudy)
            return Collections.emptyList();

        if (null == local)
            local = getDatasetDefinitionsLocal(study, null);
        List<DatasetDefinition> shared = getDatasetDefinitionsLocal(sharedStudy, null);

        if (local.isEmpty() || shared.isEmpty())
            return Collections.emptyList();

        CaseInsensitiveHashSet names = new CaseInsensitiveHashSet();
        HashSet<Integer> ids = new HashSet<>();

        for (DatasetDefinition dsd : local)
        {
            if (dsd.getDefinitionContainer().equals(dsd.getContainer()))
            {
                names.add(dsd.getName());
                ids.add(dsd.getDatasetId());
            }
        }
        Map<Integer,DatasetDefinition> shadowed = new TreeMap<>();
        for (DatasetDefinition dsd : shared)
        {
            if (names.contains(dsd.getName()) || ids.contains(dsd.getDatasetId()))
                shadowed.put(dsd.getDatasetId(), dsd);
        }

        return new ArrayList<>(shadowed.values());
    }


    public List<DatasetDefinition> getDatasetDefinitionsLocal(Study study, @Nullable CohortImpl cohort, String... types)
    {
        SimpleFilter filter = null;
        if (cohort != null)
        {
            filter = SimpleFilter.createContainerFilter(study.getContainer());
            filter.addWhereClause("(CohortId IS NULL OR CohortId = ?)", new Object[] { cohort.getRowId() });
        }

        if (types != null && types.length > 0)
        {
            // ignore during upgrade
            ColumnInfo typeCol = StudySchema.getInstance().getTableInfoDataset().getColumn("Type");
            if (null != typeCol && !typeCol.isUnselectable())
            {
                if (filter == null)
                    filter = SimpleFilter.createContainerFilter(study.getContainer());
                filter.addInClause(FieldKey.fromParts("Type"), Arrays.asList(types));
            }
        }

        // Make a copy (it's immutable) so that we can sort it. See issue 17875
        List<DatasetDefinition> datasets = new ArrayList<>(_datasetHelper.get(study.getContainer(), filter, null));
        return datasets;
    }


    public Set<PropertyDescriptor> getSharedProperties(Study study)
    {
        return _sharedProperties.get(study.getContainer());
    }


    @Nullable
    public DatasetDefinition getDatasetDefinition(Study s, int id)
    {
        DatasetDefinition ds = _datasetHelper.get(s.getContainer(), id);
        // update old rows w/o entityid
        if (null != ds && null == ds.getEntityId())
        {
            ds.setEntityId(GUID.makeGUID());
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute("UPDATE study.dataset SET entityId=? WHERE container=? and datasetid=? and entityid IS NULL", ds.getEntityId(), ds.getContainer().getId(), ds.getDatasetId());
            _datasetHelper.clearCache(ds);
            ds = _datasetHelper.get(s.getContainer(), id);
            // calling updateDatasetDefinition() during load (getDatasetDefinition()) may cause recursion problems
            //updateDatasetDefinition(null, ds);
        }
        if (null != ds)
            return ds;

        Study sharedStudy = getSharedStudy(s);
        if (null == sharedStudy)
            return null;

        ds = getDatasetDefinition(sharedStudy, id);
        if (null == ds)
            return null;
        return ds.createLocalDatasetDefintion((StudyImpl) s);
    }


    @Nullable
    public DatasetDefinition getDatasetDefinitionByLabel(Study s, String label)
    {
        if (label == null)
        {
            return null;
        }
        
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addWhereClause("LOWER(Label) = ?", new Object[]{label.toLowerCase()}, FieldKey.fromParts("Label"));

        List<DatasetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        return null;
    }


    @Nullable
    public DatasetDefinition getDatasetDefinitionByEntityId(Study s, String entityId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addCondition(FieldKey.fromParts("EntityId"), entityId);

        List<DatasetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        return null;
    }
    

    @Nullable
    public DatasetDefinition getDatasetDefinitionByName(Study s, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(s.getContainer());
        filter.addWhereClause("LOWER(Name) = ?", new Object[]{name.toLowerCase()}, FieldKey.fromParts("Name"));

        List<DatasetDefinition> defs = _datasetHelper.get(s.getContainer(), filter);
        if (defs != null && defs.size() == 1)
            return defs.get(0);

        Study sharedStudy = getSharedStudy(s);
        if (null == sharedStudy)
            return null;

        DatasetDefinition def = getDatasetDefinitionByName(sharedStudy, name);
        if (null == def)
            return null;
        return def.createLocalDatasetDefintion((StudyImpl) s);
    }


    @Nullable
    public DatasetDefinition getDatasetDefinitionByQueryName(Study study, String queryName)
    {
        // first try resolving the dataset def by name and then by label
        DatasetDefinition def = getDatasetDefinitionByName(study, queryName);
        if (null != def)
            return def;
        def = StudyManager.getInstance().getDatasetDefinitionByLabel(study, queryName);
        if (null != def)
            return def;

        // try shared study
        if (study.getContainer().isProject())
            return null;
        Study shared = StudyManager.getInstance().getSharedStudy(study);
        if (null == shared)
            return null;

        // first try resolving the dataset def by name and then by label
        def = StudyManager.getInstance().getDatasetDefinitionByName(shared, queryName);
        if (null != def)
            return def.createLocalDatasetDefintion((StudyImpl) study);
        def = StudyManager.getInstance().getDatasetDefinitionByLabel(shared, queryName);
        if (null != def)
            return def.createLocalDatasetDefintion((StudyImpl) study);

        return null;
    }


    // domainURI -> <Container,DatasetId>
    private static Cache<String, Pair<String, Integer>> domainCache = CacheManager.getCache(1000, CacheManager.DAY, "Domain->Dataset map");

    private CacheLoader<String, Pair<String, Integer>> loader = new CacheLoader<String, Pair<String, Integer>>()
    {
        @Override
        public Pair<String, Integer> load(String domainURI, Object argument)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT Container, DatasetId FROM study.Dataset WHERE TypeURI=?");
            sql.add(domainURI);

            Map<String, Object> map = new SqlSelector(StudySchema.getInstance().getSchema(), sql).getMap();

            if (null == map)
                return null;
            else
                return new Pair<>((String)map.get("Container"), (Integer)map.get("DatasetId"));
        }
    };


    @Nullable
    DatasetDefinition getDatasetDefinition(String domainURI)
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
                DatasetDefinition ret = StudyManager.getInstance().getDatasetDefinition(study, p.second);
                if (null != ret && null != ret.getDomain() && StringUtils.equalsIgnoreCase(ret.getDomain().getTypeURI(), domainURI))
                    return ret;
            }
            domainCache.remove(domainURI);
        }
        return null;
    }


    public List<String> getDatasetLSIDs(User user, DatasetDefinition def)
    {
        TableInfo tInfo = def.getTableInfo(user, true);
        return new TableSelector(tInfo.getColumn("lsid")).getArrayList(String.class);
    }


    public void uncache(DatasetDefinition def)
    {
        if (null == def)
            return;

        _log.debug("Uncaching dataset: " + def.getName(), new Throwable());

        _datasetHelper.clearCache(def);
        String uri = def.getTypeURI();
        if (null != uri)
            domainCache.remove(uri);

        // Also clear caches of subjects and visits- changes to this dataset may have affected this data:
        clearParticipantVisitCaches(def.getStudy());
    }


    public Map<VisitMapKey,Boolean> getRequiredMap(Study study)
    {
        TableInfo tableVisitMap = StudySchema.getInstance().getTableInfoVisitMap();
        final HashMap<VisitMapKey,Boolean> map = new HashMap<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), "SELECT DatasetId, VisitRowId, Required FROM " + tableVisitMap + " WHERE Container = ?",
                study.getContainer()).forEach(rs -> map.put(new VisitMapKey(rs.getInt(1), rs.getInt(2)), rs.getBoolean(3)));

        return map;
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

    List<VisitDataset> getMapping(final VisitImpl visit)
    {
        if (visit.getContainer() == null)
            throw new IllegalStateException("Visit has no container");

        final List<VisitDataset> visitDatasets = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_VISIT,
                visit.getContainer(), visit.getRowId()).forEach(rs -> {
                    int datasetId = rs.getInt("DataSetId");
                    boolean isRequired = rs.getBoolean("Required");
                    visitDatasets.add(new VisitDataset(visit.getContainer(), datasetId, visit.getRowId(), isRequired));
                });

        return visitDatasets;
    }


    public List<VisitDataset> getMapping(final Dataset dataset)
    {
        final List<VisitDataset> visitDatasets = new ArrayList<>();

        new SqlSelector(StudySchema.getInstance().getSchema(), VISITMAP_JOIN_BY_DATASET,
                dataset.getContainer(), dataset.getDatasetId()).forEach(rs -> {
                    int visitRowId = rs.getInt("VisitRowId");
                    boolean isRequired = rs.getBoolean("Required");
                    visitDatasets.add(new VisitDataset(dataset.getContainer(), dataset.getDatasetId(), visitRowId, isRequired));
                });

        return visitDatasets;
    }


    public void updateVisitDatasetMapping(User user, Container container, int visitId,
                                          int datasetId, VisitDatasetType type)
    {
        VisitDataset vds = getVisitDatasetMapping(container, visitId, datasetId);
        if (vds == null)
        {
            if (type != VisitDatasetType.NOT_ASSOCIATED)
            {
                // need to insert a new VisitMap entry:
                createVisitDatasetMapping(user, container, visitId,
                        datasetId, type == VisitDatasetType.REQUIRED);
            }
        }
        else if (type == VisitDatasetType.NOT_ASSOCIATED)
        {
            // need to remove an existing VisitMap entry:
            Table.delete(SCHEMA.getTableInfoVisitMap(),
                    new Object[] { container.getId(), visitId, datasetId});
        }
        else if ((VisitDatasetType.OPTIONAL == type && vds.isRequired()) ||
                 (VisitDatasetType.REQUIRED == type && !vds.isRequired()))
        {
            Map<String,Object> required = new HashMap<>(1);
            required.put("Required", VisitDatasetType.REQUIRED == type ? Boolean.TRUE : Boolean.FALSE);
            Table.update(user, SCHEMA.getTableInfoVisitMap(), required,
                    new Object[]{container.getId(), visitId, datasetId});
        }
    }

    public long getNumDatasetRows(User user, Dataset dataset)
    {
        TableInfo sdTable = dataset.getTableInfo(user, false);
        return new TableSelector(sdTable).getRowCount();
    }


    public int purgeDataset(DatasetDefinition dataset, User user)
    {
        return purgeDataset(dataset, null, user);
    }

    /**
     * Delete all rows from a dataset or just those newer than the cutoff date.
     */
    public int purgeDataset(DatasetDefinition dataset, Date cutoff, User user)
    {
        return dataset.deleteRows(user, cutoff);
    }

    /**
     * delete a dataset definition along with associated type, data, visitmap entries
     * @param performStudyResync whether or not to kick off our normal bookkeeping. If the whole study is being deleted,
     * we don't need to bother doing this, for example.
     */
    public void deleteDataset(StudyImpl study, User user, DatasetDefinition ds, boolean performStudyResync)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        if (!ds.canDeleteDefinition(user))
            throw new IllegalStateException("Can't delete dataset: " + ds.getName());

        deleteDatasetType(study, user, ds);
        try {
            QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(study.getContainer(),
                    StudySchema.getInstance().getSchemaName(), ds.getName());
            if (def != null)
                def.delete(user);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute("DELETE FROM " + SCHEMA.getTableInfoVisitMap() + "\n" +
                "WHERE Container=? AND DatasetId=?", study.getContainer(), ds.getDatasetId());

        // UNDONE: This is broken
        // this._datasetHelper.delete(ds);
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute("DELETE FROM " + StudySchema.getInstance().getTableInfoDataset() + "\n" +
                "WHERE Container=? AND DatasetId=?", study.getContainer(), ds.getDatasetId());
        _datasetHelper.clearCache(study.getContainer());

        SecurityPolicyManager.deletePolicy(ds);

        if (safeIntegersEqual(ds.getDatasetId(), study.getParticipantCohortDatasetId()))
            CohortManager.getInstance().setManualCohortAssignment(study, user, Collections.emptyMap());

        if (performStudyResync)
        {
            // This dataset may have contained the only references to some subjects or visits; as a result, we need
            // to re-sync the participant and participant/visit tables.  (Issue 12447)
            // Don't provide the deleted dataset in the list of modified datasets- deletion doesn't count as a modification
            // within VisitManager, and passing in the empty set ensures that all subject/visit info will be recalculated.
            getVisitManager(study).updateParticipantVisits(user, Collections.emptySet());
        }

        SchemaKey schemaPath = SchemaKey.fromParts(SCHEMA.getSchemaName());
        QueryService.get().fireQueryDeleted(user, study.getContainer(), null, schemaPath, Collections.singleton(ds.getName()));
        StudyServiceImpl.addDatasetAuditEvent(
                user, study.getContainer(), ds, "Dataset deleted: " + ds.getName(),null);

        unindexDataset(ds);
    }


    /** delete a dataset type and data
     *  does not clear typeURI as we're about to delete the dataset
     */
    private void deleteDatasetType(Study study, User user, DatasetDefinition ds)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();

        if (null == ds)
            return;

        if (!ds.canDeleteDefinition(user))
            throw new IllegalStateException("Can't delete dataset: " + ds.getName());

        StorageProvisioner.drop(ds.getDomain());

        if (ds.getTypeURI() != null)
        {
            try
            {
                OntologyManager.deleteType(ds.getTypeURI(), study.getContainer());
            }
            catch (DomainNotFoundException x)
            {
                // continue
            }
        }
    }


    // Any container can be passed here (whether it contains a study or not).
    public void clearCaches(Container c, boolean unmaterializeDatasets)
    {
        Study study = getStudy(c);
        clearCachedStudies();
        _studyHelper.clearCache(c);
        _visitHelper.clearCache(c);
//        _locationHelper.clearCache(c);
        AssayManager.get().clearProtocolCache();
        if (unmaterializeDatasets && null != study)
            for (DatasetDefinition def : getDatasetDefinitions(study))
                uncache(def);
        _datasetHelper.clearCache(c);

        _qcStateCache.remove(c.getId());
        DbCache.clear(StudySchema.getInstance().getTableInfoParticipant());

        for (StudyImpl substudy : StudyManager.getInstance().getAncillaryStudies(c))
            clearCaches(substudy.getContainer(), unmaterializeDatasets);
    }

    public void deleteAllStudyData(Container c, User user)
    {
        // Cancel any reload timer
        StudyReload.cancelTimer(c);

        // No need to delete individual participants if the whole study is going away
        VisitManager.cancelParticipantPurge(c);

        // Before we delete any data, we need to go fetch the Dataset definitions.
        StudyImpl study = StudyManager.getInstance().getStudy(c);
        List<DatasetDefinition> dsds;
        if (study == null) // no study in this folder
            dsds = Collections.emptyList();
        else
            dsds = study.getDatasets();

        // get the list of study design tables
        List<TableInfo> studyDesignTables = new ArrayList<>();
        UserSchema schema = QueryService.get().getUserSchema(user, c, StudyQuerySchema.SCHEMA_NAME);
        studyDesignTables.add(schema.getTable(StudyQuerySchema.PRODUCT_TABLE_NAME));
        studyDesignTables.add(schema.getTable(StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME));
        studyDesignTables.add(schema.getTable(StudyQuerySchema.TREATMENT_TABLE_NAME));
        studyDesignTables.add(schema.getTable(StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME));
        studyDesignTables.add(schema.getTable(StudyQuerySchema.TREATMENT_VISIT_MAP_TABLE_NAME));

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        Set<TableInfo> deletedTables = new HashSet<>();
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);

        try (Transaction transaction = scope.ensureTransaction())
        {
            StudyDesignManager.get().deleteStudyDesigns(c, deletedTables);
            StudyDesignManager.get().deleteStudyDesignLookupValues(c, deletedTables);

            for (DatasetDefinition dsd : dsds)
            {
                if (dsd.getContainer().equals(dsd.getDefinitionContainer()))
                    deleteDataset(study, user, dsd, false);
                else
                    dsd.deleteAllRows(user);
            }

            //
            // samples
            //
            SpecimenManager.getInstance().deleteAllSampleData(c, deletedTables);

            //
            // assay schedule
            //
            Table.delete(SCHEMA.getTableInfoAssaySpecimenVisit(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoAssaySpecimenVisit());
            Table.delete(_assaySpecimenHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_assaySpecimenHelper.getTableInfo());

            //
            // metadata
            //
            Table.delete(SCHEMA.getTableInfoVisitMap(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoVisitMap());
            Table.delete(StudySchema.getInstance().getTableInfoUploadLog(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoUploadLog());
            Table.delete(_datasetHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_datasetHelper.getTableInfo());
//            Table.delete(_locationHelper.getTableInfo(), containerFilter);
//            assert deletedTables.add(_locationHelper.getTableInfo());
            Table.delete(_visitHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_visitHelper.getTableInfo());
            Table.delete(_studyHelper.getTableInfo(), containerFilter);
            assert deletedTables.add(_studyHelper.getTableInfo());

            // participant lists
            Table.delete(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap(), containerFilter);
            assert deletedTables.add(ParticipantGroupManager.getInstance().getTableInfoParticipantGroupMap());
            Table.delete(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup(), containerFilter);
            assert deletedTables.add(ParticipantGroupManager.getInstance().getTableInfoParticipantGroup());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantCategory(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantCategory());
            ParticipantGroupManager.getInstance().clearCache(c);

            //
            // participant and assay data (OntologyManager will take care of properties)
            //
            // Table.delete(StudySchema.getInstance().getTableInfoStudyData(null), containerFilter);
            //assert deletedTables.add(StudySchema.getInstance().getTableInfoStudyData(null));
            Table.delete(StudySchema.getInstance().getTableInfoParticipantVisit(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantVisit());
            Table.delete(StudySchema.getInstance().getTableInfoVisitAliases(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitAliases());
            Table.delete(SCHEMA.getTableInfoParticipant(), containerFilter);
            assert deletedTables.add(SCHEMA.getTableInfoParticipant());
            Table.delete(StudySchema.getInstance().getTableInfoCohort(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoCohort());
            Table.delete(StudySchema.getInstance().getTableInfoParticipantView(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoParticipantView());

            // participant group cohort union view
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable(StudyQuerySchema.PARTICIPANT_GROUP_COHORT_UNION_TABLE_NAME));

            //
            // plate service
            //
            Table.delete(StudySchema.getInstance().getSchema().getTable("Well"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("Well"));
            Table.delete(StudySchema.getInstance().getSchema().getTable("WellGroup"), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getSchema().getTable("WellGroup"));
            Table.delete(StudySchema.getInstance().getTableInfoPlate(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoPlate());

            // QC States
            Table.delete(StudySchema.getInstance().getTableInfoQCState(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoQCState());

            // Specimen comments
            Table.delete(StudySchema.getInstance().getTableInfoSpecimenComment(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoSpecimenComment());

            // study data provisioned tables
            //deleteStudyDataProvisionedTables(c, user); // NOTE: this looks to be handled by the OntologyManager
            deleteStudyDesignData(c, user, studyDesignTables);

            Table.delete(StudySchema.getInstance().getTableInfoTreatmentVisitMap(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoTreatmentVisitMap());
            Table.delete(StudySchema.getInstance().getTableInfoObjective(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoObjective());
            Table.delete(StudySchema.getInstance().getTableInfoVisitTag(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitTag());
            Table.delete(StudySchema.getInstance().getTableInfoVisitTagMap(), containerFilter);
            assert deletedTables.add(StudySchema.getInstance().getTableInfoVisitTagMap());

            // dataset tables
            for (DatasetDefinition dsd : dsds)
            {
                fireDatasetChanged(dsd);
            }

            // Clear this container ID from any source and destination columns of study snapshots. Then delete any
            // study snapshots that are orphaned (both source and destination are gone).
            SqlExecutor executor = new SqlExecutor(StudySchema.getInstance().getSchema());
            executor.execute(getStudySnapshotUpdateSql(c, "Source"));
            executor.execute(getStudySnapshotUpdateSql(c, "Destination"));

            Filter orphanedFilter = new SimpleFilter
            (
                new CompareType.CompareClause(FieldKey.fromParts("Source"), CompareType.ISBLANK, null),
                new CompareType.CompareClause(FieldKey.fromParts("Destination"), CompareType.ISBLANK, null)
            );
            Table.delete(StudySchema.getInstance().getTableInfoStudySnapshot(), orphanedFilter);

            assert deletedTables.add(StudySchema.getInstance().getTableInfoStudySnapshot());

            transaction.commit();
        }

        clearCachedStudies();
        ContainerManager.notifyContainerChange(c.getId(), ContainerManager.Property.StudyChange);

        //
        // trust and verify... but only when asserts are on
        //

        assert verifyAllTablesWereDeleted(deletedTables);
    }


    /**
     * Drops the domains for the provisioned study data tables : Product, Treatment, ProductAntigen
     * TreatmentProductMap, TreatmentVisitMap...
     * @param c
     * @param user
     */
    private void deleteStudyDataProvisionedTables(Container c, User user)
    {
        StudyProductDomainKind productDomainKind = new StudyProductDomainKind();
        String productDomainURI = productDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PRODUCT_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, productDomainURI));

        StudyProductAntigenDomainKind productAntigenDomainKind = new StudyProductAntigenDomainKind();
        String productAntigenDomainURI = productAntigenDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PRODUCT_ANTIGEN_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, productAntigenDomainURI));

        StudyTreatmentProductDomainKind studyTreatmentProductDomainKind = new StudyTreatmentProductDomainKind();
        String treatmentProductDomainURI = studyTreatmentProductDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.TREATMENT_PRODUCT_MAP_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, treatmentProductDomainURI));

        StudyTreatmentDomainKind studyTreatmentDomainKind = new StudyTreatmentDomainKind();
        String treatmentDomainURI = studyTreatmentDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.TREATMENT_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, treatmentDomainURI));

        StudyPersonnelDomainKind studyPersonnelDomainKind = new StudyPersonnelDomainKind();
        String personnelDomainURI = studyPersonnelDomainKind.generateDomainURI(StudyQuerySchema.SCHEMA_NAME, StudyQuerySchema.PERSONNEL_TABLE_NAME, c, null);
        StorageProvisioner.drop(PropertyService.get().getDomain(c, personnelDomainURI));
    }

    private void deleteStudyDesignData(Container c, User user, List<TableInfo> studyDesignTables)
    {
        for (TableInfo tinfo : studyDesignTables)
        {
            if (tinfo instanceof FilteredTable)
            {
                Table.delete(((FilteredTable)tinfo).getRealTable(), new SimpleFilter(FieldKey.fromParts("Container"), c));
            }
        }
    }

    private SQLFragment getStudySnapshotUpdateSql(Container c, String columnName)
    {
        SQLFragment sql = new SQLFragment();
        sql.append("UPDATE ");
        sql.append(StudySchema.getInstance().getTableInfoStudySnapshot().getSelectName());
        sql.append(" SET ");
        sql.append(columnName);
        sql.append(" = NULL WHERE ");
        sql.append(columnName);
        sql.append(" = ?");
        sql.add(c);

        return sql;
    }

    // TODO: Check that datasets are deleted as well?
    private boolean verifyAllTablesWereDeleted(Set<TableInfo> deletedTables)
    {
        if (1==1)
            return true;

        // Pretend like we deleted from StudyData and StudyDataTemplate tables  TODO: why aren't we deleting from these?
        Set<String> deletedTableNames = new CaseInsensitiveHashSet("studydata", "studydatatemplate");

        for (TableInfo t : deletedTables)
        {
            deletedTableNames.add(t.getName());
        }

        StringBuilder missed = new StringBuilder();

        for (String tableName : StudySchema.getInstance().getSchema().getTableNames())
        {
            if (!deletedTableNames.contains(tableName) &&
                    !"specimen".equalsIgnoreCase(tableName) && !"vial".equalsIgnoreCase(tableName) && !"specimenevent".equalsIgnoreCase(tableName) &&
                    !"site".equalsIgnoreCase(tableName) && !"specimenprimarytype".equalsIgnoreCase(tableName) &&
                    !"specimenderivative".equalsIgnoreCase(tableName) && !"specimenadditive".equalsIgnoreCase(tableName))
            {
                missed.append(" ");
                missed.append(tableName);
            }
        }

        if (missed.length() != 0)
            throw new IllegalStateException("Expected to delete from these tables:" + missed);

        return true;
    }

    public ParticipantDataset[] getParticipantDatasets(Container container, Collection<String> lsids)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts("LSID"), lsids));
        // We can't use the table layer to map results to our bean class because of the unfortunately named
        // "_VisitDate" column in study.StudyData.

        TableInfo sdti = StudySchema.getInstance().getTableInfoStudyData(StudyManager.getInstance().getStudy(container), null);
        List<ParticipantDataset> pds = new ArrayList<>();
        DatasetDefinition dataset = null;

        try (ResultSet rs = new TableSelector(sdti, filter, new Sort("DatasetId")).getResultSet())
        {
            while (rs.next())
            {
                ParticipantDataset pd = new ParticipantDataset();
                pd.setContainer(container);
                int datasetId = rs.getInt("DatasetId");
                if (dataset == null || datasetId != dataset.getDatasetId())
                    dataset = getDatasetDefinition(getStudy(container), datasetId);
                pd.setDatasetId(datasetId);
                pd.setLsid(rs.getString("LSID"));
                if (!dataset.isDemographicData())
                {
                    pd.setSequenceNum(rs.getDouble("SequenceNum"));
                    pd.setVisitDate(rs.getTimestamp("_VisitDate"));
                }
                pd.setParticipantId(rs.getString("ParticipantId"));
                pds.add(pd);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }


        return pds.toArray(new ParticipantDataset[pds.size()]);
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

        Set<SecurableResource> resources = new HashSet<>();
        resources.addAll(getDatasetDefinitions(study));

        Set<UserPrincipal> principals = new HashSet<>();

        for (RoleAssignment ra : newPolicy.getAssignments())
        {
            if (!(ra.getRole().equals(restrictedReader)))
                principals.add(SecurityManager.getPrincipal(ra.getUserId()));
        }

        SecurityPolicyManager.clearRoleAssignments(resources, principals);
    }


    /** study container only (not dataspace!) */
    public long getParticipantCount(Study study)
    {
        SQLFragment sql = new SQLFragment("SELECT COUNT(ParticipantId) FROM ");
        sql.append(SCHEMA.getTableInfoParticipant(), "p");
        sql.append(" WHERE Container = ?");
        sql.add(study.getContainer());
        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getObject(Long.class);
    }

    public String[] getParticipantIds(Study study, User user)
    {
        return getParticipantIds(study, user, -1);
    }

    /** study container only (not dataspace!) */
    public String[] getParticipantIdsForGroup(Study study, User user, int groupId)
    {
        return getParticipantIds(study, user, null, groupId, -1);
    }

    /** study container only (not dataspace!) */
    public String[] getParticipantIds(Study study, User user, int rowLimit)
    {
        return getParticipantIds(study, user, null, -1, rowLimit);
    }

    public String[] getParticipantIds(Study study, User user, ContainerFilter cf, int rowLimit)
    {
        return getParticipantIds(study, user, cf, -1, rowLimit);
    }

    /** study container only (not dataspace!) */
    private String[] getParticipantIds(Study study, User user, ContainerFilter cf, int participantGroupId, int rowLimit)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = getSQLFragmentForParticipantIds(study, user, cf, participantGroupId, rowLimit, schema, "ParticipantId");
        return new SqlSelector(schema, sql).getArray(String.class);
    }

    private static final String ALTERNATEID_COLUMN_NAME = "AlternateId";
    private static final String DATEOFFSET_COLUMN_NAME = "DateOffset";
    private static final String PTID_COLUMN_NAME = "ParticipantId";
    private static final String CONTAINER_COLUMN_NAME = "Container";
    public class ParticipantInfo
    {
        private final String _containerId;
        private final String _alternateId;
        private final int _dateOffset;

        public ParticipantInfo(String containerId, String alternateId, int dateOffset)
        {
            _containerId = containerId;
            _alternateId = alternateId;
            _dateOffset = dateOffset;
        }
        public String getContainerId()
        {
            return _containerId;
        }
        public String getAlternateId()
        {
            return _alternateId;
        }
        public int getDateOffset()
        {
            return _dateOffset;
        }
    }

    public Map<String, ParticipantInfo> getParticipantInfos(Study study, User user, final boolean isShiftDates, final boolean isAlternateIds)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = getSQLFragmentForParticipantIds(study, user, null, -1, -1, schema,
                CONTAINER_COLUMN_NAME + ", " + PTID_COLUMN_NAME + ", " + ALTERNATEID_COLUMN_NAME + ", " + DATEOFFSET_COLUMN_NAME);
        final Map<String, ParticipantInfo> alternateIdMap = new HashMap<>();

        new SqlSelector(schema, sql).forEach(rs -> {
            String containerId = rs.getString(CONTAINER_COLUMN_NAME);
            String participantId = rs.getString(PTID_COLUMN_NAME);
            String alternateId = isAlternateIds ? rs.getString(ALTERNATEID_COLUMN_NAME) : participantId;     // if !isAlternateIds, use participantId
            int dateOffset = isShiftDates ? rs.getInt(DATEOFFSET_COLUMN_NAME) : 0;                            // if !isDateShift, use 0 shift
            alternateIdMap.put(participantId, new ParticipantInfo(containerId, alternateId, dateOffset));
        });

        return alternateIdMap;
    }


    private SQLFragment getSQLFragmentForParticipantIds(Study study, User user, @Nullable ContainerFilter cf, int participantGroupId, int rowLimit, DbSchema schema, String columns)
    {
        SQLFragment filter = getParticipantFilter(study, user, cf);

        SQLFragment sql;
        if (participantGroupId == -1)
        {
            sql = new SQLFragment("SELECT " + columns + " FROM " + SCHEMA.getTableInfoParticipant()).append(" WHERE ").append(filter).append(" ORDER BY ParticipantId");
        }
        else
        {
            TableInfo table = StudySchema.getInstance().getTableInfoParticipantGroupMap();
            sql = new SQLFragment("SELECT " + columns + " FROM " + table + " WHERE ").append(filter).append(" AND GroupId = ? ORDER BY ParticipantId").add(participantGroupId);
        }
        if (rowLimit > 0)
            sql = schema.getSqlDialect().limitRows(sql, rowLimit);
        return sql;
    }


    private SQLFragment getParticipantFilter(Study study, User user, @Nullable ContainerFilter cf)
    {
        SQLFragment filter = new SQLFragment();
        if (!study.getShareDatasetDefinitions())
        {
            filter.append("Container=").append(study.getContainer());
        }
        else
        {
            if (null == user)
                throw new IllegalStateException("provide a user to query the participants table");
            if (null == cf)
                cf = new DataspaceContainerFilter(user, study);
            filter = cf.getSQLFragment(SCHEMA.getSchema(), new SQLFragment("Container"), study.getContainer());
        }
        return filter;
    }


    public String[] getParticipantIdsForCohort(Study study, int currentCohortId, int rowLimit)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? AND CurrentCohortId = ? ORDER BY ParticipantId", study.getContainer().getId(), currentCohortId);

        if (rowLimit > 0)
            sql = schema.getSqlDialect().limitRows(sql, rowLimit);

        return new SqlSelector(schema, sql).getArray(String.class);
    }

    public String[] getParticipantIdsNotInCohorts(Study study)
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM " + SCHEMA.getTableInfoParticipant() + " WHERE Container = ? AND CurrentCohortId IS NULL",
                study.getContainer().getId());

        return new SqlSelector(schema, sql).getArray(String.class);
    }

    public String[] getParticipantIdsNotInGroupCategory(Study study, User user, int categoryId)
    {
        return getParticipantIdsNotInGroupCategory(study, user, null, categoryId);
    }

    public String[] getParticipantIdsNotInGroupCategory(Study study, User user, @Nullable ContainerFilter cf, int categoryId)
    {
        TableInfo groupMapTable = StudySchema.getInstance().getTableInfoParticipantGroupMap();
        TableInfo tableInfoParticipantGroup = StudySchema.getInstance().getTableInfoParticipantGroup();
        DbSchema schema = StudySchema.getInstance().getSchema();

        SQLFragment filter = getParticipantFilter(study,user,cf);

        SQLFragment sql = new SQLFragment("SELECT ParticipantId FROM ").append(SCHEMA.getTableInfoParticipant().getFromSQL("P"))
                .append(" WHERE ").append(filter)
                .append(" AND ParticipantId NOT IN (SELECT DISTINCT ParticipantId FROM ").append(groupMapTable.getFromSQL("PGM"))
                .append(" WHERE GroupId IN (SELECT PG.RowId FROM ").append(tableInfoParticipantGroup.getFromSQL("PG")).append(" WHERE Container = ? AND CategoryId = ?))")
                .add(study.getContainer().getId())
                .add(categoryId);

        return new SqlSelector(schema.getScope(), sql).getArray(String.class);
    }

    public static final int ALTERNATEID_DEFAULT_NUM_DIGITS = 6;

    public void clearAlternateParticipantIds(Study study)
    {
        if (study.isDataspaceStudy())
            return;
        String [] participantIds = getParticipantIds(study,null);

        for (String participantId : participantIds)
            setAlternateId(study, study.getContainer().getId(), participantId, null);
    }

    public void generateNeededAlternateParticipantIds(Study study, User user)
    {
        Map<String, ParticipantInfo> participantInfos = getParticipantInfos(study, user, false, true);

        StudyController.ChangeAlternateIdsForm changeAlternateIdsForm = StudyController.getChangeAlternateIdForm((StudyImpl) study);
        String prefix = changeAlternateIdsForm.getPrefix();
        if (null == prefix)
            prefix = "";        // So we don't get the string "null" as the prefix
        int numDigits = changeAlternateIdsForm.getNumDigits();
        if (numDigits < ALTERNATEID_DEFAULT_NUM_DIGITS)
            numDigits = ALTERNATEID_DEFAULT_NUM_DIGITS;       // Should not happen, but be safe

        HashSet<Integer> usedNumbers = new HashSet<>();
        for (ParticipantInfo participantInfo : participantInfos.values())
        {
            String alternateId = participantInfo.getAlternateId();
            if (alternateId != null)
            {
                try
                {
                    if (0 == prefix.length() || alternateId.startsWith(prefix))
                    {
                        String alternateIdNoPrefix = alternateId.substring(prefix.length());
                        int alternateIdInt = Integer.valueOf(alternateIdNoPrefix);
                        usedNumbers.add(alternateIdInt);
                    }
                }
                catch (NumberFormatException x)
                {
                    // It's possible that the id is not an integer after stripping prefix, because it can be
                    // set explicitly. That's fine, because it won't conflict with what we might generate
                }
            }
        }

        Random random = new Random();
        int firstRandom = (int)Math.pow(10, (numDigits - 1));
        int maxRandom = (int)Math.pow(10, numDigits) - firstRandom;

        for (Map.Entry<String, ParticipantInfo> entry : participantInfos.entrySet())
        {
            ParticipantInfo participantInfo = entry.getValue();
            String alternateId = participantInfo.getAlternateId();

            if (null == alternateId)
            {
                String participantId = entry.getKey();
                int newId = nextRandom(random, usedNumbers, firstRandom, maxRandom);
                setAlternateId(study, participantInfo.getContainerId(), participantId, prefix + String.valueOf(newId));
            }
        }
    }

    public int setImportedAlternateParticipantIds(Study study, DataLoader dl, BatchValidationException errors) throws IOException
    {
        // Use first line to determine order of columns we care about
        // The first columcn in the data must contain the ones we are seeking
        String[][] firstline = dl.getFirstNLines(1);
        if (null == firstline || 0 == firstline.length)
            return 0;       // Unexpected but just in case

        boolean seenParticipantId = false;
        boolean seenAlternateIdOrDateOffset = false;
        boolean headerError = false;
        ColumnDescriptor[] columnDescriptors = new ColumnDescriptor[3];
        for (int i = 0; i < 3 && i < firstline[0].length; i += 1)
        {
            String header = firstline[0][i];
            switch (header)
            {
                case PTID_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(PTID_COLUMN_NAME, String.class);
                    seenParticipantId = true;
                    break;
                case ALTERNATEID_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(ALTERNATEID_COLUMN_NAME, String.class);
                    seenAlternateIdOrDateOffset = true;
                    break;
                case DATEOFFSET_COLUMN_NAME:
                    columnDescriptors[i] = new ColumnDescriptor(DATEOFFSET_COLUMN_NAME, Integer.class);
                    seenAlternateIdOrDateOffset = true;
                    break;
                default:
                    if (i < 2)
                        headerError = true;
                    break;
            }
            if (headerError)
                break;
        }

        int rowCount = 0;
        if (!seenParticipantId || !seenAlternateIdOrDateOffset || headerError)
        {
            errors.addRowError(new ValidationException("The header row must contain " + PTID_COLUMN_NAME + " and either " +
                    ALTERNATEID_COLUMN_NAME + ", " + DATEOFFSET_COLUMN_NAME + " or both."));
        }
        else
        {
            assert null != columnDescriptors[0] && null != columnDescriptors[1];        // Since we've seen PTID and 1 other
            if (null == columnDescriptors[2])
                columnDescriptors = Arrays.copyOf(columnDescriptors, 2);    // Can't hand DataLoader a null column

            // Now get loader to load all rows with correct columns and types
            dl.setColumns(columnDescriptors);
            dl.setHasColumnHeaders(true);
            dl.setThrowOnErrors(true);
            dl.setInferTypes(false);

            // Note alternateIds that are already used
            Map<String, ParticipantInfo> participantInfos = getParticipantInfos(study, null, true, true);
            CaseInsensitiveHashSet usedIds = new CaseInsensitiveHashSet();
            for (ParticipantInfo participantInfo : participantInfos.values())
            {
                String alternateId = participantInfo.getAlternateId();
                if (alternateId != null)
                {
                    usedIds.add(alternateId);
                }
            }

            List<Map<String, Object>> rows = dl.load();
            rowCount = rows.size();

            // Remove used alternateIds for participantIds that are in the list to be changed
            for (Map<String, Object> row : rows)
            {
                String participantId = Objects.toString(row.get(PTID_COLUMN_NAME), null);
                String alternateId = Objects.toString(row.get(ALTERNATEID_COLUMN_NAME), null);
                if (null != participantId && null != alternateId)
                {
                    ParticipantInfo participantInfo = participantInfos.get(participantId);
                    if (null != participantInfo)
                    {
                        String currentAlternateId = participantInfo.getAlternateId();
                        if (null != currentAlternateId && !alternateId.equalsIgnoreCase(currentAlternateId))
                            usedIds.remove(currentAlternateId);     // remove as it will get replaced
                    }
                }
            }

            try (Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                for (Map<String, Object> row : rows)
                {
                    String participantId = Objects.toString(row.get(PTID_COLUMN_NAME), null);
                    if (null == participantId)
                    {
                        // ParticipantId must be specified
                        errors.addRowError(new ValidationException("A ParticipantId must be specified."));
                        break;
                    }

                    String alternateId = Objects.toString(row.get(ALTERNATEID_COLUMN_NAME), null);
                    Integer dateOffset = (null != row.get(DATEOFFSET_COLUMN_NAME)) ? (Integer)row.get(DATEOFFSET_COLUMN_NAME) : null;

                    if (null == alternateId && null == dateOffset)
                    {
                        errors.addRowError(new ValidationException("Either " + ALTERNATEID_COLUMN_NAME + " or " + DATEOFFSET_COLUMN_NAME + " must be specified."));
                        break;
                    }

                    ParticipantInfo participantInfo = participantInfos.get(participantId);
                    if (null != participantInfo)
                    {
                        String currentAlternateId = participantInfo.getAlternateId();
                        if (null != alternateId && !alternateId.equalsIgnoreCase(currentAlternateId) && usedIds.contains(alternateId))
                        {
                            errors.addRowError(new ValidationException("Two participants may not share the same Alternate ID."));
                            break;
                        }

                        if ((null != alternateId && !alternateId.equalsIgnoreCase(currentAlternateId)) ||
                            (null != dateOffset && dateOffset != participantInfo.getDateOffset()))
                        {

                            setAlternateIdAndDateOffset(study, participantId, alternateId, dateOffset);
                            if (null != alternateId)
                                usedIds.add(alternateId);                 // Add new id
                        }
                    }
                    else
                    {
                        errors.addRowError(new ValidationException("ParticipantID " + participantId + " not found."));
                    }
                }

                if (!errors.hasErrors())
                    transaction.commit();
            }
        }

        if (errors.hasErrors())
            return 0;
        return rowCount;
    }

    private void setAlternateId(Study study, String containerId, String participantId, @Nullable String alternateId)
    {
        // Set alternateId even if null, because that's how we clear it
        SQLFragment sql = new SQLFragment(String.format(
                "UPDATE %s SET AlternateId = ? WHERE Container = ? AND ParticipantId = ?", SCHEMA.getTableInfoParticipant().getSelectName()),
                alternateId, containerId, participantId);
        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
    }

    private void setAlternateIdAndDateOffset(Study study, String participantId, @Nullable String alternateId, @Nullable Integer dateOffset)
    {
        // Only set alternateId and/or dateOffset if non-null
        assert null != participantId;
        if (null != alternateId || null != dateOffset)
        {
            SQLFragment sql = new SQLFragment("UPDATE " + SCHEMA.getTableInfoParticipant().getSelectName() + " SET ");
            boolean needComma = false;
            if (null != alternateId)
            {
                sql.append("AlternateId = ?").add(alternateId);
                needComma = true;
            }
            if (null != dateOffset)
            {
                if (needComma)
                    sql.append(", ");
                sql.append("DateOffset = ?").add(dateOffset);
            }
            sql.append(" WHERE Container = ? AND ParticipantId = ?");
            sql.add(study.getContainer());
            sql.add(participantId);
            new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        }
    }

    private int nextRandom(Random random, HashSet<Integer> usedNumbers, int firstRandom, int maxRandom)
    {
        int newId;
        do
        {
            newId = random.nextInt(maxRandom) + firstRandom;
        } while (usedNumbers.contains(newId));
        usedNumbers.add(newId);
        return newId;
    }

    private void parseData(User user,
               DatasetDefinition def,
               DataLoader loader,
               Map<String, String> columnMap)
            throws ServletException, IOException
    {
        TableInfo tinfo = def.getTableInfo(user, false);

        // We're going to lower-case the keys ourselves later,
        // so this needs to be case-insensitive
        if (!(columnMap instanceof CaseInsensitiveHashMap))
        {
            columnMap = new CaseInsensitiveHashMap<>(columnMap);
        }

        // StandardDataIteratorBuilder will handle most aliasing, HOWEVER, ...
        // columnMap may contain propertyURIs (dataset import job) and labels (GWT import file)
        Map<String,ColumnInfo> nameMap = DataIteratorUtil.createTableMap(tinfo, true);

        //
        // create columns to properties map
        //
        loader.setInferTypes(false);
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

            // let DataIterator do conversions
            col.clazz = String.class;

            if (columnMap.containsKey(name))
                name = columnMap.get(name);

            col.name = name;

            ColumnInfo colinfo = nameMap.get(col.name);
            if (null != colinfo)
            {
                col.name = colinfo.getName();
                col.propertyURI = colinfo.getPropertyURI();
            }
        }
    }


    private void batchValidateExceptionToList(BatchValidationException errors, List<String> errorStrs)
    {
        for (ValidationException rowError : errors.getRowErrors())
        {
            String rowPrefix = "";
            if (rowError.getRowNumber() >= 0)
                rowPrefix = "Row " + rowError.getRowNumber() + " ";
            for (ValidationError e : rowError.getErrors())
                errorStrs.add(rowPrefix + e.getMessage());
        }
    }

    /** @deprecated pass in a BatchValidationException, not List<String>  */
    @Deprecated
    public List<String> importDatasetData(User user, DatasetDefinition def, DataLoader loader, Map<String, String> columnMap,
                                          List<String> errors, DatasetDefinition.CheckForDuplicates checkDuplicates,
                                          QCState defaultQCState, StudyImportContext studyImportContext, Logger logger)
            throws IOException, ServletException
    {
        parseData(user, def, loader, columnMap);

        Map<Enum, Object> options = new HashMap<>();
        options.put(QueryUpdateService.ConfigParameters.Logger, logger);

        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
        context.setConfigParameters(options);

        List<String> lsids = def.importDatasetData(user, loader, context, checkDuplicates, defaultQCState, studyImportContext, logger, false);
        batchValidateExceptionToList(context.getErrors(), errors);
        return lsids;
    }

    public List<String> importDatasetData(User user, DatasetDefinition def, DataLoader loader, Map<String, String> columnMap,
                                          BatchValidationException errors, DatasetDefinition.CheckForDuplicates checkDuplicates,
                                          QCState defaultQCState, QueryUpdateService.InsertOption insertOption, StudyImportContext studyImportContext, Logger logger, boolean importLookupByAlternateKey)
            throws IOException, ServletException
    {
        parseData(user, def, loader, columnMap);
        DataIteratorContext context = new DataIteratorContext(errors);
        context.setInsertOption(insertOption);
        context.setAllowImportLookupByAlternateKey(importLookupByAlternateKey);
        if (logger != null)
        {
            Map<Enum, Object> options = new HashMap<>();
            options.put(QueryUpdateService.ConfigParameters.Logger, logger);
            context.setConfigParameters(options);
        }
        return def.importDatasetData(user, loader, context, checkDuplicates, defaultQCState, studyImportContext, logger, false);
    }

    public List<String> importDatasetData(User user, DatasetDefinition def, DataLoader loader, Map<String, String> columnMap,
                                          DataIteratorContext context,
                                          DatasetDefinition.CheckForDuplicates checkDuplicates,
                                          QCState defaultQCState, StudyImportContext studyImportContext)
            throws IOException, ServletException
    {
        parseData(user, def, loader, columnMap);
        Logger logger = null != context.getConfigParameters()
                ? (Logger)context.getConfigParameters().get(QueryUpdateService.ConfigParameters.Logger)
                : null;
        return def.importDatasetData(user, loader, context, checkDuplicates, defaultQCState, studyImportContext, logger, false);
    }


    /** @deprecated pass in a BatchValidationException, not List<String>  */
    @Deprecated
    public List<String> importDatasetData(User user, DatasetDefinition def, List<Map<String, Object>> data,
                                          List<String> errors, DatasetDefinition.CheckForDuplicates checkDuplicates,
                                          QCState defaultQCState, Logger logger, boolean forUpdate)
    {
        if (data.isEmpty())
            return Collections.emptyList();

        Map<Enum, Object> options = new HashMap<>();
        options.put(QueryUpdateService.ConfigParameters.Logger, logger);

        DataIteratorBuilder it = new ListofMapsDataIterator.Builder(data.get(0).keySet(), data);
        DataIteratorContext context = new DataIteratorContext();
        context.setInsertOption(forUpdate ? QueryUpdateService.InsertOption.INSERT : QueryUpdateService.InsertOption.IMPORT);
        context.setConfigParameters(options);

        List<String> lsids = def.importDatasetData(user, it, context, checkDuplicates, defaultQCState, null, logger, forUpdate);
        batchValidateExceptionToList(context.getErrors(), errors);
        return lsids;
    }


    public boolean importDatasetSchemas(StudyImpl study, final User user, SchemaReader reader, BindException errors, boolean createShared, @Nullable Activity activity)
    {
        if (errors.hasErrors())
            return false;

        StudyImpl createDatasetStudy = null;
        if (createShared)
            createDatasetStudy = (StudyImpl)getSharedStudy(study);
        if (null == createDatasetStudy)
            createDatasetStudy = study;

        List<String> importErrors = new LinkedList<>();
        final Map<String, DatasetDefinitionEntry> datasetDefEntryMap = new HashMap<>();

        // Use a factory to ensure domain URI consistency between imported properties and the dataset.  See #7944.
        DomainURIFactory factory = name -> {
            assert datasetDefEntryMap.containsKey(name);
            DatasetDefinitionEntry defEntry = datasetDefEntryMap.get(name);
            Container defContainer = defEntry.datasetDefinition.getDefinitionContainer();
            String domainURI = getDomainURI(defEntry.datasetDefinition.getDefinitionContainer(), user, name, defEntry.datasetDefinition.getEntityId());
            return new Pair<>(domainURI, defContainer);
        };

        // We need to build the datasets (but not save) before we create the property descriptors so that
        // we can use the unique DomainURI for each dataset as part of the PropertyURI
        populateDatasetDefEntryMap(study, createDatasetStudy, reader, user, errors, datasetDefEntryMap);
        if (errors.hasErrors())
            return false;

        ImportPropertyDescriptorsList list = reader.getImportPropertyDescriptors(factory, importErrors, study.getContainer());
        if (!importErrors.isEmpty())
        {
            for (String error : importErrors)
                errors.reject("importDatasetSchemas", error);
            return false;
        }

        // Check PHI levels; Must check activity level here, because we're in pipeline job, so Compliance can't get activity from HttpContext
        PHI maxAllowedPhi = ComplianceService.get().getMaxAllowedPhi(createDatasetStudy.getContainer(), user);
        if (null != activity && !maxAllowedPhi.isLevelAllowed(activity.getPHI()))
            maxAllowedPhi = activity.getPHI();      // Reduce allowed level

        PHI maxContainedPhi = PHI.NotPHI;
        for (ImportPropertyDescriptor ipd : list.properties)
        {
            if (maxContainedPhi.getRank() < ipd.pd.getPHI().getRank())
                maxContainedPhi = ipd.pd.getPHI();
        }

        if (!maxContainedPhi.isLevelAllowed(maxAllowedPhi))
        {
            errors.reject(ERROR_MSG, "User's max allowed PHI is '" + maxAllowedPhi.getLabel() + "', but imported datasets contain higher PHI '" + maxContainedPhi.getLabel() + "'.");
            return false;
        }

        for (ImportPropertyDescriptor ipd : list.properties)
        {
            if (null == ipd.domainName || null == ipd.domainURI)
                errors.reject("importDatasetSchemas", "Dataset not specified for property: " + ipd.pd.getName());
        }
        if (errors.hasErrors())
            return false;

        StudyManager manager = StudyManager.getInstance();

        // now actually create the datasets
        for (Map.Entry<String, DatasetDefinitionEntry> entry : datasetDefEntryMap.entrySet())
        {
            DatasetDefinitionEntry d = entry.getValue();
            DatasetDefinition def = d.datasetDefinition;

            if (d.isNew)
                manager.createDatasetDefinition(user, def);
            else if (d.isModified)
                manager.updateDatasetDefinition(user, def);

            if (d.tags != null)
                ReportPropsManager.get().importProperties(def.getEntityId(), def.getDefinitionContainer(), user, d.tags);
        }

        // now that we actually have datasets, create/update the domains
        Map<String, Domain> domainsMap = new CaseInsensitiveHashMap<>();
        Map<String, List<? extends DomainProperty>> domainsPropertiesMap = new CaseInsensitiveHashMap<>();

        buildPropertySaveAndDeleteLists(datasetDefEntryMap, list, domainsMap, domainsPropertiesMap);

        dropNotRequiredIndices(reader, datasetDefEntryMap, domainsMap);

        if (!deleteAndSaveProperties(user, errors, domainsMap, domainsPropertiesMap))
            return false;

        addMissingRequiredIndices(reader, datasetDefEntryMap, domainsMap);

        return true;
    }

    private boolean deleteAndSaveProperties(User user, BindException errors, Map<String, Domain> domainsMap, Map<String, List<? extends DomainProperty>> domainsPropertiesMap)
    {
        // see if we need to delete any columns from an existing domain
        for (Domain d : domainsMap.values())
        {
            List<? extends DomainProperty> propertiesToDel = domainsPropertiesMap.get(d.getTypeURI());
            for (DomainProperty p : propertiesToDel)
            {
                p.delete();
            }

            try
            {
                d.save(user);
            }
            catch (ChangePropertyDescriptorException ex)
            {
                errors.reject("importDatasetSchemas", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                return false;
            }
        }
        return true;
    }

    private void buildPropertySaveAndDeleteLists(Map<String, DatasetDefinitionEntry> datasetDefEntryMap, ImportPropertyDescriptorsList list, Map<String, Domain> domainsMap, Map<String, List<? extends DomainProperty>> domainsPropertiesMap)
    {
        for (ImportPropertyDescriptor ipd : list.properties)
        {
            Domain d = domainsMap.get(ipd.domainURI);
            if (null == d)
            {
                DatasetDefinitionEntry entry = datasetDefEntryMap.get(ipd.domainName);
                d = PropertyService.get().getDomain(entry.datasetDefinition.getDefinitionContainer(), ipd.domainURI);
                if (null == d)
                    d = PropertyService.get().createDomain(entry.datasetDefinition.getDefinitionContainer(), ipd.domainURI, ipd.domainName);
                domainsMap.put(d.getTypeURI(), d);
                populateDomainExistingPropertiesMap(domainsPropertiesMap, d);
            }
            // Issue 14569:  during study reimport be sure to look for a column has been deleted.
            // Look at the existing properties for this dataset's domain and
            // remove them as we find them in schema.  If there are any properties left after we've
            // iterated over all the import properties then we need to delete them
            List<? extends DomainProperty> propertiesToDel = domainsPropertiesMap.get(d.getTypeURI());
            DomainProperty p = d.getPropertyByName(ipd.pd.getName());
            propertiesToDel.remove(p);

            if (null != p)
            {
                // Enable the domain to make schema changes for this property if required
                // by dropping/adding the property and its storage at domain save time
                p.setSchemaImport(true);
                OntologyManager.updateDomainPropertyFromDescriptor(p, ipd.pd);
            }
            else
            {
                // don't add property descriptors for columns with 'global' propertyuri
                // TODO: move to conceptURI, and use 'local' propertyURI so each domain can have its own
                // propertydescriptor instance
                if (ipd.pd.getPropertyURI().startsWith("http://cpas.labkey.com/Study#"))
                    continue;
                p = d.addProperty();
                ipd.pd.copyTo(p.getPropertyDescriptor());
                p.setName(ipd.pd.getName());
                p.setRequired(ipd.pd.isRequired());  // TODO: Redundant? copyTo() already copied required (without involving nullable)
                p.setDescription(ipd.pd.getDescription());
            }

            ipd.validators.forEach(p::addValidator);
            p.setConditionalFormats(ipd.formats);
            p.setDefaultValue(ipd.defaultValue);
        }

        //Ensure that each dataset has an entry in the domain map
        if (datasetDefEntryMap.size() != domainsMap.size())
        {
            for (DatasetDefinitionEntry datasetDefinitionEntry : datasetDefEntryMap.values())
            {
                if (!domainsMap.containsKey(datasetDefinitionEntry.datasetDefinition.getTypeURI()))
                {
                    Domain domain =
                            PropertyService.get().getDomain(
                                    datasetDefinitionEntry.datasetDefinition.getDefinitionContainer(),
                                    datasetDefinitionEntry.datasetDefinition.getTypeURI());
                    if (domain != null)
                    {
                        populateDomainExistingPropertiesMap(domainsPropertiesMap, domain);
                        domainsMap.put(datasetDefinitionEntry.datasetDefinition.getTypeURI(), domain);
                    }
                }
            }
        }
    }

    private void populateDomainExistingPropertiesMap(Map<String, List<? extends DomainProperty>> domainsPropertiesMap, Domain d)
    {
        // add all the properties that exist for the domain
        List<? extends DomainProperty> existingProperties = new ArrayList<>(d.getProperties());
        domainsPropertiesMap.put(d.getTypeURI(), existingProperties);
    }

    private void addMissingRequiredIndices(SchemaReader reader, Map<String, DatasetDefinitionEntry> datasetDefEntryMap, Map<String, Domain> domainsMap)
    {
        for (SchemaReader.DatasetImportInfo datasetImportInfo : reader.getDatasetInfo().values())
        {
            DatasetDefinitionEntry datasetDefinitionEntry = datasetDefEntryMap.get(datasetImportInfo.name);
            if(datasetDefinitionEntry.datasetDefinition.isShared())
            {
                continue;
            }
            Domain domain = domainsMap.get(datasetDefinitionEntry.datasetDefinition.getTypeURI());
            domain.setPropertyIndices(datasetImportInfo.indices);
            StorageProvisioner.addMissingRequiredIndices(domain);
        }
    }

    private void dropNotRequiredIndices(SchemaReader reader, Map<String, DatasetDefinitionEntry> datasetDefEntryMap, Map<String, Domain> domainsMap)
    {
        for (SchemaReader.DatasetImportInfo datasetImportInfo : reader.getDatasetInfo().values())
        {
            DatasetDefinitionEntry datasetDefinitionEntry = datasetDefEntryMap.get(datasetImportInfo.name);
            if(datasetDefinitionEntry.datasetDefinition.isShared())
            {
                continue;
            }
            Domain domain = domainsMap.get(datasetDefinitionEntry.datasetDefinition.getTypeURI());
            domain.setPropertyIndices(datasetImportInfo.indices);
            StorageProvisioner.dropNotRequiredIndices(domain);
        }
    }


    public String getDomainURI(Container c, User u, Dataset def)
    {
        if (null == def)
            return getDomainURI(c, u, null, null);
        else
            return getDomainURI(c, u, def.getName(), def.getEntityId());
    }


    private boolean populateDatasetDefEntryMap(StudyImpl study, StudyImpl createDatasetStudy, SchemaReader reader, User user, BindException errors, Map<String, DatasetDefinitionEntry> defEntryMap)
    {
        StudyManager manager = StudyManager.getInstance();
        Container c = study.getContainer();
        Map<Integer, SchemaReader.DatasetImportInfo> datasetInfoMap = reader.getDatasetInfo();

        for (Map.Entry<Integer, SchemaReader.DatasetImportInfo> entry : datasetInfoMap.entrySet())
        {
            int id = entry.getKey().intValue();
            SchemaReader.DatasetImportInfo info = entry.getValue();
            String name = info.name;
            String label = info.label;
            if (label == null)
            {
                // Default to using the name as the label if none was explicitly specified
                label = name;
            }

            // Check for name conflicts
            Dataset existingDef = manager.getDatasetDefinitionByLabel(study, label);

            if (existingDef != null && existingDef.getDatasetId() != id)
            {
                errors.reject("importDatasetSchemas", "Dataset '" + existingDef.getName() + "' is already using the label '" + label + "'");
                return false;
            }

            existingDef = manager.getDatasetDefinitionByName(study, name);

            if (existingDef != null && existingDef.getDatasetId() != id)
            {
                errors.reject("importDatasetSchemas", "Existing " + name + " dataset has id " + existingDef.getDatasetId() +
                    ", uploaded " + name + " dataset has id " + id);
                return false;
            }

            if (info.demographicData && (info.keyPropertyName != null))
            {
                errors.reject("importDatasetSchemas", "Dataset '" + name + "' has key field set to " + info.keyPropertyName + ". This a demographic dataset therefore cannot have an extra key property.");
                return false;
            }

            DatasetDefinition def = manager.getDatasetDefinition(study, id);

            if (def == null)
            {
                def = new DatasetDefinition(createDatasetStudy, id, name, label, null, null, null);
                def.setDescription(info.description);
                def.setVisitDatePropertyName(info.visitDatePropertyName);
                def.setShowByDefault(!info.isHidden);
                def.setKeyPropertyName(info.keyPropertyName);
                def.setCategory(info.category);
                def.setKeyManagementType(info.keyManagementType);
                def.setDemographicData(info.demographicData);
                def.setType(info.type);
                def.setTag(info.tag);
                defEntryMap.put(name, new DatasetDefinitionEntry(def, true, info.tags));
                def.setUseTimeKeyField(info.useTimeKeyField);
            }
            else if (def.isAssayData())
            {
                 errors.reject("importDatasetSchemas", "Unable to modify assay dataset '" + def.getLabel() + "'.");
            }
            else
            {
                // TODO: modify shared definition?
                boolean canEditDefinition = def.canUpdateDefinition(user);

                if (canEditDefinition)
                {
                    def = def.createMutable();
                    def.setLabel(label);
                    def.setName(name);
                    def.setDescription(info.description);
                    if (null == def.getTypeURI())
                    {
                        def.setTypeURI(getDomainURI(c, user, def));
                    }

                    def.setVisitDatePropertyName(info.visitDatePropertyName);
                    def.setShowByDefault(!info.isHidden);
                    def.setKeyPropertyName(info.keyPropertyName);
                    def.setCategory(info.category);
                    def.setKeyManagementType(info.keyManagementType);
                    def.setDemographicData(info.demographicData);
                    def.setTag(info.tag);
                }
                else
                {
                    // TODO: warn
                    // name, label, description, visitdatepropertyname, category
                    if (def.getKeyManagementType() != info.keyManagementType)
                        errors.reject("ERROR_MSG", "Key type is not compatible with shared dataset: " + def.getName());
                    if (!StringUtils.equalsIgnoreCase(def.getKeyPropertyName(), info.keyPropertyName))
                        errors.reject("ERROR_MSG", "Key property name is not compatible with shared dataset: " + def.getName());
                    if (def.isDemographicData() != info.demographicData)
                        errors.reject("ERROR_MSG", "Demographic type is not compatible with shared dataset: " + def.getName());
                }

                defEntryMap.put(name, new DatasetDefinitionEntry(def, false, canEditDefinition, info.tags));
            }
        }

        return true;
    }

    // Detect if this dataset has an old-style URI without the entityid.  If so, assign a new type URI to this dataset
    // and update the domain descriptor URI
    // old:  urn:lsid:labkey.com:StudyDataset.Folder-6:DEM
    // new:  urn:lsid:labkey.com:StudyDataset.Folder-6:DEM-cbffdfa1-f19b-1030-90dd-bf4ca488b2d0
    // Also, the URI will change if the dataset name changes
    private void ensureDatasetDefinitionDomain(User user, DatasetDefinition def)
    {
        String oldURI = def.getTypeURI();
        String newURI = getDomainURI(def.getContainer(), user, def);

        if (StringUtils.equals(oldURI, newURI))
            return;

        // This dataset has the old uri so upgrade it to use the new URI format
        def.setTypeURI(newURI, true /*upgrade*/);

        // fixup the domain
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(oldURI, def.getContainer());
        if (null != dd)
        {
            dd = dd.edit()
                    .setDomainURI(newURI)
                    .setName(def.getName()) // Name may have changed too; it's part of URI
                    .build();
            OntologyManager.updateDomainDescriptor(dd);

            // since the descriptor has changed, ensure the domain is up to date
            def.refreshDomain();
        }
    }

    private static String getDomainURI(Container c, User u, String name, String id)
    {
        return DatasetDomainKind.generateDomainURI(name, id, c);
    }


    @NotNull
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

    public static SQLFragment timePortionFromDateSQL(String dateColumnName)
    {
        SqlDialect dialect = StudySchema.getInstance().getSqlDialect();
        SQLFragment sql = new SQLFragment();
        if (dialect.isPostgreSQL())
        {
            sql.append("to_char(").append(dateColumnName).append(", 'HH24MISS')");
        }
        else if (dialect.isSqlServer())
        {
            sql.append("FORMAT(").append(dateColumnName).append(", 'HHmmss')");
        }
        else
        {
            sql.append("CAST((").append(dateColumnName).append(") AS VARCHAR(10))");
        }
        return sql;
    }

    private String getParticipantCacheName(Container container)
    {
        return container.getId() + "/" + Participant.class.toString();
    }

    /** non-permission checking, non-recursive */
    private Map<String, Participant> getParticipantMap(Study study)
    {
        Map<String, Participant> participantMap = (Map<String, Participant>) DbCache.get(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()));
        if (participantMap == null)
        {
            SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
            ArrayList<Participant> participants = new TableSelector(StudySchema.getInstance().getTableInfoParticipant(),
                    filter, new Sort("ParticipantId")).getArrayList(Participant.class);
            participantMap = new LinkedHashMap<>();
            for (Participant participant : participants)
                participantMap.put(participant.getParticipantId(), participant);
            participantMap = Collections.unmodifiableMap(participantMap);
            DbCache.put(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(study.getContainer()), participantMap, CacheManager.HOUR);
        }
        return participantMap;
    }

    public void clearParticipantCache(Container container)
    {
        DbCache.remove(StudySchema.getInstance().getTableInfoParticipant(), getParticipantCacheName(container));
    }

    public Collection<Participant> getParticipants(Study study)
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        return Collections.unmodifiableCollection(participantMap.values());
    }

    public Participant getParticipant(Study study, String participantId)
    {
        Map<String, Participant> participantMap = getParticipantMap(study);
        return participantMap.get(participantId);
    }

    public static class ParticipantNotUniqueException extends Exception
    {
        ParticipantNotUniqueException(String ptid)
        {
            super("Paricipant found in more than one study: " + ptid);
        }
    }

    /* non-permission checking,  may return participant from sub folder */
    public Container findParticipant(Study study, String ptid) throws ParticipantNotUniqueException
    {
        Participant p = getParticipant(study, ptid);
        if (null != p)
            return study.getContainer();
        else if  (!study.isDataspaceStudy())
            return null;

        TableInfo table = StudySchema.getInstance().getTableInfoParticipant();
        ArrayList<String> containers = new SqlSelector(table.getSchema(), new SQLFragment("SELECT container FROM study.participant WHERE participantid=?",ptid))
                .getArrayList(String.class);
        if (containers.size() == 0)
            return null;
        else if (containers.size() == 1)
            return ContainerManager.getForId(containers.get(0));
        throw new ParticipantNotUniqueException(ptid);
    }


    public CustomParticipantView getCustomParticipantView(Study study)
    {
        if (study == null)
            return null;

        Path path = ModuleHtmlView.getStandardPath("participant");

        for (Module module : study.getContainer().getActiveModules())
        {
            if (ModuleHtmlView.exists(module, path))
            {
                return CustomParticipantView.create(ModuleHtmlView.get(module, path));
            }
        }

        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(study.getContainer());
        return new TableSelector(StudySchema.getInstance().getTableInfoParticipantView(), containerFilter, null).getObject(CustomParticipantView.class);
    }

    public CustomParticipantView saveCustomParticipantView(Study study, User user, CustomParticipantView view)
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

        Map<String, String> getAliases();
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config)
    {
        return getParticipantView(container, config, null);
    }

    public WebPartView<ParticipantViewConfig> getParticipantView(Container container, ParticipantViewConfig config, BindException errors)
    {
        StudyImpl study = getStudy(container);
        if (study.getTimepointType() == TimepointType.CONTINUOUS)
            return new BaseStudyController.StudyJspView<>(study, "participantData.jsp", config, errors);
        else
            return new BaseStudyController.StudyJspView<>(study, "participantAll.jsp", config, errors);
    }

    public WebPartView<ParticipantViewConfig> getParticipantDemographicsView(Container container, ParticipantViewConfig config, BindException errors)
    {
        return new BaseStudyController.StudyJspView<>(getStudy(container), "participantCharacteristics.jsp", config, errors);
    }

    /**
     * Called when a dataset has been modified in order to set the modified time, plus any other related actions.
     * @param fireNotification - true to fire the changed notification.
     */
    public static void datasetModified(DatasetDefinition def, User user, boolean fireNotification)
    {
        // Issue 19285 - run this as a commit task.  This has the benefit of only running per set of batch changes
        // under the same transaction and only running if the transaction is committed.  If no transaction is active then
        // the code is run immediately
        DbScope scope = StudySchema.getInstance().getScope();
        scope.addCommitTask(getInstance().getDatasetModifiedRunnable(def, user, fireNotification), CommitTaskOption.POSTCOMMIT);
    }

    public Runnable getDatasetModifiedRunnable(DatasetDefinition def, User user, boolean fireNotification)
    {
        return new DatasetModifiedRunnable(def, user, fireNotification);
    }

    private class DatasetModifiedRunnable implements Runnable
    {
        private final @NotNull User _user;
        private final @NotNull
        DatasetDefinition _def;
        private final boolean _fireNotification;

        private DatasetModifiedRunnable(@NotNull DatasetDefinition def, @NotNull User user, boolean fireNotification)
        {
            _def = def;
            _user = user;
            _fireNotification = fireNotification;
        }

        private int getDatasetId()
        {
            return _def.getDatasetId();
        }

        private Container getContainer()
        {
            return _def.getContainer();
        }

        @Override
        public void run()
        {
            DatasetDefinition.updateModified(_def, new Date());
            if (_fireNotification)
                fireDatasetChanged(_def);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            DatasetModifiedRunnable that = (DatasetModifiedRunnable) o;
            if (getDatasetId() != that.getDatasetId())
                return false;
            if (!getContainer().equals(that.getContainer()))
                return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = getContainer().hashCode();
            result = 31 * result + this.getDatasetId();
            return result;
        }
    }

    public static void fireDatasetChanged(Dataset def)
    {
        for (DatasetManager.DatasetListener l : DatasetManager.getListeners())
        {
            try
            {
                l.datasetChanged(def);
            }
            catch (Throwable t)
            {
                _log.error("fireDatasetChanged", t);
            }
        }
    }


    // Return a source->alias map for the specified participant
    public Map<String, String> getAliasMap(StudyImpl study, User user, String ptid)
    {
        @Nullable final TableInfo aliasTable = StudyQuerySchema.createSchema(study, user, true).getParticipantAliasesTable();

        if (null == aliasTable)
            return Collections.emptyMap();

        List<ColumnInfo> columns = aliasTable.getColumns();
        SimpleFilter filter = new SimpleFilter(columns.get(0).getFieldKey(), ptid);

        // Return source -> alias map
        return new TableSelector(aliasTable, Arrays.asList(columns.get(2), columns.get(1)), filter, null).getValueMap();
    }


    public void reindex(Container c)
    {
        _enumerateDocuments(null, c);
    }
    

    private void unindexDataset(DatasetDefinition ds)
    {
        String docid = "dataset:" + new Path(ds.getContainer().getId(),String.valueOf(ds.getDatasetId())).toString();
        SearchService ss = SearchService.get();
        if (null != ss)
            ss.deleteResource(docid);
    }


    public static void indexDatasets(IndexTask task, Container c, Date modifiedSince)
    {
        SearchService ss = SearchService.get();
        if (null == ss)
            return;

        SQLFragment f = new SQLFragment("SELECT Container, DatasetId FROM " + StudySchema.getInstance().getTableInfoDataset());
        if (null != c)
        {
            f.append(" WHERE Container = ?");
            f.add(c);
        }

        new SqlSelector(StudySchema.getInstance().getSchema(), f).forEach(rs ->
        {
            String container = rs.getString(1);
            int id = rs.getInt(2);

            Container c2 = ContainerManager.getForId(container);
            if (null != c2)
            {
                Study study = StudyManager.getInstance().getStudy(c2);

                if (null != study)
                {
                    DatasetDefinition dsd = StudyManager.getInstance().getDatasetDefinition(study, id);
                    if (null != dsd)
                        indexDataset(task, dsd);
                }
            }
        });
    }

    private static void indexDataset(@Nullable IndexTask task, DatasetDefinition dsd)
    {
        if (dsd.getType().equals(Dataset.TYPE_PLACEHOLDER))
            return;
        if (null == dsd.getTypeURI() || null == dsd.getDomain())
            return;
        if (null == task)
        {
            // TODO: Workaround for 30614: Search module doesn't work on TeamCity
            final SearchService ss = SearchService.get();
            if (ss == null)
                return;
            task = ss.defaultTask();
        }
        String docid = "dataset:" + new Path(dsd.getContainer().getId(), String.valueOf(dsd.getDatasetId())).toString();

        StringBuilder body = new StringBuilder();
        Map<String, Object> props = new HashMap<>();

        props.put(SearchService.PROPERTY.categories.toString(), datasetCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), StringUtils.defaultIfEmpty(dsd.getLabel(),dsd.getName()));
        String name = dsd.getName();
        String label = StringUtils.equals(dsd.getLabel(),name) ? null : dsd.getLabel();
        String description = dsd.getDescription();
        String tag = dsd.getTag();
        String keywords = StringUtilsLabKey.joinNonBlank(" ", name, label, description, tag);
        props.put(SearchService.PROPERTY.keywordsMed.toString(), keywords);

        body.append(keywords).append("\n");

        StudyQuerySchema schema = StudyQuerySchema.createSchema(dsd.getStudy(), User.getSearchUser(), false);
        TableInfo tableInfo = schema.createDatasetTableInternal(dsd);
        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, tableInfo.getDefaultVisibleColumns());
        String sep = "";
        for (ColumnInfo column : columns.values())
        {
            String n = StringUtils.trimToEmpty(column.getName());
            String l = StringUtils.trimToEmpty(column.getLabel());
            if (n.equals(l))
                l = "";
            body.append(sep).append(StringUtilsLabKey.joinNonBlank(" ", n, l));
            sep = ",\n";
        }

        ActionURL view = new ActionURL(StudyController.DatasetAction.class, null);
        view.replaceParameter("datasetId", String.valueOf(dsd.getDatasetId()));
        view.setExtraPath(dsd.getContainer().getId());

        SimpleDocumentResource r = new SimpleDocumentResource(new Path(docid), docid,
                "text/plain", body.toString(),
                view, props);
        task.addResource(r, SearchService.PRIORITY.item);
    }

    public static void indexParticipants(final IndexTask task, @NotNull final Container c, @Nullable List<String> ptids)
    {
        if (null != ptids && ptids.size() == 0)
            return;

        final int BATCH_SIZE = 500;
        if (null != ptids && ptids.size() > BATCH_SIZE)
        {
            ArrayList<String> list = new ArrayList<>(BATCH_SIZE);
            for (String ptid : ptids)
            {
                list.add(ptid);
                if (list.size() == BATCH_SIZE)
                {
                    final ArrayList<String> l = list;
                    Runnable r = () -> indexParticipants(task, c, l);
                    task.addRunnable(r, SearchService.PRIORITY.bulk);
                    list = new ArrayList<>(BATCH_SIZE);
                }
            }
            indexParticipants(task, c, list);
            return;
        }

        final StudyImpl study = StudyManager.getInstance().getStudy(c);
        if (null == study)
            return;
        final String nav = NavTree.toJS(Collections.singleton(new NavTree("study", PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(c))), null, false).toString();

        SQLFragment f = new SQLFragment();

        f.append("SELECT Container, ParticipantId FROM ");
        f.append(StudySchema.getInstance().getTableInfoParticipant(), "p");
        f.append(" WHERE Container = ?");
        f.add(c);

        if (null != ptids)
        {
            f.append(" AND ParticipantId ");
            StudySchema.getInstance().getSqlDialect().appendInClauseSql(f, ptids);
        }

        // TODO: This won't work because the Participant table lacks Created and Modified columns. See #31139.
//        SQLFragment lastIndexedFragment = new LastIndexedClause(StudySchema.getInstance().getTableInfoParticipant(), null, null).toSQLFragment(null, null);
//        if (!lastIndexedFragment.isEmpty())
//            f.append(" AND ").append(lastIndexedFragment);

        final ActionURL indexURL = new ActionURL(StudyController.IndexParticipantAction.class, c);
        indexURL.setExtraPath(c.getId());
        final ActionURL executeURL = new ActionURL(StudyController.ParticipantAction.class, c);
        executeURL.setExtraPath(c.getId());

        new SqlSelector(StudySchema.getInstance().getSchema(), f).forEach(rs -> {
            final String ptid = rs.getString(2);
            String displayTitle = "Study " + study.getLabel() + " -- " +
                    StudyService.get().getSubjectNounSingular(study.getContainer()) + " " + ptid;
            ActionURL execute = executeURL.clone().addParameter("participantId", String.valueOf(ptid));
            Path p = new Path(c.getId(), ptid);
            String docid = "participant:" + p.toString();

            String uniqueIds = ptid;

            // Add all participant aliases as high priority uniqueIds
            Map<String, String> aliasMap = StudyManager.getInstance().getAliasMap(study, User.getSearchUser(), ptid);

            if (!aliasMap.isEmpty())
                uniqueIds = uniqueIds + " " + StringUtils.join(aliasMap.values(), " ");

            Map<String, Object> props = new HashMap<>();
            props.put(SearchService.PROPERTY.categories.toString(), subjectCategory.getName());
            props.put(SearchService.PROPERTY.title.toString(), displayTitle);
            props.put(SearchService.PROPERTY.identifiersHi.toString(), uniqueIds);
            props.put(SearchService.PROPERTY.navtrail.toString(), nav);

            // Index a barebones participant document for now TODO: Figure out if it's safe to include demographic data or not (can all study users see it?)

            // SimpleDocument
            SimpleDocumentResource r = new SimpleDocumentResource(
                    p, docid,
                    c.getId(),
                    "text/plain",
                    displayTitle,
                    execute, props
            )
            {
                @Override
                public void setLastIndexed(long ms, long modified)
                {
                    StudySchema ss = StudySchema.getInstance();
                    new SqlExecutor(ss.getSchema()).execute("UPDATE " + ss.getTableInfoParticipant().getSelectName() +
                        " SET LastIndexed = ? WHERE Container = ? AND ParticipantId = ?", new Timestamp(ms), c, ptid);
                }
            };
            task.addResource(r, SearchService.PRIORITY.item);
        });
    }

    
    // make sure we don't over do it with multiple calls to reindex the same study (see reindex())
    // add a level of indirection
    // CONSIDER: add some facility like this to SearchService??
    // NOTE: this needs to be reviewed if we use modifiedSince

    final static WeakHashMap<Container,Runnable> _lastEnumerate = new WeakHashMap<>();

    public static void _enumerateDocuments(IndexTask t, final Container c)
    {
        if (null == c)
            return;

        final SearchService ss = SearchService.get();
        if (ss == null)
            return;
        final IndexTask defaultTask = ss.defaultTask();
        final IndexTask task = null==t ? defaultTask : t;

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

                Study study = StudyManager.getInstance().getStudy(c);

                if (null != study)
                {
                    StudyManager.indexDatasets(task, c, null);
                    StudyManager.indexParticipants(task, c, null);
                    // study protocol document
                    _enumerateProtocolDocuments(task, study);
                }

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


    public static void _enumerateProtocolDocuments(IndexTask task, @NotNull Study study)
    {
        AttachmentParent parent = ((StudyImpl)study).getProtocolDocumentAttachmentParent();
        if (null == parent)
            return;

        ActionURL begin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(study.getContainer());
        String nav = NavTree.toJS(Collections.singleton(new NavTree("study", begin)), null, false).toString();
        AttachmentService serv = AttachmentService.get();
        Path p = study.getContainer().getParsedPath().append("@study");

        for (Attachment att : serv.getAttachments(parent))
        {
            ActionURL download = StudyController.getProtocolDocumentDownloadURL(study.getContainer(), att.getName());

            WebdavResource r = serv.getDocumentResource
            (
                p.append(att.getName()),
                download,
                "\"" + att.getName() + "\" -- Protocol document attached to study " + study.getLabel(),
                parent, att.getName(), SearchService.fileCategory
            );
            r.getMutableProperties().put(SearchService.PROPERTY.navtrail.toString(), nav);
            task.addResource(r, SearchService.PRIORITY.item);
        }
    }


    public List<StudyImpl> getAncillaryStudies(Container sourceStudyContainer)
    {
        // in the upgrade case there  may not be any ancillary studyies
        TableInfo t = StudySchema.getInstance().getTableInfoStudy();
        ColumnInfo ssci = t.getColumn("SourceStudyContainerId");
        if (null == ssci || ssci.isUnselectable())
            return Collections.emptyList();
        return Collections.unmodifiableList(new TableSelector(StudySchema.getInstance().getTableInfoStudy(),
                new SimpleFilter(FieldKey.fromParts("SourceStudyContainerId"), sourceStudyContainer), null).getArrayList(StudyImpl.class));
    }

    // Return collection of current snapshots that are configured to refresh specimens
    public Collection<StudySnapshot> getRefreshStudySnapshots()
    {
        SQLFragment sql = new SQLFragment("SELECT ss.* FROM ");
        sql.append(StudySchema.getInstance().getTableInfoStudy(), "s");
        sql.append(" JOIN ");
        sql.append(StudySchema.getInstance().getTableInfoStudySnapshot(), "ss");
        sql.append(" ON s.StudySnapshot = ss.RowId AND Source IS NOT NULL AND Destination IS NOT NULL AND Refresh = ?");
        sql.add(Boolean.TRUE);

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql).getCollection(StudySnapshot.class);
    }

    @Nullable
    public StudySnapshot getRefreshStudySnapshot(Integer snapshotId)
    {
        TableSelector selector = new TableSelector(StudySchema.getInstance().getTableInfoStudySnapshot(), new SimpleFilter(FieldKey.fromParts("RowId"), snapshotId), null);

        return selector.getObject(StudySnapshot.class);
    }

    /**
     * Convert a placeholder or 'ghost' dataset to an actual dataset by renaming the target dataset to the placeholder's name,
     * transferring all timepoint requirements from the placeholder to the target and deleting the placeholder dataset.
     */
    public DatasetDefinition linkPlaceHolderDataset(StudyImpl study, User user, DatasetDefinition expectationDataset, DatasetDefinition targetDataset)
    {
        if (expectationDataset == null || targetDataset == null)
            throw new IllegalArgumentException("Both expectation DataSet and target DataSet must exist");

        if (!expectationDataset.getType().equals(Dataset.TYPE_PLACEHOLDER))
            throw new IllegalArgumentException("Only a DataSet of type : placeholder can be linked");

        if (!targetDataset.getType().equals(Dataset.TYPE_STANDARD))
            throw new IllegalArgumentException("Only a DataSet of type : standard can be linked to");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (Transaction transaction = scope.ensureTransaction())
        {
            // transfer any timepoint requirements from the ghost to target
            for (VisitDataset vds : expectationDataset.getVisitDatasets())
            {
                VisitDatasetType type = vds.isRequired() ? VisitDatasetType.REQUIRED : VisitDatasetType.NOT_ASSOCIATED;
                StudyManager.getInstance().updateVisitDatasetMapping(user, study.getContainer(), vds.getVisitRowId(), targetDataset.getDatasetId(), type);
            }

            String name = expectationDataset.getName();
            String label = expectationDataset.getLabel();

            // no need to resync the study, as there should be no data in the expectation dataset
            deleteDataset(study, user, expectationDataset, false);

            targetDataset = targetDataset.createMutable();
            targetDataset.setName(name);
            targetDataset.setLabel(label);
            targetDataset.save(user);

            transaction.commit();
        }

        return targetDataset;
    }
    
    public static class CategoryListener implements ViewCategoryListener
    {
        private StudyManager _instance;

        private CategoryListener(StudyManager instance)
        {
            _instance = instance;
        }

        @Override
        public void categoryDeleted(User user, ViewCategory category) throws Exception
        {
            for (DatasetDefinition def : getDatasetsForCategory(category))
            {
                def = def.createMutable();
                def.setCategoryId(0);
                def.save(user);
            }
        }

        @Override
        public void categoryCreated(User user, ViewCategory category) throws Exception {}

        @Override
        public void categoryUpdated(User user, ViewCategory category) throws Exception
        {
            Container c = ContainerManager.getForId(category.getContainerId());
            if (null != c)
                _instance._datasetHelper.clearCache(c);
        }

        private List<DatasetDefinition> getDatasetsForCategory(ViewCategory category)
        {
            if (category != null)
            {
                Study study = _instance.getStudy(ContainerManager.getForId(category.getContainerId()));
                if (study != null)
                {
                    SimpleFilter filter = SimpleFilter.createContainerFilter(study.getContainer());
                    filter.addCondition(FieldKey.fromParts("CategoryId"), category.getRowId());
                    return _instance._datasetHelper.get(study.getContainer(), filter);
                }
            }

            return Collections.emptyList();
        }
    }

    /**
     * Get the shared study in the project for the given study (excluding the shared study itself.)
     */
    @Nullable
    public Study getSharedStudy(@NotNull Container c)
    {
        if (c.isProject())
            return null;
        Container p = c.getProject();
        if (null == p)
            return null;
        Study sharedStudy = getStudy(p);
        if (null == sharedStudy)
            return null;
        if (!sharedStudy.getShareDatasetDefinitions())
            return null;
        return sharedStudy;
    }

    /**
     * Get the shared study in the project for the given study (excluding the shared study itself.)
     */
    @Nullable
    public Study getSharedStudy(@NotNull Study study)
    {
        return getSharedStudy(study.getContainer());
    }

    /**
     * Get the shared study in the project for the given study
     * or just return the current study if no shared study exists.
     */
    public @NotNull Study getSharedStudyOrCurrent(@NotNull Study study)
    {
        Study sharedStudy = getSharedStudy(study);
        return sharedStudy != null ? sharedStudy : study;
    }

    /**
     * Get the Study to use for visits -- either the
     * project shared study's container (if shared visits is turned on)
     * or the current study container.
     */
    @NotNull
    public Study getStudyForVisits(@NotNull Study study)
    {
        Study sharedStudy = getSharedStudy(study);
        if (sharedStudy != null && sharedStudy.getShareVisitDefinitions())
            return sharedStudy;

        return study;
    }

    /**
     * Get the Study to use for VisitTags -- either the
     * project shared study's container or the current study container.
     */
    @NotNull
    public Study getStudyForVisitTag(@NotNull Study study)
    {
        return getSharedStudyOrCurrent(study);
    }


    /****
     *
     *
     *
     * TESTING
     *
     *
     */



    @TestWhen(TestWhen.When.BVT)
    public static class DatasetImportTestCase extends Assert
    {
        TestContext _context = null;
        StudyManager _manager = StudyManager.getInstance();

        StudyImpl _studyDateBased = null;
        StudyImpl _studyVisitBased = null;

//        @BeforeClass
        public void createStudy()
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            {
                String name = GUID.makeHash();
                Container c = ContainerManager.createContainer(junit, name);
                StudyImpl s = new StudyImpl(c, "Junit Study");
                s.setTimepointType(TimepointType.DATE);
                s.setStartDate(new Date(DateUtil.parseDateTime(c, "2001-01-01")));
                s.setSubjectColumnName("SubjectID");
                s.setSubjectNounPlural("Subjects");
                s.setSubjectNounSingular("Subject");
                s.setSecurityType(SecurityType.BASIC_WRITE);
                s.setStartDate(new Date(DateUtil.parseDateTime(c, "1 Jan 2000")));
                _studyDateBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

                MvUtil.assignMvIndicators(c,
                        new String[] {"X", "Y", "Z"},
                        new String[] {"XXX", "YYY", "ZZZ"});
            }

            {
                String name = GUID.makeHash();
                Container c = ContainerManager.createContainer(junit, name);
                StudyImpl s = new StudyImpl(c, "Junit Study");
                s.setTimepointType(TimepointType.VISIT);
                s.setStartDate(new Date(DateUtil.parseDateTime(c, "2001-01-01")));
                s.setSubjectColumnName("SubjectID");
                s.setSubjectNounPlural("Subjects");
                s.setSubjectNounSingular("Subject");
                s.setSecurityType(SecurityType.BASIC_WRITE);
                _studyVisitBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

                MvUtil.assignMvIndicators(c,
                        new String[] {"X", "Y", "Z"},
                        new String[] {"XXX", "YYY", "ZZZ"});
            }
        }


        int counterDatasetId = 100;
        int counterRow = 0;
        protected enum DatasetType
       {
           NORMAL
           {
               public void configureDataset(DatasetDefinition dd)
               {
                   dd.setKeyPropertyName("Measure");
               }
           },
           DEMOGRAPHIC
           {
               public void configureDataset(DatasetDefinition dd)
               {
                   dd.setDemographicData(true);
               }
           },
           OPTIONAL_GUID
           {
               public void configureDataset(DatasetDefinition dd)
               {
                   dd.setKeyPropertyName("GUID");
                   dd.setKeyManagementType(Dataset.KeyManagementType.GUID);
               }
           };

           public abstract void configureDataset(DatasetDefinition dd);
       }



        Dataset createDataset(Study study, String name, boolean demographic) throws Exception
        {
            if(demographic)
                return createDataset(study, name, DatasetType.DEMOGRAPHIC);
            else
                return createDataset(study, name, DatasetType.NORMAL);
        }


        Dataset createDataset(Study study, String name, DatasetType type) throws Exception
        {
            int id = counterDatasetId++;
            _manager.createDatasetDefinition(_context.getUser(), study.getContainer(), id);
            DatasetDefinition dd = _manager.getDatasetDefinition(study, id);
            dd = dd.createMutable();

            dd.setName(name);
            dd.setLabel(name);
            dd.setCategory("Category");

            type.configureDataset(dd);

            String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), null, dd);
            dd.setTypeURI(domainURI);
            OntologyManager.ensureDomainDescriptor(domainURI, dd.getName(), study.getContainer());
            StudyManager.getInstance().updateDatasetDefinition(_context.getUser(), dd);

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

            if(type==DatasetType.OPTIONAL_GUID)
            {
                DomainProperty guid = domain.addProperty();
                guid.setName("GUID");
                guid.setPropertyURI(domain.getTypeURI()+"#"+guid.getName());
                guid.setRangeURI(PropertyType.STRING.getTypeUri());
                guid.setRequired(true);
            }

            DomainProperty value = domain.addProperty();
            value.setName("Value");
            value.setPropertyURI(domain.getTypeURI() + "#" + value.getName());
            value.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            value.setMvEnabled(true);

            // Missing values and validators don't work together, so I need another column
            DomainProperty number = domain.addProperty();
            number.setName("Number");
            number.setPropertyURI(domain.getTypeURI() + "#" + number.getName());
            number.setRangeURI(PropertyType.DOUBLE.getTypeUri());
            number.addValidator(pvLessThan100);

            if (type.equals(DatasetType.DEMOGRAPHIC))
            {
                DomainProperty startDate = domain.addProperty();
                startDate.setName("StartDate");
                startDate.setPropertyURI(domain.getTypeURI() + "#" + startDate.getName());
                startDate.setRangeURI(PropertyType.DATE_TIME.getTypeUri());
            }
            // save
            domain.save(_context.getUser());

            return study.getDataset(id);
        }


        @Test
        public void testDateConversion()
        {
            Date d = new Date();
            String iso = DateUtil.toISO(d.getTime(), true);
            DbSchema core = CoreSchema.getInstance().getSchema();
            SQLFragment select = new SQLFragment("SELECT ");
            select.append(core.getSqlDialect().getISOFormat(new SQLFragment("?",d)));
            String db = new SqlSelector(core, select).getObject(String.class);
            // SQL SERVER doesn't quite store millisecond precision
            assertEquals(23,iso.length());
            assertEquals(23,db.length());
            assertEquals(iso.substring(0,20), db.substring(0,20));
            String jdbc = (String)JdbcType.VARCHAR.convert(d);
            assertEquals(jdbc, iso);
        }


        private static final double DELTA = 1E-8;

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _testImportDatasetData(_studyDateBased);
                _testDatsetUpdateService(_studyDateBased);
                _testDaysSinceStartCalculation(_studyDateBased);
                _testImportDemographicDatasetData(_studyDateBased);
                _testImportDemographicDatasetData(_studyVisitBased);
                _testImportDatasetData(_studyVisitBased);
                _testImportDatasetDataAllowImportGuid(_studyDateBased);
                _testDatasetTransformExport(_studyDateBased);
                testDatasetSubcategory();

// TODO
//                _testDatsetUpdateService(_studyVisitBased);
            }
            catch (BatchValidationException x)
            {
                List<ValidationException> l = x.getRowErrors();
                if (null != l && l.size() > 0)
                    throw l.get(0);
                throw x;
            }
            finally
            {
                tearDown();
            }
        }


        private void _testDatsetUpdateService(StudyImpl study) throws Throwable
        {
            StudyQuerySchema ss = StudyQuerySchema.createSchema(study, _context.getUser(), false);
            Dataset def = createDataset(study, "A", false);
            TableInfo tt = ss.getTable(def.getName());
            QueryUpdateService qus = tt.getUpdateService();
            BatchValidationException errors = new BatchValidationException();
            assertNotNull(qus);

            Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
            Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
            List<Map<String, Object>> rows = new ArrayList<>();

            // insert one row
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++this.counterRow), "Value", 1.0));
            List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
            assertFalse(msg, errors.hasErrors());
            Map<String,Object> firstRowMap = ret.get(0);
            String lsidRet = (String)firstRowMap.get("lsid");
            assertNotNull(lsidRet);
            assertTrue("lsid should end with "+":101.A1.20110101.0000.Test"+counterRow + ".  Was: " + lsidRet, lsidRet.endsWith(":101.A1.20110101.0000.Test"+counterRow));

            String lsidFirstRow;

            try (ResultSet rs = new TableSelector(tt).getResultSet())
            {
                assertTrue(rs.next());
                lsidFirstRow = rs.getString("lsid");
                assertEquals(lsidFirstRow, lsidRet);
            }

            // duplicate row
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found"));

            // different participant
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "B2", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 2.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());

            // different date
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Feb1, "Measure", "Test" + (counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());

            // different measure
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());

            // duplicates in batch
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found in the database or imported data"));

            // missing participantid
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", null, "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: All dataset rows must include a value for SubjectID
            msg = errors.getRowErrors().get(0).getMessage();
            assertTrue(msg.contains("required") || msg.contains("must include"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("SubjectID"));

            // missing date
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", null, "Measure", "Test" + (++counterRow), "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Row 1 does not contain required field date.
            assertTrue(errors.getRowErrors().get(0).getMessage().toLowerCase().contains("date"));

            // missing required property field (Measure in map)
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Row 1 does not contain required field Measure.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("required"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

            // missing required property field (Measure not in map)
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Value", 1.0));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Row 1 does not contain required field Measure.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("does not contain required field"));
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

            // legal MV indicator
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());

            // illegal MV indicator
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "N/A"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Could not convert 'N/A' for field Value, should be of type Double
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("should be of type Double"));

            // conversion test
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "100"));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());


            // validation test
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 101));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            //study:Label: Value '101.0' for field 'Number' is invalid.
            assertTrue(errors.getRowErrors().get(0).getMessage().contains("is invalid"));

            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1, "Number", 99));
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());

            // QCStateLabel
            rows.clear(); errors.clear();
            rows.add(PageFlowUtil.mapInsensitive("QCStateLabel", "dirty", "SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 5));
            List<QCState> qcstates = StudyManager.getInstance().getQCStates(study.getContainer());
            assertEquals(0, qcstates.size());
            qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
            assertFalse(errors.hasErrors());
            qcstates = StudyManager.getInstance().getQCStates(study.getContainer());
            assertEquals(1, qcstates.size());
            assertEquals("dirty" , qcstates.get(0).getLabel());

            // let's try to update a row
            rows.clear(); errors.clear();
            assertTrue(firstRowMap.containsKey("Value"));
            CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
            row.putAll(firstRowMap);
            row.put("Value", 3.14159);
            // TODO why is Number==null OK on insert() but not update()?
            row.put("Number", 1.0);
            rows.add(row);
            List<Map<String, Object>> keys = new ArrayList<>();
            keys.add(PageFlowUtil.mapInsensitive("lsid", lsidFirstRow));
            ret = qus.updateRows(_context.getUser(), study.getContainer(), rows, keys, null, null);
            assert(ret.size() == 1);
        }

        private void _testImportDatasetDataAllowImportGuid(Study study) throws Throwable
        {
            int sequenceNum = 0;

            StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
            Dataset def = createDataset(study, "GU", DatasetType.OPTIONAL_GUID);
            TableInfo tt = def.getTableInfo(_context.getUser());

            Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
            List<Map<String, Object>> rows = new ArrayList<>();

            String guid = "GUUUUID";
            Map map = PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid);
            importRowVerifyGuid(def, map, tt);

            // duplicate row
            // Issue 12985
            rows.add(map);
            importRows(def, rows, "duplicate key", "All rows must have unique SubjectID/Date/GUID values.");

            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
//                assertTrue(-1 != errors.get(0).indexOf("duplicate key value violates unique constraint"));

            //same participant, guid, different sequenceNum
            importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid), tt);

            //  same GUID,sequenceNum, different different participant
            importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum, "GUID", guid), tt);

            //same subject, sequenceNum, GUID not provided
            importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt);

            //repeat:  should still work
            importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt);
        }

        private void _testImportDatasetData(Study study) throws Throwable
        {
            int sequenceNum = 0;

            StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
            Dataset def = createDataset(study, "B", false);
            TableInfo tt = def.getTableInfo(_context.getUser());

            Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
            Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
            List<Map<String, Object>> rows = new ArrayList<>();

            // insert one row
            rows.clear();
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
            importRows(def, rows);

            try (Results results = new TableSelector(tt).getResults())
            {
                assertTrue("Didn't find imported row", results.next());
                assertFalse("Found unexpected second row", results.next());
            }

            // duplicate row
            // study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
            importRows(def, rows, "Duplicates were found");

            // different participant
            Map map2 = PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++);
            importRow(def, map2);
            importRow(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++));

            // different date
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // different measure
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // duplicates in batch
            rows.clear();
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum));
            rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
            //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
            importRows(def, rows, "Duplicates were found in the database or imported data");

            // missing participantid
            importRow(def, PageFlowUtil.map("SubjectId", null, "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++), "required", "SubjectID");

            // missing date
            if(study==_studyDateBased) //irrelevant for sequential visits
                importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", null, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++), "required", "date");

            // missing required property field
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0, "SequenceNum", sequenceNum++), "required", "Measure");

            // legal MV indicator
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

            // count rows with "X"
            final MutableInt Xcount = new MutableInt(0);
            new TableSelector(tt).forEach(rs -> {
                if ("X".equals(rs.getString("ValueMVIndicator")))
                    Xcount.increment();
            });

            // legal MV indicator
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", null, "ValueMVIndicator", "X", "SequenceNum", sequenceNum++));

            // should have two rows with "X"
            final MutableInt XcountAgain = new MutableInt(0);
            new TableSelector(tt).forEach(rs -> {
                if ("X".equals(rs.getString("ValueMVIndicator")))
                    XcountAgain.increment();
            });
            assertEquals(Xcount.intValue() + 1, XcountAgain.intValue());

            // illegal MV indicator
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "N/A", "SequenceNum", sequenceNum++), "Value");

            // conversion test
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "100", "SequenceNum", sequenceNum++));

            // validation test
            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1, "Number", 101, "SequenceNum", sequenceNum++), "is invalid");

            importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1, "Number", 99, "SequenceNum", sequenceNum++));
        }

        private void importRowVerifyKey(Dataset def, Map map, TableInfo tt, String key, String... expectedErrors)  throws Exception
        {
            importRow(def, map, expectedErrors);
            String expectedKey = (String) map.get(key);

            try (ResultSet rs = new TableSelector(tt).getResultSet())
            {
                rs.last();
                assertEquals(expectedKey, rs.getString(key));
            }
        }

        private void importRowVerifyGuid(Dataset def, Map map, TableInfo tt, String... expectedErrors)  throws Exception
        {
            if(map.containsKey("GUID"))
                importRowVerifyKey(def, map, tt, "GUID", expectedErrors);
            else
            {
                importRow(def, map, expectedErrors);

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    rs.last();
                    String actualKey = rs.getString("GUID");
                    assertTrue("No GUID generated when null GUID provided", actualKey.length() > 0);
                }
            }
        }

        private void importRows(Dataset def, List<Map<String, Object>> rows, String... expectedErrors)  throws Exception
        {
            BatchValidationException errors = new BatchValidationException();

            DataLoader dl = new MapLoader(rows);
            Map<String,String> columnMap = new CaseInsensitiveHashMap<>();

            StudyManager.getInstance().importDatasetData(
                    _context.getUser(),
                    (DatasetDefinition) def, dl, columnMap,
                    errors, DatasetDefinition.CheckForDuplicates.sourceAndDestination, null, QueryUpdateService.InsertOption.IMPORT, null, null, false);

            if(expectedErrors == null)
            {
                if (errors.hasErrors())
                    throw errors;
            }
            else
            {
                for(String expectedError : expectedErrors)
                    assertTrue("Expected to find '" + expectedError + "' in error message: " + errors.getMessage(),
                            errors.getMessage().contains(expectedError));
            }
        }

        private void importRow(Dataset def, Map map, String... expectedErrors)  throws Exception
        {
            importRows(def, Arrays.asList(map), expectedErrors);
        }

        private void _testImportDemographicDatasetData(Study study) throws Throwable
        {
            StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
            Dataset def = createDataset(study, "Dem", true);
            TableInfo tt = def.getTableInfo(_context.getUser());

            Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
            List rows = new ArrayList();

            TimepointType time = study.getTimepointType();

            if (time == TimepointType.VISIT)
            {
                // insert one row w/visit
                importRow(def, PageFlowUtil.map("SubjectId", "A1", "SequenceNum", 1.0, "Measure", "Test"+(++counterRow), "Value", 1.0));

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    assertEquals(1.0, rs.getDouble("SequenceNum"), DELTA);
                }

                // insert one row w/o visit
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0));
                importRows(def, rows);

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
                }
            }
            else
            {
                // insert one row w/ date
                rows.clear();
                rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0));
                importRows(def, rows);

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    assertEquals(Feb1, new java.util.Date(rs.getTimestamp("date").getTime()));
                }

                Map map = PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0);
                importRow(def, map);

                try (ResultSet rs = new TableSelector(tt).getResultSet())
                {
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
                    assertTrue(rs.next());
                    if ("A2".equals(rs.getString("SubjectId")))
                        assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
                }
            }
        }

        /**
         * Test calculation of timepoints for date based studies. Regression test for issue : 25987
         */
        private void _testDaysSinceStartCalculation(Study study) throws Throwable
        {
            StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
            Dataset dem = createDataset(study, "Dem", true);
            Dataset ds = createDataset(study, "DS", false);

            TableInfo tableInfo = ss.getTable(ds.getName());
            QueryUpdateService qus = tableInfo.getUpdateService();

            Date Feb1 = new Date(DateUtil.parseISODateTime("2016-02-01"));
            List rows = new ArrayList();
            List<String> errors = new ArrayList<>();

            TimepointType time = study.getTimepointType();

            Map<String, Integer> validValues = new HashMap<>();
            validValues.put("A1", 0);
            validValues.put("A2", 1);

            if (time == TimepointType.DATE)
            {
                // insert rows with a startDate
                rows.clear();
                errors.clear();
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0, "StartDate", Feb1));
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A2", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0, "StartDate", Feb1));
                importRows(dem, rows);

                // insert rows with a datetime
                rows.clear();
                BatchValidationException qusErrors = new BatchValidationException();
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", "2016-02-01 13:00", "Measure", "Test"+(++counterRow), "Value", 1.0));
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A2", "Date", "2016-02-02 10:00", "Measure", "Test"+(++counterRow), "Value", 1.0));

                List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, qusErrors, null, null);
                String msg = qusErrors.getRowErrors().size() > 0 ? qusErrors.getRowErrors().get(0).toString() : "no message";
                assertFalse(msg, qusErrors.hasErrors());

                try (ResultSet rs = new TableSelector(tableInfo).getResultSet())
                {
                    while (rs.next())
                    {
                        assertEquals((int)validValues.get(rs.getString("SubjectId")), rs.getInt("Day"));
                    }
                }

                // clean up
                try (Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
                {
                    dem.delete(_context.getUser());
                    ds.delete(_context.getUser());
                    transaction.commit();
                }
            }
        }

        private void _testDatasetTransformExport(Study study) throws Throwable
        {
            ResultSet rs = null;

            try
            {
                // create a dataset
                StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
                Dataset def = createDataset(study, "DS", false);
                TableInfo datasetTI = ss.getTable(def.getName());
                QueryUpdateService qus = datasetTI.getUpdateService();
                BatchValidationException errors = new BatchValidationException();
                assertNotNull(qus);

                // insert one row
                List rows = new ArrayList();
                Date jan1 = new Date(DateUtil.parseISODateTime("2012-01-01"));
                rows.add(PageFlowUtil.mapInsensitive("SubjectId", "DS1", "Date", jan1, "Measure", "Test" + (++this.counterRow), "Value", 0.0));
                List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
                assertFalse(errors.hasErrors());
                Map<String,Object> firstRowMap = ret.get(0);

                // Ensure alternateIds are generated for all participants
                StudyManager.getInstance().generateNeededAlternateParticipantIds(study, _context.getUser());

                // query the study.participant table to verify that the dateoffset and alternateID were generated for the ptid row inserted into the dataset
                TableInfo participantTableInfo = StudySchema.getInstance().getTableInfoParticipant();
                List<ColumnInfo> cols = new ArrayList<>();
                cols.add(participantTableInfo.getColumn("participantid"));
                cols.add(participantTableInfo.getColumn("dateoffset"));
                cols.add(participantTableInfo.getColumn("alternateid"));
                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(participantTableInfo.getColumn("participantid"), "DS1");
                filter.addCondition(participantTableInfo.getColumn("container"), study.getContainer());
                rs = QueryService.get().select(participantTableInfo, cols, null, null);

                // store the ptid date offset and alternate ID for verification later
                int dateOffset = -1;
                String alternateId = null;
                while(rs.next())
                {
                    if ("DS1".equals(rs.getString("participantid")))
                    {
                        dateOffset = rs.getInt("dateoffset");
                        alternateId = rs.getString("alternateid");
                        break;
                    }
                }
                assertTrue("Date offset expected to be between 1 and 365", dateOffset > 0 && dateOffset < 366);
                assertNotNull(alternateId);
                rs.close(); rs = null;

                // test "exporting" the dataset data using the date shited values and alternate IDs
                Collection<ColumnInfo> datasetCols = new LinkedHashSet<>(datasetTI.getColumns());
                DatasetDataWriter.createDateShiftColumns(datasetTI, datasetCols, study.getContainer());
                DatasetDataWriter.createAlternateIdColumns(datasetTI, datasetCols, study.getContainer());
                rs = QueryService.get().select(datasetTI, datasetCols, null, null);

                // verify values from the transformed dataset
                assertTrue(rs.next());
                assertNotNull(rs.getString("SubjectId"));
                assertFalse("DS1".equals(rs.getString("SubjectId")));
                assertTrue(alternateId.equals(rs.getString("SubjectID")));
                assertTrue(rs.getDate("Date").before((Date)firstRowMap.get("Date")));
                // TODO: calculate the date offset and verify that it matches the firstRowMap.get("Date") value
                assertFalse(rs.next());

                rs.close(); rs = null;
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }

        // TODO
        @Test
        public void _testAutoIncrement()
        {

        }


        // TODO
        @Test
        public void _testGuid()
        {

        }

        /**
         * Regression test for 24107
         */
        public void testDatasetSubcategory() throws Exception
        {
            Container c = _studyDateBased.getContainer();
            User user = _context.getUser();
            Dataset dataset = createDataset(_studyDateBased, "DatasetWithSubcategory", true);
            int datasetId = dataset.getDatasetId();
            ViewCategoryManager mgr = ViewCategoryManager.getInstance();

            ViewCategory category = mgr.ensureViewCategory(c, user, "category");
            ViewCategory subCategory = mgr.ensureViewCategory(c, user, "category", "category");

            DatasetDefinition dd = _manager.getDatasetDefinition(_studyDateBased, dataset.getDatasetId());
            dd = dd.createMutable();

            dd.setCategoryId(subCategory.getRowId());
            dd.save(user);

            // roundtrip the definition through the domain editor
            DatasetServiceImpl datasetService = new DatasetServiceImpl(ViewContext.getMockViewContext(user, c, null, false), _studyDateBased, _manager);

            GWTDataset gwtDataset = datasetService.getDataset(datasetId);
            GWTDomain gwtDomain = datasetService.getDomainDescriptor(dd.getTypeURI());
            datasetService.updateDatasetDefinition(gwtDataset, gwtDomain, gwtDomain);

            DatasetDefinition ds = _studyDateBased.getDataset(datasetId);
            assertTrue(ds.getCategoryId() == subCategory.getRowId());

            // clean up
            try (Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
            {
                dataset.delete(_context.getUser());
                transaction.commit();
            }
        }

        public void tearDown()
        {
            if (null != _studyDateBased)
            {
                assertTrue(ContainerManager.delete(_studyDateBased.getContainer(), _context.getUser()));
            }
            if (null != _studyVisitBased)
            {
                assertTrue(ContainerManager.delete(_studyVisitBased.getContainer(), _context.getUser()));
            }
        }
    }

    public static class VisitCreationTestCase extends Assert
    {
        private static final double DELTA = 1E-8;

        @Test
        public void testExistingVisitBased()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.VISIT);

            List<VisitImpl> existingVisits = new ArrayList<>(3);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2.5, 3.0, null, Visit.Type.BASELINE));

            assertEquals("Should return existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 2.5, Visit.Type.BASELINE, existingVisits));
            assertEquals("Should return existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 3.0, Visit.Type.BASELINE, existingVisits));

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.1, Visit.Type.BASELINE, existingVisits), existingVisits, 1.1, 1.1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3.001, Visit.Type.BASELINE, existingVisits), existingVisits, 3.001, 3.001);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 4, 4);
        }

        @Test
        public void testEmptyVisitBased()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.VISIT);

            List<VisitImpl> existingVisits = new ArrayList<>();

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.1, Visit.Type.BASELINE, existingVisits), existingVisits, 1.1, 1.1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3.001, Visit.Type.BASELINE, existingVisits), existingVisits, 3.001, 3.001);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 4, 4);
        }

        @Test
        public void testEmptyDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>();

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits), existingVisits, 1, 1);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -10, Visit.Type.BASELINE, existingVisits), existingVisits, -10, -10);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);

            study.setDefaultTimepointDuration(7);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 6);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits), existingVisits, 7, 13);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits), existingVisits, 7, 13);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 15, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 20);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -10, Visit.Type.BASELINE, existingVisits), existingVisits, -10, -10);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);
        }

        @Test
        public void testExistingDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>(3);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 7, 13, null, Visit.Type.BASELINE));

            assertSame("Should be existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 13, Visit.Type.BASELINE, existingVisits));

            study.setDefaultTimepointDuration(7);
            assertSame("Should be existing visit", existingVisits.get(0), getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(1), getInstance().ensureVisitWithoutSaving(study, 2, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 7, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 10, Visit.Type.BASELINE, existingVisits));
            assertSame("Should be existing visit", existingVisits.get(2), getInstance().ensureVisitWithoutSaving(study, 13, Visit.Type.BASELINE, existingVisits));
        }

        @Test
        public void testCreationDateBased()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>(4);
            existingVisits.add(new VisitImpl(null, 1, 1, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 2, 2, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 7, 13, null, Visit.Type.BASELINE));
            existingVisits.add(new VisitImpl(null, 62, 64, null, Visit.Type.BASELINE));

            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 3);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 14);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -14, Visit.Type.BASELINE, existingVisits), existingVisits, -14, -14);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5);

            study.setDefaultTimepointDuration(7);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 5, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 20, "Week 3");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 21, Visit.Type.BASELINE, existingVisits), existingVisits, 21, 27, "Week 4");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0, "Day 0");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5, "Day 0.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5, "Day 1.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5, "Day -5");

            study.setDefaultTimepointDuration(30);
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 3, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 4, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 5, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 6, Visit.Type.BASELINE, existingVisits), existingVisits, 3, 6, "Day 3 - 6");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 14, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 21, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 29, Visit.Type.BASELINE, existingVisits), existingVisits, 14, 29, "Day 14 - 29");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 30, Visit.Type.BASELINE, existingVisits), existingVisits, 30, 59, "Month 2");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 60, Visit.Type.BASELINE, existingVisits), existingVisits, 60, 61, "Day 60 - 61");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 61, Visit.Type.BASELINE, existingVisits), existingVisits, 60, 61, "Day 60 - 61");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 65, Visit.Type.BASELINE, existingVisits), existingVisits, 65, 89, "Day 65 - 89");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 100, Visit.Type.BASELINE, existingVisits), existingVisits, 90, 119, "Month 4");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0, Visit.Type.BASELINE, existingVisits), existingVisits, 0, 0, "Day 0");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 0.5, Visit.Type.BASELINE, existingVisits), existingVisits, 0.5, 0.5, "Day 0.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, 1.5, Visit.Type.BASELINE, existingVisits), existingVisits, 1.5, 1.5, "Day 1.5");
            validateNewVisit(getInstance().ensureVisitWithoutSaving(study, -5, Visit.Type.BASELINE, existingVisits), existingVisits, -5, -5, "Day -5");
        }

        @Test
        public void testVisitDescription()
        {
            StudyImpl study = new StudyImpl();
            study.setContainer(JunitUtil.getTestContainer());
            study.setTimepointType(TimepointType.DATE);

            List<VisitImpl> existingVisits = new ArrayList<>();

            VisitImpl newVisit = getInstance().ensureVisitWithoutSaving(study, 1, Visit.Type.BASELINE, existingVisits);
            newVisit.setDescription("My custom visit description");
            validateNewVisit(newVisit, existingVisits, 1, 1, "Day 1", "My custom visit description");
        }

        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax, String label, String description)
        {
            validateNewVisit(newVisit, existingVisits, seqNumMin, seqNumMax, label);
            assertEquals("Descriptions don't match", description, newVisit.getDescription());
        }

        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax, String label)
        {
            validateNewVisit(newVisit, existingVisits, seqNumMin, seqNumMax);
            assertEquals("Labels don't match", label, newVisit.getLabel());
        }
        private void validateNewVisit(VisitImpl newVisit, List<VisitImpl> existingVisits, double seqNumMin, double seqNumMax)
        {
            for (VisitImpl existingVisit : existingVisits)
            {
                assertNotSame("Should be a new visit", newVisit, existingVisit);
            }
            assertEquals("Shouldn't have a rowId yet", 0, newVisit.getRowId());
            assertEquals("Wrong sequenceNumMin", seqNumMin, newVisit.getSequenceNumMin(), DELTA);
            assertEquals("Wrong sequenceNumMax", seqNumMax, newVisit.getSequenceNumMax(), DELTA);
        }
    }

    @TestWhen(TestWhen.When.BVT)
    public static class AssayScheduleTestCase extends Assert
    {
        TestContext _context = null;
        User _user = null;
        Container _container = null;
        StudyImpl _junitStudy = null;
        StudyManager _manager = StudyManager.getInstance();

        Map<String, String> _lookups = new HashMap<>();
        List<AssaySpecimenConfigImpl> _assays = new ArrayList<>();
        List<VisitImpl> _visits = new ArrayList<>();

        @Test
        public void test() throws Throwable
        {
            try
            {
                createStudy();
                _user = _context.getUser();
                _container = _junitStudy.getContainer();

                populateLookupTables();
                populateAssayConfigurations();
                populateAssaySchedule();

                verifyAssayConfigurations();
                verifyAssaySchedule();
                verifyCleanUpAssayConfigurations();
            }
            finally
            {
                tearDown();
            }
        }

        private void verifyCleanUpAssayConfigurations()
        {
            _manager.deleteAssaySpecimenVisits(_container, _visits.get(0).getRowId());
            verifyAssayScheduleRowCount(2);
            assertEquals(1, _manager.getAssaySpecimenVisitIds(_container, _assays.get(0)).size());
            assertEquals(1, _manager.getVisitsForAssaySchedule(_container).size());

            _manager.deleteAssaySpecimenVisits(_container, _visits.get(1).getRowId());
            verifyAssayScheduleRowCount(0);
            assertEquals(0, _manager.getAssaySpecimenVisitIds(_container, _assays.get(0)).size());
            assertEquals(0, _manager.getVisitsForAssaySchedule(_container).size());
        }

        private void verifyAssaySchedule()
        {
            verifyAssayScheduleRowCount(4);

            List<VisitImpl> visits = _manager.getVisitsForAssaySchedule(_container);
            assertEquals("Unexpected assay schedule visit count", 2, visits.size());

            for (AssaySpecimenConfigImpl assay : _manager.getAssaySpecimenConfigs(_container, "RowId"))
            {
                List<Integer> visitIds = _manager.getAssaySpecimenVisitIds(_container, assay);
                for (VisitImpl visit : _visits)
                    assertTrue("Assay schedule does not contain expected visitId", visitIds.contains(visit.getRowId()));
            }
        }

        private void verifyAssayScheduleRowCount(int expectedCount)
        {
            TableSelector selector = new TableSelector(StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), SimpleFilter.createContainerFilter(_container), null);
            assertEquals("Unexpected number of assay schedule visit records", expectedCount, selector.getRowCount());
        }

        private void verifyAssayConfigurations()
        {
            List<AssaySpecimenConfigImpl> assays = _manager.getAssaySpecimenConfigs(_container, "RowId");
            assertEquals("Unexpected assay configuration count", 2, assays.size());

            for (AssaySpecimenConfigImpl assay : assays)
            {
                assertEquals("Unexpected assay configuration lookup value", _lookups.get("Lab"), assay.getLab());
                assertEquals("Unexpected assay configuration lookup value", _lookups.get("SampleType"), assay.getSampleType());
            }
        }

        private void populateAssaySchedule()
        {
            _visits.add(StudyManager.getInstance().createVisit(_junitStudy, _user, new VisitImpl(_container, 1.0, "Visit 1", Visit.Type.BASELINE)));
            _visits.add(StudyManager.getInstance().createVisit(_junitStudy, _user, new VisitImpl(_container, 2.0, "Visit 2", Visit.Type.SCHEDULED_FOLLOWUP)));
            assertEquals(_visits.size(), 2);

            for (AssaySpecimenConfigImpl assay : _assays)
            {
                for (VisitImpl visit : _visits)
                {
                    AssaySpecimenVisitImpl asv = new AssaySpecimenVisitImpl(_container, assay.getRowId(), visit.getRowId());
                    Table.insert(_user, StudySchema.getInstance().getTableInfoAssaySpecimenVisit(), asv);
                }
            }

            verifyAssayScheduleRowCount(_assays.size() * _visits.size());
        }

        private void populateAssayConfigurations()
        {
            AssaySpecimenConfigImpl assay1 = new AssaySpecimenConfigImpl(_container, "Assay1", "Assay 1 description");
            assay1.setLab(_lookups.get("Lab"));
            assay1.setSampleType(_lookups.get("SampleType"));
            _assays.add(Table.insert(_user, StudySchema.getInstance().getTableInfoAssaySpecimen(), assay1));

            AssaySpecimenConfigImpl assay2 = new AssaySpecimenConfigImpl(_container, "Assay2", "Assay 2 description");
            assay2.setLab(_lookups.get("Lab"));
            assay2.setSampleType(_lookups.get("SampleType"));
            _assays.add(Table.insert(_user, StudySchema.getInstance().getTableInfoAssaySpecimen(), assay2));

            assertEquals(_assays.size(), 2);
        }

        private void populateLookupTables()
        {
            String name, label;

            Map<String, String> data = new HashMap<>();
            data.put("Container", _container.getId());

            data.put("Name", name = "Test Lab");
            data.put("Label", label = "Test Lab Label");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignLabs(), data);
            assertEquals("Unexpected study design lookup label", label, _manager.getStudyDesignLabLabelByName(_container, name));
            assertNull("Unexpected study design lookup label", _manager.getStudyDesignLabLabelByName(_container, "UNK"));
            _lookups.put("Lab", name);

            data.put("Name", name = "Test Sample Type");
            data.put("Label", label = "Test Sample Type Label");
            data.put("PrimaryType", "Test Primary Type");
            data.put("ShortSampleCode", "TP");
            Table.insert(_user, StudySchema.getInstance().getTableInfoStudyDesignSampleTypes(), data);
            _lookups.put("SampleType", name);
        }

        private void createStudy()
        {
            _context = TestContext.get();
            Container junit = JunitUtil.getTestContainer();

            String name = GUID.makeHash();
            Container c = ContainerManager.createContainer(junit, name);
            StudyImpl s = new StudyImpl(c, "Junit Study");
            s.setTimepointType(TimepointType.VISIT);
            s.setStartDate(new Date(DateUtil.parseDateTime(c, "2014-01-01")));
            s.setSubjectColumnName("SubjectID");
            s.setSubjectNounPlural("Subjects");
            s.setSubjectNounSingular("Subject");
            s.setSecurityType(SecurityType.BASIC_WRITE);
            _junitStudy = StudyManager.getInstance().createStudy(_context.getUser(), s);
        }

        private void tearDown()
        {
            if (null != _junitStudy)
            {
                assertTrue(ContainerManager.delete(_junitStudy.getContainer(), _context.getUser()));
            }
        }
    }
}
