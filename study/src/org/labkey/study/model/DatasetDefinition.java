/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditHandler;
import org.labkey.api.audit.AuditHandler;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.Sets;
import org.labkey.api.data.AuditConfigurable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbScope.Transaction;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.ExceptionFramework;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.Transient;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.DomainNotFoundException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.RestrictedReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.CompletionType;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ReentrantLockWithName;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.dataset.DatasetAuditProvider;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.DatasetUpdateService;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.beans.factory.InitializingBean;

import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.labkey.api.query.QueryService.AuditAction.DELETE;
import static org.labkey.api.query.QueryService.AuditAction.TRUNCATE;

public class DatasetDefinition extends AbstractStudyEntity<Integer, DatasetDefinition> implements Cloneable, Dataset, InitializingBean
{
    // DatasetQueryUpdateService


    //    static final Object MANAGED_KEY_LOCK = new Object();
    private static final Logger _log = LogManager.getLogger(DatasetDefinition.class);

    private final ReentrantLock _lock = new ReentrantLockWithName(DatasetDefinition.class, "_lock");

    private Container _definitionContainer;
    private Boolean _isShared = null;
    private StudyImpl _study;
    private int _datasetId;
    private String _name;
    private String _typeURI;
    private String _category;
    private Integer _categoryId;
    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private @NotNull KeyManagementType _keyManagementType = KeyManagementType.None;
    private String _description;
    private boolean _demographicData; //demographic information, sequenceNum
    private Integer _cohortId;
    private Integer _publishSourceId;   // the identifier of the published data source
    private String _publishSourceType;  // the type of published data source (assay, sample type, ...)

    private String _fileName; // Filename from the original import  TODO: save this at import time and load it from db
    private String _tag;
    private String _type = Dataset.TYPE_STANDARD;
    private DataSharing _datasharing = DataSharing.NONE;
    private boolean _useTimeKeyField = false;


    private static final String[] BASE_DEFAULT_FIELD_NAMES_ARRAY = new String[]
    {
        "Container",
        "ParticipantID",
        "ptid",
        "SequenceNum", // used in both date-based and visit-based studies
        "DatasetId",
        "SiteId",
        "Created",
        "CreatedBy",
        "Modified",
        "ModifiedBy",
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "dsrowid",
        "Dataset",
        "ParticipantSequenceNum",
        "_key",
        // The following columns names don't refer to actual built-in dataset columns, but
        // they're used by import ('replace') or are commonly used/confused synonyms for built-in column names
        "replace",
        "visit",
        "participant",
        DataIntegrationService.Columns.TransformImportHash.getColumnName()
    };

    private static final String[] DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY = new String[]
    {
        "Date",
        "VisitDate",
    };

    private static final String[] DEFAULT_RELATIVE_DATE_FIELD_NAMES_ARRAY = new String[]
    {
        "Day"
    };

    private static final String[] DEFAULT_VISIT_FIELD_NAMES_ARRAY = new String[]
    {
        "VisitSequenceNum"
    };

    // fields to hide on the dataset schema view
    private static final String[] HIDDEN_DEFAULT_FIELD_NAMES_ARRAY = new String[]
    {
        "Container",
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "dsrowid",
        "Dataset",
        "ParticipantSequenceNum",
        DataIntegrationService.Columns.TransformImportHash.getColumnName()
    };

    static final Set<String> DEFAULT_ABSOLUTE_DATE_FIELDS;
    static final Set<String> DEFAULT_RELATIVE_DATE_FIELDS;
    static final Set<String> DEFAULT_VISIT_FIELDS;
    private static final Set<String> HIDDEN_DEFAULT_FIELDS = Sets.newCaseInsensitiveHashSet(HIDDEN_DEFAULT_FIELD_NAMES_ARRAY);

    static
    {
        DEFAULT_ABSOLUTE_DATE_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_ABSOLUTE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY));

        DEFAULT_RELATIVE_DATE_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY));
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_RELATIVE_DATE_FIELD_NAMES_ARRAY));

        DEFAULT_VISIT_FIELDS = Sets.newCaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_VISIT_FIELDS.addAll(Sets.newCaseInsensitiveHashSet(DEFAULT_VISIT_FIELD_NAMES_ARRAY));
    }

    public DatasetDefinition()
    {
    }


    public DatasetDefinition(StudyImpl study, int datasetId)
    {
        _study = study;
        setContainer(_study.getContainer());
        _datasetId = datasetId;
        _name = String.valueOf(datasetId);
        _label =  String.valueOf(datasetId);
        _typeURI = null;
        _showByDefault = true;
        _isShared = study.getShareDatasetDefinitions();
    }


    public DatasetDefinition(StudyImpl study, int datasetId, String name, String label, String category, String entityId, @Nullable String typeURI)
    {
        _study = study;
        setContainer(_study.getContainer());
        _datasetId = datasetId;
        _name = name;
        _label = label;
        _category = category;
        _entityId = null != entityId ? entityId : GUID.makeGUID();
        _typeURI = null != typeURI ? typeURI : DatasetDomainKind.generateDomainURI(name, _entityId, getContainer());
        _showByDefault = true;
        _isShared = study.getShareDatasetDefinitions();
    }

    /*
     * given a potentially shared dataset definition, return a dataset definition that is scoped to the current study
     */
    public DatasetDefinition createLocalDatasetDefinition(StudyImpl substudy)
    {
        if (substudy.getContainer().equals(getContainer()))
            return this;
        assert isShared();
        DatasetDefinition sub = createMutable();
        assert sub != this;
        sub._definitionContainer = sub.getContainer();
        sub.setContainer(substudy.getContainer());
        sub._study = substudy;

        // apply substudy dataset overrides
        String category = "dataset-overrides:" + getDatasetId();
        PropertyManager.PropertyMap map = PropertyManager.getProperties(substudy.getContainer(), category);
        if (!map.isEmpty())
        {
            if (map.get("showByDefault") != null)
                sub.setShowByDefault(Boolean.valueOf(map.get("showByDefault")));
        }

        sub.lock();
        assert sub.isShared();
        return sub;
    }

    @Override
    public void setContainer(Container container)
    {
        super.setContainer(container);
        _study = null;
    }

    @Override
    public void savePolicy(MutableSecurityPolicy policy, User user)
    {
        String baseDescription = "Security changed.";
        String removalDescription = "Removed assignments";
        String additionDescription = "Added assignments";

        SecurityPolicy existingPolicy = SecurityPolicyManager.getPolicy(this);
        if (!existingPolicy.getAssignments().equals(policy.getAssignments()))
        {
            DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(getContainer().getId(), getPolicyChangeSummary(policy, existingPolicy, baseDescription, removalDescription, additionDescription));
            event.setDatasetId(getDatasetId());
            AuditLogService.get().addEvent(user, event);
        }
        super.savePolicy(policy, user);
    }

    @Override
    public boolean isShared()
    {
        assert null != _isShared;
        return _isShared;
    }

    public boolean isInherited()
    {
        return isShared() && null != _definitionContainer && !_definitionContainer.equals(getContainer());
    }

    /**
     * Return true if this local dataset has the same id as a dataset from a shared study.
     */
    public List<DatasetDefinition> getShadowed()
    {
        return StudyManager.getInstance().getShadowedDatasets(getStudy(), Arrays.asList(this));
    }


    //TODO this should probably be driven off the DomainKind to avoid code duplication
    public static boolean isDefaultFieldName(String fieldName, Study study)
    {
        String subjectCol = study.getSubjectColumnName();
        if (subjectCol.equalsIgnoreCase(fieldName))
            return true;

        if (DatasetDomainKind.DATE.equalsIgnoreCase(fieldName) && !study.getTimepointType().isVisitBased())
            return true;

        return switch (study.getTimepointType())
                {
                    case VISIT -> DEFAULT_VISIT_FIELDS.contains(fieldName);
                    case CONTINUOUS -> DEFAULT_ABSOLUTE_DATE_FIELDS.contains(fieldName);
                    default -> DEFAULT_RELATIVE_DATE_FIELDS.contains(fieldName);
                };
    }


    public static boolean showOnManageView(String fieldName, Study study)
    {
        return !HIDDEN_DEFAULT_FIELDS.contains(fieldName);
    }


    @Override
    public Set<String> getDefaultFieldNames()
    {
        TimepointType timepointType = getStudy().getTimepointType();
        Set<String> fieldNames =
                timepointType == TimepointType.VISIT ? DEFAULT_VISIT_FIELDS :
                timepointType == TimepointType.CONTINUOUS ? DEFAULT_ABSOLUTE_DATE_FIELDS:
                DEFAULT_RELATIVE_DATE_FIELDS;

        return Collections.unmodifiableSet(fieldNames);
    }


    @Override
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public String getFileName()
    {
        if (null == _fileName)
        {
            NumberFormat dsf = new DecimalFormat("dataset000.tsv");

            return dsf.format(getDatasetId());
        }
        else
        {
            return _fileName;
        }
    }

    public void setFileName(String fileName)
    {
        _fileName = fileName;
    }

    @Override
    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        verifyMutability();

        if (category != null)
            _category = category;
    }

    public Integer getCategoryId()
    {
        return _categoryId;
    }

    public void setCategoryId(Integer categoryId)
    {
        verifyMutability();
        _categoryId = categoryId;
        _category = null;

        if (_categoryId != null)
        {
            ViewCategory category = ViewCategoryManager.getInstance().getCategory(getDefinitionContainer(), _categoryId);
            if (category != null)
                _category = ViewCategoryManager.getInstance().encode(category);
        }
    }

    @Override
    public ViewCategory getViewCategory()
    {
        if (_categoryId != null)
            return ViewCategoryManager.getInstance().getCategory(getDefinitionContainer(), _categoryId);

        return null;
    }

    @Override
    public int getDatasetId()
    {
        return _datasetId;
    }

    public void setDatasetId(int datasetId)
    {
        verifyMutability();
        _datasetId = datasetId;
    }

    public String getDataSharing()
    {
        return getDataSharingEnum().name();
    }

    public void setDataSharing(@NotNull String datasharing)
    {
        // datasharing can == null during server upgrade
        if (null == datasharing)
        {
            _datasharing = DataSharing.NONE;
            return;
        }
        _datasharing = DataSharing.valueOf(datasharing);
        if (!getStudy().getShareVisitDefinitions() && _datasharing != DataSharing.NONE)
            throw new IllegalStateException();
    }

    @Override
    public DataSharing getDataSharingEnum()
    {
        if (!isDemographicData())
            return DataSharing.NONE;
        return _datasharing;
    }


    /**
     * We do not want to invalidate caches every time someone updates a dataset row
     * So don't store modified in this bean.
     *
     * Instead, cache modified dates separately
     */
    private static final CacheLoader<String,Date> MODIFIED_DATES_LOADER = (key, argument) -> {
        StudySchema ss = StudySchema.getInstance();
        SQLFragment sql = new SQLFragment("SELECT Modified FROM " + ss.getTableInfoDataset() + " WHERE EntityId = ?",key);

        return new SqlSelector(ss.getScope(),sql).getObject(Date.class);
    };

    private static final Cache<String, Date> MODIFIED_DATES_CACHE = DatabaseCache.get(StudySchema.getInstance().getScope(), CacheManager.UNLIMITED, CacheManager.HOUR, "Dataset modified", MODIFIED_DATES_LOADER);

    @Override
    public Date getModified()
    {
        return getModified(this);
    }

    public static Date getModified(DatasetDefinition def)
    {
        return MODIFIED_DATES_CACHE.get(def.getEntityId());
    }

    public static void updateModified(DatasetDefinition def, Date modified)
    {
        StudySchema ss = StudySchema.getInstance();
        SQLFragment sql = new SQLFragment("UPDATE " + ss.getTableInfoDataset() + " SET Modified = ? WHERE EntityId = ?", modified, def.getEntityId());
        new SqlExecutor(ss.getScope()).execute(sql);
        MODIFIED_DATES_CACHE.remove(def.getEntityId());
    }

    @Override
    public String getTag()
    {
        return _tag;
    }

    public void setTag(String tag)
    {
        _tag = tag;
    }

    @Override
    public String getTypeURI()
    {
        return _typeURI;
    }

    public void setTypeURI(String typeURI, boolean isUpgrade)
    {
        verifyMutability();
        if (StringUtils.equals(typeURI, _typeURI))
            return;
        if (null != _typeURI && !isUpgrade)
            throw new IllegalStateException("TypeURI is already set");
        _typeURI = typeURI;
    }


    public void setTypeURI(String typeURI)
    {
        setTypeURI(typeURI, false);
    }


    @Override
    public String getPropertyURI(String column)
    {
        PropertyDescriptor pd = DatasetDefinition.getStandardPropertiesMap().get(column);
        if (null != pd)
            return pd.getPropertyURI();
        return _typeURI + "." + column;
    }


    public VisitDatasetType getVisitType(int visitRowId)
    {
        VisitDataset vds = getVisitDataset(visitRowId);
        if (vds == null)
            return VisitDatasetType.NOT_ASSOCIATED;
        else if (vds.isRequired())
            return VisitDatasetType.REQUIRED;
        else
            return VisitDatasetType.OPTIONAL;
    }


    public List<VisitDataset> getVisitDatasets()
    {
        return Collections.unmodifiableList(StudyManager.getInstance().getMapping(this));
    }


    public VisitDataset getVisitDataset(int visitRowId)
    {
        List<VisitDataset> datasets = getVisitDatasets();
        for (VisitDataset vds : datasets)
        {
            if (vds.getVisitRowId() == visitRowId)
                return vds;
        }
        return null;
    }


    public int getRowId()
    {
        return getDatasetId();
    }

    @Override
    public Integer getPrimaryKey()
    {
        return getRowId();
    }


    // Note: This "table" exists only in XML (not the database). It's used to define the standard properties in every dataset.
    public static TableInfo getTemplateTableInfo()
    {
        return StudySchema.getInstance().getSchema().getTable("studydatatemplate");
    }


    /**
     * For consistency, now return the equivalent of
     *    StudyUserSchema().createSchema().getSchema("Datasets").getTable(_dataset.getLabel());
     *
     * Internal study code can still use the DatasetSchemaTableInfo methods, however, we should try hard to
     * remove usages of DatasetSchemaTableInfo.
     */
    @Override
    public TableInfo getTableInfo(User user) throws UnauthorizedException
    {
        var sqs = StudyQuerySchema.createSchema(_study, user, null);
        return sqs.getDatasetTable(this, null);
    }

    /**
     * Get table info representing dataset.  This relies on the DatasetDefinition being removed from
     * the cache if the dataset type changes.
     * see StudyManager.importDatasetTSV()
     *
     * TODO convert usages of DatasetDefinition.getTableInfo() to use StudyQuerySchema.getTable()
     */
    public DatasetSchemaTableInfo getDatasetSchemaTableInfo(User user) throws UnauthorizedException
    {
        return getDatasetSchemaTableInfo(user, true, false);
    }

    public DatasetSchemaTableInfo getDatasetSchemaTableInfo(User user, boolean checkPermission) throws UnauthorizedException
    {
        return getDatasetSchemaTableInfo(user, checkPermission, false);
    }

    public DatasetSchemaTableInfo getDatasetSchemaTableInfo(User user, boolean checkPermission, boolean multiContainer) throws UnauthorizedException
    {
        //noinspection ConstantConditions
        if (user == null && checkPermission)
            throw new IllegalArgumentException("user cannot be null");

        if (checkPermission && !canReadInternal(user))
        {
            throw new UnauthorizedException();
        }

        return new DatasetSchemaTableInfo(this, user, multiContainer);
    }


    /** why do some datasets have a typeURI, but no domain? */
    private Domain ensureDomain()
    {
        assert _lock.isHeldByCurrentThread();

        if (isInherited())
        {
            StudyImpl shared = getDefinitionStudy();
            DatasetDefinition ds = shared.getDataset(getDatasetId());
            return null == ds ? null : ds.ensureDomain();
        }

        if (null == getTypeURI())
            throw new IllegalStateException();

        Domain d = getDomain();
        if (null != d)
            return d;

        _domain = PropertyService.get().createDomain(getContainer(), getTypeURI(), getName());
        try
        {
            _domain.save(null);
        }
        catch (ChangePropertyDescriptorException x)
        {
            throw new RuntimeException(x);
        }
        return _domain;
    }


    private TableInfo loadStorageTableInfo()
    {
        if (null == getTypeURI())
            return null;

        // Use a transaction to ensure that we acquire the lock and check out the DB connection from the pool in a
        // consistent order to avoid deadlock
        try (Transaction t = StudySchema.getInstance().getSchema().getScope().ensureTransaction(_lock))
        {
            Domain d = ensureDomain();

            // create table may set storageTableName() so uncache _domain
            if (null == d.getStorageTableName())
                _domain = null;

            TableInfo ti = StorageProvisioner.createTableInfo(d);

            t.commit();
            return ti;
        }
    }


    /**
     *  just a wrapper for StorageProvisioner.create()
     */
    public void provisionTable()
    {
        try (Transaction t = StudySchema.getInstance().getSchema().getScope().ensureTransaction(_lock))
        {
            _domain = null;
            if (null == getTypeURI())
            {
                DatasetDefinition d = createMutable();
                d.setTypeURI(DatasetDomainKind.generateDomainURI(getName(), getEntityId(), getContainer()));
                d.save(null);
            }
            ensureDomain();
            loadStorageTableInfo();
            StudyManager.getInstance().uncache(this);
            t.commit();
        }
    }


    @Transient
    public TableInfo getStorageTableInfo() throws UnauthorizedException
    {
        if (isInherited())
        {
            StudyImpl shared = getDefinitionStudy();
            DatasetDefinition ds = shared.getDataset(getDatasetId());
            return null == ds ? null : ds.getStorageTableInfo();
        }
        else
        {
            return loadStorageTableInfo();
        }
    }


    /**
     * Deletes rows without auditing
     */
    public int deleteRows(@Nullable Date cutoff)
    {
        int count;

        DbSchema schema = StudySchema.getInstance().getSchema();

        try (Transaction transaction = ensureTransaction())
        {
            CPUTimer time = new CPUTimer("purge");
            time.start();

            SQLFragment studyDataFrag = new SQLFragment("DELETE FROM ").append(getStorageTableInfo());
            String and = "\nWHERE ";

            // only apply a container filter on delete when this is a shared dataset definition
            // and we are not at the container where the definition lives (see issue 28224)
            if (isShared() && !getContainer().getId().equals(getDefinitionContainer().getId()))
            {
                studyDataFrag.append(and).append("container=").appendValue(getContainer());
                and = " AND ";
            }

            if (cutoff != null)
            {
                studyDataFrag.append(and).append(" AND _VisitDate > ?").add(cutoff);
            }

            SqlExecutor executor = new SqlExecutor(schema).setExceptionFramework(ExceptionFramework.JDBC);
            count = executor.execute(studyDataFrag);
            StudyManager.datasetModified(this, true);

            transaction.commit();

            time.stop();
            _log.debug("purgeDataset " + getDisplayString() + " " + DateUtil.formatDuration(time.getTotal()/1000));
        }

        return count;
    }

    @Override
    public boolean isDemographicData()
    {
        return _demographicData;
    }

    public boolean isParticipantAliasDataset()
    {
        Integer id = _study.getParticipantAliasDatasetId();
        return null != id && id.equals(getDatasetId());
    }

    @Override
    public boolean isPublishedData()
    {
        return _publishSourceId != null;
    }

    @Override @Nullable
    public PublishSource getPublishSource()
    {
        if (_publishSourceType != null)
            return PublishSource.valueOf(_publishSourceType);
        return null;
    }

    @Override
    public @Nullable ExpObject resolvePublishSource()
    {
        PublishSource publishSource = getPublishSource();
        if (publishSource != null)
        {
            return publishSource.resolvePublishSource(getPublishSourceId());
        }
        return null;
    }

    public void setDemographicData(boolean demographicData)
    {
        verifyMutability();
        _demographicData = demographicData;
    }

    @Override
    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        verifyMutability();
        _type = type;
    }

    @Override
    public boolean getUseTimeKeyField()
    {
        return _useTimeKeyField;
    }

    @Override
    public void setUseTimeKeyField(boolean useTimeKeyField)
    {
        verifyMutability();
        _useTimeKeyField = useTimeKeyField;
    }

    @Override
    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = StudyManager.getInstance().getStudy(getContainer());
        return _study;
    }


    public StudyImpl getDefinitionStudy()
    {
        if (isInherited())
            return StudyManager.getInstance().getStudy(_definitionContainer);
        return getStudy();
    }


    public Container getDefinitionContainer()
    {
        return null!=_definitionContainer ? _definitionContainer : getContainer();
    }


    // Determines the user's permissions on this dataset based on the current dataset security rules. Primarily interested
    // in read, insert, update, and delete permissions... except for ADVANCED_WRITE which needs to return all perms.
    @Override
    public Set<Class<? extends Permission>> getPermissions(UserPrincipal user)
    {
        return getPermissions(user, null);
    }

    public Set<Class<? extends Permission>> getPermissions(UserPrincipal user, @Nullable Set<Role> contextualRoles)
    {
        Set<Class<? extends Permission>> result = new HashSet<>();

        //if the study security type is basic read or basic write, use the container's policy instead of the
        //study's policy. This will enable us to "remember" the study-level role assignments in case we want
        //to switch back to them in the future
        SecurityType securityType = getStudy().getSecurityType();
        SecurableResource securableResource = (securityType == SecurityType.BASIC_READ || securityType == SecurityType.BASIC_WRITE) ? getContainer() : getStudy();

        Set<Class<? extends Permission>> studyPermissions = SecurityManager.getPermissions(securableResource, user, contextualRoles);

        //need to check both the study's policy and the dataset's policy
        //users that have read permission on the study can read all datasets
        //users that have read-some permission on the study must also have read permission on this dataset
        copyReadPerms(studyPermissions, result);
        if (studyPermissions.contains(ReadSomePermission.class))
        {
            Set<Class<? extends Permission>> datasetPermissions = SecurityPolicyManager.getPolicy(this).getOwnPermissions(user);
            copyReadPerms(datasetPermissions, result);
        }

        if (result.contains(ReadPermission.class))
        {
            // Now check if they can write
            if (securityType == SecurityType.BASIC_WRITE)
            {
                // Basic write grants dataset edit perms (insert/update/delete) based on user's folder perms
                copyEditPerms(getStudy().getContainer(), user, result);
            }
            else if (securityType == SecurityType.ADVANCED_WRITE)
            {
                // Advanced write grants dataset edit perms (insert/update/delete) based on study or dataset policy perms
                copyEditPerms(securableResource, user, result);
                // A user can be part of multiple groups, which are set to both Edit All and Per Dataset permissions
                // so check for a custom security policy even if they have UpdatePermission on the study's policy
                if (studyPermissions.contains(ReadSomePermission.class))
                {
                    // Advanced write grants dataset permissions based on the policy stored directly on the dataset
                    // In this case, we return all permissions, important for EHR-specific per-dataset role assignments
                    result.addAll(SecurityManager.getPermissions(this, user, contextualRoles));
                }
            }
        }

        if (isEditProhibited(user, result))
            result.retainAll(READ_PERMS);
        return result;
    }


    private static final Collection<Class<? extends Permission>> READ_PERMS = List.of(ReadPermission.class, RestrictedReadPermission.class);

    private void copyReadPerms(Set<Class<? extends Permission>> granted, Set<Class<? extends Permission>> result)
    {
        READ_PERMS.stream().filter(granted::contains).forEach(result::add);
    }

    private static final Collection<Class<? extends Permission>> EDIT_PERMS = List.of(InsertPermission.class, UpdatePermission.class, DeletePermission.class);

    private void copyEditPerms(SecurableResource resource, UserPrincipal user, Set<Class<? extends Permission>> result)
    {
        Set<Class<? extends Permission>> granted = SecurityManager.getPermissions(resource, user, Set.of());
        EDIT_PERMS.stream().filter(granted::contains).forEach(result::add);
    }

    /** @deprecated use DatasetTableImpl.hasPermission()! */
    @Override
    @Deprecated
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return hasPermissions(user, Set.of(perm), null);
    }

    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @Nullable Set<Role> contextualRoles)
    {
        return hasPermissions(user, Set.of(perm), contextualRoles);
    }

    public boolean hasPermissions(@NotNull UserPrincipal user, @NotNull Set<Class<? extends Permission>> perms, @Nullable Set<Role> contextualRoles)
    {
        if (perms.isEmpty())
            throw new IllegalStateException();
        Set<Class<? extends Permission>> granted = getPermissions(user, contextualRoles);
        boolean editProhibited = isEditProhibited(user, granted);
        boolean hasAdmin = getContainer().hasPermission(user, AdminPermission.class);

        for (var perm : perms)
        {
            if (perm != ReadPermission.class && perm != RestrictedReadPermission.class && editProhibited)
                return false;
            if (!hasAdmin && !granted.contains(perm))
                return false;
        }
        return true;
    }


    private boolean isEditProhibited(UserPrincipal user, Set<Class<? extends Permission>> perms)
    {
        return getStudy().isDataspaceStudy();
    }

    @Override
    @Deprecated
    public boolean canRead(UserPrincipal user)
    {
        return hasPermission(user, ReadPermission.class, null);
    }

    public boolean canReadInternal(UserPrincipal user)
    {
        return hasPermission(user, ReadPermission.class, null);
    }


    @Override
    public boolean canDeleteDefinition(UserPrincipal user)
    {
        if (!getContainer().equals(getDefinitionContainer()))
            return false;
        return getContainer().hasPermission(user, AdminPermission.class);
    }


    @Override
    public boolean canUpdateDefinition(User user)
    {
        return getContainer().hasPermission(user, AdminPermission.class) && getDefinitionContainer().getId().equals(getContainer().getId());
    }

    @Override
    public KeyType getKeyType()
    {
        if (isDemographicData())
            return KeyType.SUBJECT;
        if (getKeyPropertyName() != null)
            return KeyType.SUBJECT_VISIT_OTHER;
        return KeyType.SUBJECT_VISIT;
    }


    /**
     * Construct a description of the key type for this dataset.
     * Participant/Visit/ExtraKey
     * @return Description of KeyType
     */
    @Override
    public String getKeyTypeDescription()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(StudyService.get().getSubjectNounSingular(getContainer()));
        if (!isDemographicData())
        {
            sb.append(getStudy().getTimepointType().isVisitBased() ? "/Visit" : "/Date");

            if (getKeyPropertyName() != null)
                sb.append("/").append(getKeyPropertyName());
            else if (getUseTimeKeyField())
                sb.append("/Time");
        }
        else if (getKeyPropertyName() != null)
        {
            sb.append("/").append(getKeyPropertyName());
        }
        return sb.toString();
    }

    @Override
    public boolean hasMatchingExtraKey(Dataset other)
    {
        if (other == null)
            return false;

        if (isPublishedData() || other.isPublishedData() || getKeyPropertyName() == null || other.getKeyPropertyName() == null)
            return false;

        Domain thisDomain = getDomain();
        Domain otherDomain = other.getDomain();
        if (null == thisDomain || null == otherDomain)
            return false;
        
        DomainProperty thisKeyDP = thisDomain.getPropertyByName(getKeyPropertyName());
        DomainProperty otherKeyDP = otherDomain.getPropertyByName(other.getKeyPropertyName());
        if (thisKeyDP == null || otherKeyDP == null)
            return false;

        // Key property types must match
        PropertyType thisKeyType = thisKeyDP.getPropertyDescriptor().getPropertyType();
        PropertyType otherKeyType = otherKeyDP.getPropertyDescriptor().getPropertyType();
        if (!LOOKUP_KEY_TYPES.contains(thisKeyType) || thisKeyType != otherKeyType)
            return false;

        // Either the lookups must match or the Key property name must match.
        Lookup thisKeyLookup = thisKeyDP.getLookup();
        Lookup otherKeyLookup = otherKeyDP.getLookup();
        if (thisKeyLookup != null && otherKeyLookup != null)
        {
            if (!thisKeyLookup.equals(otherKeyLookup))
                return false;
        }
        else
        {
            if (thisKeyLookup != null || otherKeyLookup != null)
                return false;

            if (!getKeyPropertyName().equalsIgnoreCase(other.getKeyPropertyName()))
                return false;
        }

        // NOTE: Also consider comparing ConceptURI of the properties

        return true;
    }

    @Override
    public void delete(User user)
    {
        if (!canDeleteDefinition(user))
        {
            throw new UnauthorizedException("No permission to delete dataset " + getName() + " for study in " + getContainer().getPath());
        }
        StudyManager.getInstance().deleteDataset(getStudy(), user, this, true);
    }


    @Override
    public void deleteAllRows(User user)
    {
        TableInfo data = getStorageTableInfo();
        if (null == data)
            return;
        DbScope scope =  StudySchema.getInstance().getSchema().getScope();
        try (Transaction transaction = scope.ensureTransaction())
        {
            Table.delete(data, new SimpleFilter().addWhereClause("Container=?", new Object[] {getContainer()}));
            StudyManager.datasetModified(this, true);
            transaction.commit();
        }
    }


    // The set of allowed extra key lookup types that we can join across.
    private static final EnumSet<PropertyType> LOOKUP_KEY_TYPES = EnumSet.of(
            PropertyType.DATE_TIME,
            // Disallow DOUBLE extra key for DatasetAutoJoin.  See Issue 14860.
            //PropertyType.DOUBLE,
            PropertyType.STRING,
            PropertyType.INTEGER);

    /** most external users should use this */
    public String getVisitDateColumnName()
    {
        if (null == _visitDatePropertyName && !getStudy().getTimepointType().isVisitBased())
            _visitDatePropertyName = "Date"; //Todo: Allow alternate names
        return _visitDatePropertyName;
    }

    @Override
    public String getVisitDatePropertyName()
    {
        return _visitDatePropertyName;
    }


    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        _visitDatePropertyName = visitDatePropertyName;
    }

    @Override
    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    @Override
    public void setKeyPropertyName(String keyPropertyName)
    {
        verifyMutability();
        _keyPropertyName = keyPropertyName;
    }


    @Override
    public void save(User user)
    {
        // caller should have checked canUpdate() by now, so we throw if not legal
        // NOTE: don't check AdminPermission here.  It breaks assay publish-to-study
//        if (!canUpdateDefinition(user))
        if (!getDefinitionContainer().getId().equals(getContainer().getId()))
            throw new IllegalStateException("Can't save dataset in this folder...");
        StudyManager.getInstance().updateDatasetDefinition(user, this);
        MODIFIED_DATES_CACHE.remove(getEntityId());
    }


    @Override
    public void setKeyManagementType(@NotNull KeyManagementType type)
    {
        _keyManagementType = type;
    }

    @Override
    @NotNull
    public KeyManagementType getKeyManagementType()
    {
        return _keyManagementType;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return null == _description ? "The study dataset " + getName() : _description;
    }

    /** @return the lock object used to synchronize domain loading */
    public Lock getDomainLoadingLock()
    {
        return _lock;
    }

    private static class AutoCompleteDisplayColumnFactory implements DisplayColumnFactory
    {
        private final ActionURL _completionBase;

        public AutoCompleteDisplayColumnFactory(Container studyContainer, CompletionType type)
        {
            _completionBase = PageFlowUtil.urlProvider(StudyUrls.class).getCompletionURL(studyContainer, type);
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                protected ActionURL getAutoCompleteURLPrefix()
                {
                    return _completionBase;
                }
            };
        }
    }


//    private void _hideColumn(ColumnInfo col)
//    {
//        col.setHidden(true);
//        col.setShownInInsertView(false);
//        col.setShownInUpdateView(false);
//    }

    private void _showColumn(BaseColumnInfo col)
    {
        col.setHidden(false);
        col.setShownInInsertView(true);
        col.setShownInUpdateView(true);
    }


    /**
     * NOTE the constructor takes a USER in order that some lookup columns can be properly
     * verified/constructed
     *
     * CONSIDER: we could use a way to delay permission checking and final schema construction for lookups
     * so that this object can be cached...
     */

    public class DatasetSchemaTableInfo extends SchemaTableInfo
    {
        @Override
        public String getSelectName()
        {
            return null;
        }

        @Override
        public @Nullable SQLFragment getSQLName()
        {
            return null;
        }

        private final Container _container;
        private final DatasetDefinition _def;
        boolean _multiContainer;
        BaseColumnInfo _ptid;

        TableInfo _storage;
        TableInfo _template;


        private ColumnInfo getStorageColumn(String name)
        {
            if (null != _storage)
                return _storage.getColumn(name);
            else
                return _template.getColumn(name);
        }


        private ColumnInfo getStorageColumn(Domain d, DomainProperty p)
        {
            return _storage.getColumn(p.getName());
        }


        DatasetSchemaTableInfo(DatasetDefinition def, final User user, boolean multiContainer)
        {
            super(StudySchema.getInstance().getSchema(), DatabaseTableType.TABLE, def.getName());
            setTitle(def.getLabel());
            _def = def;
            _autoLoadMetaData = false;
            _container = def.getContainer();
            _multiContainer = multiContainer;     /* true: don't preapply the container filter, let wrapper tableinfo handle it */
            Study study = StudyManager.getInstance().getStudy(_container);

            _storage = def.getStorageTableInfo();
            _template = getTemplateTableInfo();

            // ParticipantId

            {
                // StudyData columns
                // NOTE (MAB): I think it was probably wrong to alias participantid to subjectname here
                // That probably should have been done only in the StudyQuerySchema
                // CONSIDER: remove this aliased column
                var ptidCol = getStorageColumn("ParticipantId");
                if (null == ptidCol) // shouldn't happen! bug mothership says it did
                    throw new NullPointerException("ParticipantId column not found in dataset: " + (null != _container ? "(" + _container.getPath() + ") " : "") + getName());
                var wrapped = newDatasetColumnInfo(this, ptidCol, getParticipantIdURI());
                wrapped.setName("ParticipantId");
                String subject = StudyService.get().getSubjectColumnName(_container);

                if ("ParticipantId".equalsIgnoreCase(subject))
                    _ptid = wrapped;
                else
                    _ptid = new AliasedColumn(this, subject, wrapped);

                _ptid.setNullable(false);
                addColumn(_ptid);
            }

            // base columns

            for (String name : Arrays.asList("Container", "lsid", "ParticipantSequenceNum", "sourcelsid", "Created", "CreatedBy", "Modified", "ModifiedBy", "dsrowid", DataIntegrationService.Columns.TransformImportHash.getColumnName()))
            {
                var col = getStorageColumn(name);
                if (null == col) continue;
                var wrapped = newDatasetColumnInfo(this, col, uriForName(col.getName()));
                wrapped.setName(name);
                wrapped.setUserEditable(false);
                addColumn(wrapped);
            }

            // _Key

            if (def.getKeyPropertyName() != null)
            {
                var keyCol = newDatasetColumnInfo(this, getStorageColumn("_Key"), getKeyURI());
                keyCol.setUserEditable(false);
                addColumn(keyCol);
            }

            // SequenceNum

            var sequenceNumCol = newDatasetColumnInfo(this, getStorageColumn("SequenceNum"), getSequenceNumURI());
            sequenceNumCol.setName("SequenceNum");
            sequenceNumCol.setDisplayColumnFactory(new AutoCompleteDisplayColumnFactory(_container, CompletionType.VisitId));
            sequenceNumCol.setMeasure(false);

            if (def.isDemographicData())
            {
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            if (!study.getTimepointType().isVisitBased())
            {
                sequenceNumCol.setNullable(true);
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            addColumn(sequenceNumCol);

            // Date

            var column = getStorageColumn("Date");
            var visitDateCol = newDatasetColumnInfo(this, column, getVisitDateURI());
            if (!study.getTimepointType().isVisitBased() && !def.isDemographicData())
                visitDateCol.setNullable(false);

            if (!study.getTimepointType().isVisitBased() && def.getUseTimeKeyField())
                visitDateCol.setFormat("DateTime");
            else
                visitDateCol.setFormat("Date");  // #26844: Date vs. date time type support

            addColumn(visitDateCol);

            // QCState

            var qcStateCol = newDatasetColumnInfo(this, getStorageColumn(DatasetTableImpl.QCSTATE_ID_COLNAME), getQCStateURI());
            // UNDONE: make the QC column user editable.  This is turned off for now because DatasetSchemaTableInfo is not
            // a FilteredTable, so it doesn't know how to restrict QC options to those in the current container.
            // Note that QC state can still be modified via the standard update UI.
            qcStateCol.setUserEditable(false);
            addColumn(qcStateCol);

            // Property columns (see OntologyManager.getColumnsForType())

            Domain d = def.getDomain();
            List<? extends DomainProperty> properties = null == d ? Collections.emptyList() : d.getProperties();

            for (DomainProperty p : properties)
            {
                if (null != getColumn(p.getName()))
                {
                    BaseColumnInfo builtin = (BaseColumnInfo)getColumn(p.getName());
                    // StorageProvisioner should have already handled copying most propertydescriptor attributes
                    if (!p.isHidden())
                        _showColumn(builtin);
                    if (null != p.getLabel())
                        builtin.setLabel(p.getLabel());
                    builtin.setPropertyURI(p.getPropertyURI());
                    continue;
                }
                var col = getStorageColumn(d, p);

                if (col == null)
                {
                    _log.error("didn't find column for property: " + p.getPropertyURI());
                    continue;
                }

                var wrapped = newDatasetColumnInfo(this, col, p.getPropertyDescriptor());
                addColumn(wrapped);

                // Set the FK if the property descriptor is configured as a lookup or a conceptURI.
                // DatasetSchemaTableInfos aren't cached, so it's safe to include the current user
                PropertyDescriptor pd = p.getPropertyDescriptor();
                if (null != pd && (pd.getLookupQuery() != null || pd.getConceptURI() != null))
                {
                    wrapped.setFk(PdLookupForeignKey.create(DefaultSchema.get(user, getContainer()), pd));
                }

                if (p.isMvEnabled())
                {
                    var baseColumn = StorageProvisioner.get().getMvIndicatorColumn(_storage, pd,
                                                "No MV column found for '" + col.getName() + "' in dataset '" + getName() + "'");
                    var mvColumn = newDatasetColumnInfo(this, baseColumn, p.getPropertyDescriptor().getPropertyURI());
                    mvColumn.setName(p.getName() + MvColumn.MV_INDICATOR_SUFFIX);
                    mvColumn.setLabel(col.getLabel() + " MV Indicator");
                    mvColumn.setPropertyURI(wrapped.getPropertyURI());
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);

                    var rawValueCol = new AliasedColumn(wrapped.getName() + RawValueColumn.RAW_VALUE_SUFFIX, wrapped);
                    rawValueCol.setDisplayColumnFactory(BaseColumnInfo.DEFAULT_FACTORY);
                    rawValueCol.setLabel(wrapped.getLabel() + " Raw Value");
                    rawValueCol.setUserEditable(false);
                    rawValueCol.setHidden(true);
                    rawValueCol.setMvColumnName(null); // This version of the column does not show missing values
                    rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields
                    rawValueCol.setRawValueColumn(true);

                    addColumn(mvColumn);
                    addColumn(rawValueCol);

                    wrapped.setMvColumnName(mvColumn.getFieldKey());
                }
            }

            // If we have an extra key, and it's server-managed, make it non-editable
            if (def.getKeyManagementType() != KeyManagementType.None)
            {
                for (ColumnInfo col : getColumns())
                {
                    if (col.getName().equals(def.getKeyPropertyName()))
                    {
                        ((BaseColumnInfo)col).setUserEditable(false);
                    }
                }
            }

            // Dataset

            var datasetColumn = new ExprColumn(this, "Dataset", new SQLFragment("CAST('" + def.getEntityId() + "' AS " + getSqlDialect().getGuidType() + ")"), JdbcType.VARCHAR);
            LookupForeignKey datasetFk = new LookupForeignKey("entityid")
            {
                @Override
                public TableInfo getLookupTableInfo()
                {
                    StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(_container), user);
                    return schema.getTable("Datasets");
                }
            };
            datasetColumn.setFk(datasetFk);
            datasetColumn.setUserEditable(false);
            datasetColumn.setHidden(true);
            datasetColumn.setDimension(false);
            addColumn(datasetColumn);

            setPkColumnNames(Arrays.asList("LSID"));
        }


        public ColumnInfo getParticipantColumn()
        {
            return _ptid;
        }


        @Override
        public ColumnInfo getColumn(@NotNull String name)
        {
            if ("ParticipantId".equalsIgnoreCase(name))
                return getParticipantColumn();
            return super.getColumn(name);
        }

        @Override
        @NotNull
        public SQLFragment getFromSQL(String alias)
        {
            SqlDialect d = getSqlDialect();
            if (null == _storage)
            {
                SQLFragment from = new SQLFragment();
                from.appendComment("<DatasetDefinition: " + getName() + ">", d); // UNDONE stash name
                String comma = " ";
                from.append("(SELECT ");
                for (ColumnInfo ci : _template.getColumns())
                {
                    from.append(comma).append(NullColumnInfo.nullValue(ci.getSqlTypeName())).append(" AS ").append(ci.getName());
                    comma = ", ";
                }
                from.append("\nWHERE 0=1) AS ").append(alias);
                from.appendComment("</DatasetDefinition>", d);
                return from;
            }

            boolean addContainerFilter = (isShared() && !_multiContainer) || getDataSharingEnum() != DataSharing.NONE;
            if (addContainerFilter)
            {
                SQLFragment ret = new SQLFragment();
                ret.appendComment("<DatasetDefinition: " + getName() + (getDataSharingEnum() != DataSharing.NONE ? " sharing=" + getDataSharingEnum().name() : "") + ">", d);
                ret.append("(SELECT * FROM ");
                ret.append(_storage.getFromSQL("_"));
                ret.append(" WHERE container="); //?) ");
                if (getDataSharingEnum() == DataSharing.NONE)
                    ret.appendValue(getContainer());
                else
                    ret.appendValue(getDefinitionContainer());
                ret.append(")");
                ret.append(alias);
                ret.appendComment("</DatasetDefinition>", d);
                return ret;
            }
            else
            {
                return _storage.getFromSQL(alias);
            }
        }

        //
        // UpdateableTableInfo
        //

        @Override
        public Domain getDomain()
        {
            return DatasetDefinition.this.getDomain();
        }

        @Override
        public DomainKind getDomainKind()
        {
            return DatasetDefinition.this.getDomainKind();
        }

        @Override
        public TableInfo getSchemaTableInfo()
        {
            return _storage;
        }

        @Override
        public CaseInsensitiveHashMap<String> remapSchemaColumns()
        {
            CaseInsensitiveHashMap<String> m = new CaseInsensitiveHashMap<>();
            
            // why did I add an underscore to the stored mv indicators???
            for (ColumnInfo col : getColumns())
            {
                if (null == col.getMvColumnName())
                    continue;
                m.put(col.getName() + "_" + MvColumn.MV_INDICATOR_SUFFIX, col.getMvColumnName().getName());
            }

            // shouldn't getStorageTableInfo().getColumn("date").getPropertyURI() == getVisitDateURI()?
            if (!getStudy().getTimepointType().isVisitBased())
            {
                m.put(getStorageTableInfo().getColumn("date").getPropertyURI(), getVisitDateURI());
            }

            return m;
        }

        @Override
        public boolean canUserAccessPhi()
        {
            throw new IllegalStateException("Should not be called on DatasetSchemaTableInfo");
        }

        @Override
        public AuditHandler getAuditHandler(AuditBehaviorType auditBehaviorType)
        {
            return new DatasetAuditHandler(_def);
        }
    }

    public static class DatasetAuditHandler extends AbstractAuditHandler
    {
        private Dataset _dataset;

        public DatasetAuditHandler(Dataset dataset)
        {
            _dataset = dataset;
        }

        @Override
        public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment)
        {
            QueryService.get().getDefaultAuditHandler().addSummaryAuditEvent(user, c, table, action, dataRowCount, auditBehaviorType, userComment);
        }

        @Override
        public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditTypeOverride, @Nullable String userComment, QueryService.AuditAction action, @Nullable List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> existingRows)
        {
            switch (table.getAuditBehavior(auditTypeOverride))
            {
                case NONE,SUMMARY -> {}
                case DETAILED ->
                {
                    Objects.requireNonNull(rows);
                    AuditLogService auditLog = AuditLogService.get();

                    // Caller should provide existing rows for MERGE
                    assert action != QueryService.AuditAction.MERGE || null != existingRows;

                    List<DatasetAuditProvider.DatasetAuditEvent> batch = new ArrayList<>();
                    for (int i=0; i < rows.size(); i++)
                    {
                        Map<String, Object> row = rows.get(i);
                        Map<String, Object> existingRow = null==existingRows ? null : existingRows.get(i);
                        // note switched order (oldRecord, newRecord)
                        var event = createDetailedAuditRecord(user, c, (AuditConfigurable)table, action, userComment, row, existingRow);
                        batch.add(event);
                        if (batch.size() > 1000)
                        {
                            auditLog.addEvents(user, batch);
                            batch.clear();
                        }
                    }
                    if (batch.size() > 0)
                    {
                        auditLog.addEvents(user, batch);
                        batch.clear();
                    }
                }
            }
        }

        @Override
        protected AuditTypeEvent createSummaryAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, int rowCount, @Nullable Map<String, Object> row)
        {
            throw new UnsupportedOperationException();
        }

        /**
         * NOTE: userComment field is not supported for this domain and will be ignored
         */
        @Override
        protected DatasetAuditProvider.DatasetAuditEvent createDetailedAuditRecord(User user, Container c, AuditConfigurable tInfo, QueryService.AuditAction action, @Nullable String userComment, @Nullable Map<String, Object> record, Map<String, Object> existingRecord)
        {
            String auditComment = switch (action)
                    {
                        case INSERT -> "A new dataset record was inserted";
                        case DELETE, TRUNCATE -> "A dataset record was deleted";
                        case UPDATE, MERGE ->  null!=existingRecord && !existingRecord.isEmpty() ? "A dataset record was modified" : "A new dataset record was inserted";
                        default -> "A dataset record was modified";
                    };

            String oldRecordString = null;
            String newRecordString = null;
            Object lsid = record.get("lsid");

            if (action==DELETE || action==TRUNCATE)
            {
                oldRecordString = DatasetAuditProvider.encodeForDataMap(c, record);
            }
            else if (existingRecord != null && existingRecord.size() > 0)
            {
                Pair<Map<String, Object>, Map<String, Object>> rowPair = AuditHandler.getOldAndNewRecordForMerge(record, existingRecord, Collections.emptySet(), tInfo == null? TableInfo.defaultExcludedDetailedUpdateAuditFields : tInfo.getExcludedDetailedUpdateAuditFields(), tInfo);
                oldRecordString = DatasetAuditProvider.encodeForDataMap(c, rowPair.first);

                // Check if no fields changed, if so adjust messaging
                if (rowPair.second.size() == 0 )
                {
                    auditComment = "Dataset row was processed, but no changes detected";
                    // Record values that were processed
                    newRecordString = DatasetAuditProvider.encodeForDataMap(c, record);
                }
                else
                {
                    newRecordString = DatasetAuditProvider.encodeForDataMap(c, rowPair.second);
                }
            }
            else
            {
                newRecordString = DatasetAuditProvider.encodeForDataMap(c, record);
            }

            DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(c.getId(), auditComment);

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setDatasetId(_dataset.getDatasetId());
            event.setHasDetails(true);

            event.setLsid(lsid == null ? null : lsid.toString());

            if (oldRecordString != null) event.setOldRecordMap(oldRecordString);
            if (newRecordString != null) event.setNewRecordMap(newRecordString);

            return event;
        }

        /**
         * General purpose method to add dataset audit events.
         * @param requiredAuditType The expected audit behavior type. If this does not match the type set on the
         *                          dataset, then the event will not be logged.
         */
        public void addAuditEvent(User user, Container c, AuditBehaviorType requiredAuditType, String comment, @Nullable UploadLog ul)

        {
            TableInfo table = _dataset.getTableInfo(user);
            if (table != null && table.getAuditBehavior((AuditBehaviorType)null) != requiredAuditType)
                return;

            DatasetAuditProvider.DatasetAuditEvent event = new DatasetAuditProvider.DatasetAuditEvent(c.getId(), comment);

            if (c.getProject() != null)
                event.setProjectId(c.getProject().getId());
            event.setDatasetId(_dataset.getDatasetId());
            if (ul != null)
            {
                event.setLsid(ul.getFilePath());
            }
            AuditLogService.get().addEvent(user, event);
        }
    }

    static BaseColumnInfo newDatasetColumnInfo(TableInfo tinfo, final ColumnInfo from, final String propertyURI)
    {
        var result = new AliasedColumn(tinfo, from.getName(), from);
        if (null != propertyURI)
            result.setPropertyURI(propertyURI);
        // Hidden doesn't get copied with the default set of properties
        result.setHidden(from.isHidden());
        result.setMetaDataName(from.getMetaDataName());
        return result;
    }

    
    static BaseColumnInfo newDatasetColumnInfo(TableInfo tinfo, ColumnInfo from, PropertyDescriptor p)
    {
        var ci = newDatasetColumnInfo(tinfo, from, p.getPropertyURI());
        // We are currently assuming the db column name is the same as the propertyname
        // I want to know if that changes
        assert ci.getName().equalsIgnoreCase(p.getName());
        ci.setName(p.getName());
        ci.setAlias(from.getAlias());
        return ci;
    }


    private static final Set<PropertyDescriptor> standardPropertySet = new HashSet<>();
    private static final Map<String, PropertyDescriptor> standardPropertyMap = new CaseInsensitiveHashMap<>();

    public static Set<PropertyDescriptor> getStandardPropertiesSet()
    {
        synchronized(standardPropertySet)
        {
            if (standardPropertySet.isEmpty())
            {
                TableInfo info = getTemplateTableInfo();
                for (ColumnInfo col : info.getColumns())
                {
                    String propertyURI = col.getPropertyURI();
                    if (propertyURI == null)
                        continue;
                    String name = col.getName();
                    PropertyType type = PropertyType.getFromClass(col.getJdbcType().getJavaClass());
                    PropertyDescriptor pd = new PropertyDescriptor(
                            propertyURI, type, name, ContainerManager.getSharedContainer());
                    standardPropertySet.add(pd);
                }
            }
            return standardPropertySet;
        }
    }


    Domain _domain = null;

    @Override
    @Transient
    public Domain getDomain()
    {
        if (isInherited())
        {
            StudyImpl shared = getDefinitionStudy();
            DatasetDefinition ds = shared.getDataset(getDatasetId());
            return null == ds ? null : ds.getDomain();
        }

        synchronized (this)
        {
            if (null == getTypeURI())
                return null;
            if (null != _domain)
                return _domain;
        }

        Domain d=null;
        try (Transaction t = StudySchema.getInstance().getSchema().getScope().ensureTransaction(_lock))
        {
            if (null != getTypeURI() && null == _domain)
                d = PropertyService.get().getDomain(getContainer(), getTypeURI());
            t.commit();
        }

        synchronized (this)
        {
            if (null != d)
                _domain = d;
            return _domain;
        }
    }


    public DomainKind<DatasetDomainKindProperties> getDomainKind()
    {
        switch (getStudy().getTimepointType())
        {
            case VISIT:
                return new VisitDatasetDomainKind();
            case DATE:
            case CONTINUOUS:
                return new DateDatasetDomainKind();
            default:
                return null;
        }
    }


    public Domain refreshDomain()
    {
        _domain = null;
        _domain = getDomain();
        return _domain;
    }
    

    public static Map<String,PropertyDescriptor> getStandardPropertiesMap()
    {
        synchronized(standardPropertyMap)
        {
            if (standardPropertyMap.isEmpty())
            {
                for (PropertyDescriptor pd : getStandardPropertiesSet())
                {
                    standardPropertyMap.put(pd.getName(), pd);
                }
            }
            return standardPropertyMap;
        }
    }

    public static String getStudyBaseURI()
    {
        return "http://cpas.labkey.com/Study#";
    }

    private static String uriForName(String name)
    {
        assert null != getStandardPropertiesMap().get(name);
        assert "container".equalsIgnoreCase(name)  || getStandardPropertiesMap().get(name).getPropertyURI().equalsIgnoreCase(getStudyBaseURI() + name);
        return getStandardPropertiesMap().get(name).getPropertyURI();
    }

    public static String getKeyURI()
    {
        return uriForName("_key");
    }

    public static String getSequenceNumURI()
    {
        return uriForName("SequenceNum");
    }

    public static String getDatasetIdURI()
    {
        return uriForName("DatasetId");
    }

    public static String getParticipantIdURI()
    {
        return uriForName("ParticipantId");
    }

    public static String getVisitDateURI()
    {
        return uriForName("VisitDate");
    }

    public static String getSiteIdURI()
    {
        return uriForName("SiteId");
    }

    public static String getCreatedURI()
    {
        return uriForName("Created");
    }

    public static String getSourceLsidURI()
    {
        return uriForName("SourceLSID");
    }

    public static String getModifiedURI()
    {
        return uriForName("Modified");
    }

    public static String getQCStateURI()
    {
        return uriForName(DatasetTableImpl.QCSTATE_ID_COLNAME);
    }


    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    @Override
    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    @Nullable
    @Override
    public CohortImpl getCohort()
    {
        if (_cohortId == null)
            return null;
        return new TableSelector(StudySchema.getInstance().getTableInfoCohort()).getObject(_cohortId, CohortImpl.class);
    }

    public Integer getPublishSourceId()
    {
        return _publishSourceId;
    }

    public void setPublishSourceId(Integer publishSourceId)
    {
        _publishSourceId = publishSourceId;
    }

    public String getPublishSourceType()
    {
        return _publishSourceType;
    }

    public void setPublishSourceType(String publishSourceType)
    {
        _publishSourceType = publishSourceType;
    }

    @Override
    public String toString()
    {
        return "DatasetDefinition: " + getLabel() + " " + getDatasetId();
    }


    /** Do the actual delete from the underlying table, without auditing */
    public void deleteRows(Collection<String> allLSIDs)
    {
        List<Collection<String>> rowLSIDSlices = slice(allLSIDs);

        TableInfo data = getStorageTableInfo();

        try (Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            for (Collection<String> rowLSIDs : rowLSIDSlices)
            {
                SimpleFilter filter = new SimpleFilter();
                if(null!=data.getColumn("container"))
                {
                    Container rowsContainer = getContainer();
                    if (getDataSharingEnum() != DataSharing.NONE)
                        rowsContainer = getDefinitionContainer();
                    filter.addWhereClause("Container=?", new Object[]{rowsContainer});
                }
                filter.addInClause(FieldKey.fromParts("LSID"), rowLSIDs);
                Table.delete(data, filter);
                StudyManager.datasetModified(this, true);
            }
            transaction.commit();
        }
    }

    /** Slice the full collection into separate lists that are no longer than 1000 elements long */
    private List<Collection<String>> slice(Collection<String> allLSIDs)
    {
        if (allLSIDs.size() <= 1000)
        {
            return Collections.singletonList(allLSIDs);
        }
        List<Collection<String>> rowLSIDSlices = new ArrayList<>();

        Collection<String> rowLSIDSlice = new ArrayList<>();
        rowLSIDSlices.add(rowLSIDSlice);
        for (String rowLSID : allLSIDs)
        {
            if (rowLSIDSlice.size() > 1000)
            {
                rowLSIDSlice = new ArrayList<>();
                rowLSIDSlices.add(rowLSIDSlice);
            }
            rowLSIDSlice.add(rowLSID);
        }

        return rowLSIDSlices;
    }


    /**
     * dataMaps have keys which are property URIs, and values which have already been converted.
     */
    public List<String> importDatasetData(User user, DataIteratorBuilder in, DataIteratorContext context)
    {
        if (getKeyManagementType() == KeyManagementType.RowId)
        {
            // If additional keys are managed by the server, we need to synchronize around
            // increments, as we're imitating a sequence.
            synchronized (getManagedKeyLock())
            {
                return insertData(user, in, context);
            }
        }
        else
        {
            return insertData(user, in, context);
        }
    }



    void checkForDuplicates(DataIterator data,
                                    int indexLSID, int indexPTID, int indexVisit, int indexKey, int indexReplace,
                                    DataIteratorContext context, Logger logger, CheckForDuplicates checkDuplicates,
                                    ColumnInfo subjectCol, ColumnInfo visitDateCol, ColumnInfo sequenceNumCol)
    {
        BatchValidationException errors = context.getErrors();
        HashMap<String, Object[]> failedReplaceMap = checkAndDeleteDupes(
                data,
                indexLSID, indexPTID, indexVisit, indexKey, indexReplace,
                errors, checkDuplicates);

        if (null != failedReplaceMap && failedReplaceMap.size() > 0)
        {
            StringBuilder error = new StringBuilder();
            error.append("Only one row is allowed for each ");
            error.append(getKeyTypeDescription());
            error.append(".  ");

            error.append("Duplicates were found in the ").append(checkDuplicates == CheckForDuplicates.sourceOnly ? "" : "database or ").append("imported data.");
            errors.addRowError(new ValidationException(error.toString()));

            int errorCount = 0;
            for (Map.Entry<String, Object[]> e : failedReplaceMap.entrySet())
            {
                errorCount++;
                if (errorCount > 100)
                    break;
                Object[] keys = e.getValue();
                StringBuilder err = new StringBuilder("Duplicate: ").append(subjectCol.getLabel()).append(" = ").append(keys[0]);
                if (!isDemographicData())
                {
                    if (!_study.getTimepointType().isVisitBased())
                        err.append(", ").append(visitDateCol.getLabel()).append(" = ").append(keys[1]);
                    else
                        err.append(", ").append(sequenceNumCol.getLabel()).append(" = ").append(keys[1]);
                }
                if (0 < indexKey)
                    err.append(", ").append(data.getColumnInfo(indexKey).getLabel()).append(" = ").append(keys[2]);
                errors.addRowError(new ValidationException(err.toString()));
            }
        }
        if (logger != null) logger.debug("checked for duplicates");
    }

    public enum CheckForDuplicates
    {
        never,
        sourceOnly,
        sourceAndDestination
    }


    // Using temporarily to isolate #34735
    private void checkConnection(Transaction transaction)
    {
        try
        {
            if (transaction.getConnection().isClosed())
            {
                ((ConnectionWrapper)transaction.getConnection()).logAndCheckException(new SQLException("Connection should not be closed! see https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=34735"));
                assert false : "Connection should not be closed!";
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private Transaction ensureTransaction()
    {
        // Make sure a transaction is active. If caller set one up, use a no-op version so that we can handle error
        // situations without trashing the transaction call stack
        DbScope scope = StudyService.get().getDatasetSchema().getScope();
        return scope.isTransactionActive() ? DbScope.NO_OP_TRANSACTION : scope.ensureTransaction();
    }

    private List<String> insertData(User user, DataIteratorBuilder in, DataIteratorContext context)
    {
        ArrayList<String> lsids = new ArrayList<>();
        Logger logger = (Logger)context.getConfigParameters().get(QueryUpdateService.ConfigParameters.Logger);

        context.putConfigParameter(DatasetUpdateService.Config.KeyList, lsids);

        try (Transaction transaction = ensureTransaction())
        {
            long start = System.currentTimeMillis();
            {
                UserSchema schema = QueryService.get().getUserSchema(user, getContainer(), StudyQuerySchema.SCHEMA_NAME);
                TableInfo table = schema.getTable(getName());
                if (table != null)
                {
                    QueryUpdateService qus = table.getUpdateService();
                    qus.loadRows(user, getContainer(), in, context, null);
                }
            }
            long end = System.currentTimeMillis();

            BatchValidationException errors = context.getErrors();
            if (errors.hasErrors())
                throw errors;

            _log.debug("imported " + getName() + " : " + DateUtil.formatDuration(Math.max(0,end-start)));
            transaction.commit();
            if (logger != null) logger.debug("commit complete");

            return lsids;
        }
        catch (BatchValidationException x)
        {
            assert x == context.getErrors();
            assert context.getErrors().hasErrors();
            for (ValidationException rowError : context.getErrors().getRowErrors())
            {
                for (int i=0 ; i<rowError.getGlobalErrorCount() ; i++)
                {
                    SimpleValidationError e = rowError.getGlobalError(i);
                    if (!(e.getCause() instanceof SQLException))
                        continue;
                    String msg = translateSQLException((SQLException)e.getCause());
                    if (null != msg)
                        rowError.getGlobalErrorStrings().set(i, msg);
                }
            }
            return Collections.emptyList();
        }
        catch (SQLException e)
        {
            String translated = translateSQLException(e);
            if (translated != null)
            {
                context.getErrors().addRowError(new ValidationException(translated));
                return Collections.emptyList();
            }
            throw new RuntimeSQLException(e);
        }
    }

    public String translateSQLException(RuntimeSQLException e)
    {
        return translateSQLException(e.getSQLException());
    }

    public String translateSQLException(SQLException e)
    {
        if (RuntimeSQLException.isConstraintException(e) && e.getMessage() != null &&
                (e.getMessage().contains("_pk") || e.getMessage().contains("duplicate key")))
        {
            StringBuilder sb = new StringBuilder("Duplicate dataset row. All rows must have unique ");
            sb.append(getStudy().getSubjectColumnName());
            if (!isDemographicData())
            {
                sb.append("/");
                if (getStudy().getTimepointType().isVisitBased())
                {
                    sb.append("SequenceNum");
                }
                else
                {
                    sb.append("Date");
                }
            }
            if (getKeyPropertyName() != null)
            {
                sb.append("/");
                sb.append(getKeyPropertyName());
            }
            sb.append(" values. (");
            sb.append(e.getMessage());
            sb.append(")");
            return sb.toString();
        }
        return null;
    }


    private String _managedKeyLock = null;

    public Object getManagedKeyLock()
    {
        if (null == _managedKeyLock)
            _managedKeyLock = (getEntityId() + ".MANAGED_KEY_LOCK").intern();
        return _managedKeyLock;
    }

    /**
     * NOTE Currently the caller is still responsible for locking MANAGED_KEY_LOCK while this
     * Iterator is running.  This is asserted in the code, but it would be nice to move the
     * locking into the iterator itself.
     */
    public DataIteratorBuilder getInsertDataIterator(User user, Container container, DataIteratorBuilder in, DataIteratorContext context)
    {
        TableInfo table = getDatasetSchemaTableInfo(user);
        DatasetDataIteratorBuilder b = new DatasetDataIteratorBuilder(this, user, container);
        b.setInput(in);

        boolean allowImportManagedKey = context.getConfigParameterBoolean(DatasetUpdateService.Config.AllowImportManagedKey);
        b.setAllowImportManagedKeys(allowImportManagedKey);
        b.setUseImportAliases(!allowImportManagedKey);
        b.setKeyList((List<String>)context.getConfigParameter(DatasetUpdateService.Config.KeyList));

        Container target = getDataSharingEnum() == DataSharing.NONE ? getContainer() : getDefinitionContainer();
        DataIteratorBuilder standard = StandardDataIteratorBuilder.forInsert(table, b, target, user, context);

        DataIteratorBuilder existing = ExistingRecordDataIterator.createBuilder(standard, table, null);
        DataIteratorBuilder persist = null;

        persist = ((UpdateableTableInfo)table).persistRows(existing, context);

        if (context.getInsertOption() != QueryUpdateService.InsertOption.UPDATE)
        {
            // TODO this feels like a hack, shouldn't this be handled by table.persistRows()???
            CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet("Created", "CreatedBy");
            ((TableInsertDataIteratorBuilder) persist).setDontUpdate(dontUpdate);
        }

        DataIteratorBuilder audit = DetailedAuditLogDataIterator.getDataIteratorBuilder(table, persist, context.getInsertOption(), user, target);
        return LoggingDataIterator.wrap(audit);
    }


    /** @return a SQL expression that generates the LSID for a dataset row
     * MUST match what is produced by DatasetDataIteratorBuilder.DatasetColumnsIterator.LSIDColumn
     * */
    public SQLFragment generateLSIDSQL()
    {
        if (null == getStorageTableInfo())
            return new SQLFragment("''");

        List<SQLFragment> parts = new ArrayList<>();
        parts.add(new SQLFragment("''"));
        parts.add(new SQLFragment("?", "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-"));
        parts.add(new SQLFragment("(SELECT CAST(RowId AS VARCHAR(15)) FROM Core.Containers WHERE EntityId = '" + getContainer().getId() + "')"));
        parts.add(new SQLFragment("':" + String.valueOf(getDatasetId()) + ".'"));
        parts.add(new SQLFragment("participantid"));

        if (!isDemographicData())
        {
            parts.add(new SQLFragment("'.'"));
            if (!_study.getTimepointType().isVisitBased())
            {
                SQLFragment seq = new SQLFragment();
                seq.append("CAST((");
                seq.append(StudyUtils.sequenceNumFromDateSQL("date"));
                seq.append(") AS VARCHAR(36))");
                parts.add(seq);
                parts.add(new SQLFragment("'.0000'"));   // Match what insert/import does
            }
            else
                parts.add(new SQLFragment("CAST(CAST(sequencenum AS NUMERIC(15,4)) AS VARCHAR)"));

            if (!_study.getTimepointType().isVisitBased() && getUseTimeKeyField())
            {
                parts.add(new SQLFragment("'.'"));
                parts.add(StudyManager.timePortionFromDateSQL("Date"));
            }
            else if (getKeyPropertyName() != null)
            {
                var key = getStorageTableInfo().getColumn(getKeyPropertyName());
                if (null != key)
                {
                    // It's possible for the key value to be null. In SQL, NULL concatenated with any other value is NULL,
                    // so use COALESCE to get rid of NULLs
                    parts.add(new SQLFragment("'.'"));
                    parts.add(new SQLFragment("COALESCE(_key,'')"));
                }
            }
        }

        return StudySchema.getInstance().getSchema().getSqlDialect().concatenate(parts.toArray(new SQLFragment[parts.size()]));
    }



    /**
     * If all the dupes can be replaced, delete them. If not return the ones that should NOT be replaced
     * and do not delete anything
     */
    private HashMap<String, Object[]> checkAndDeleteDupes(DataIterator rows,
                                                          int indexLSID, int indexPTID, int indexDate, int indexKey, int indexReplace,
                                                          BatchValidationException errors, CheckForDuplicates checkDuplicates)
    {
        final boolean isDemographic = isDemographicData();

        try
        {
            // duplicate keys found in error
            final LinkedHashMap<String,Object[]> noDeleteMap = new LinkedHashMap<>();

            StringBuilder sbIn = new StringBuilder();
            final Map<String, Object[]> uriMap = new HashMap<>();
            int count = 0;
            while (rows.next())
            {
                String uri = (String)rows.get(indexLSID);
                String ptid = ConvertUtils.convert(rows.get(indexPTID));
                Object[] key = new Object[4];
                key[0] = ptid;
                key[1] = rows.get(indexDate);
                if (indexKey > 0)
                    key[2] = rows.get(indexKey);
                Boolean replace = Boolean.FALSE;
                if (indexReplace > 0)
                {
                    Object replaceStr = rows.get(indexReplace);
                    replace =
                            null==replaceStr ? Boolean.FALSE :
                            replaceStr instanceof Boolean ? (Boolean)replaceStr :
                            (Boolean)ConvertUtils.convert(String.valueOf(replaceStr),Boolean.class);
                    key[3] = replace;
                }

                String uniq = isDemographic ? ptid : uri;
                // Don't count NULL's in any of the key fields as potential dupes; instead let downstream processing catch this
                // so the fact the values (or entire column) are missing is reported correctly.
                if (null == uniq || null == key[1] || (indexKey > 0 && null == key[2]))
                    continue;
                if (null != uriMap.put(uniq, key))
                    noDeleteMap.put(uniq,key);

                // partial fix for 16647, we should handle the replace case differently (do we ever replace?)
                String sep = "";
                if (uriMap.size() < 10000 || Boolean.TRUE==replace)
                {
                    if (uniq.contains(("'")))
                        uniq = uniq.replaceAll("'","''");
                    sbIn.append(sep).append("'").append(uniq).append("'");
                    sep = ", ";
                }
                count++;
            }
            if (0 == count)
                return null;

            // For source check only, if we have duplicates, and we don't have an auto-keyed dataset,
            // then we cannot proceed.
            if (checkDuplicates == CheckForDuplicates.sourceOnly)
            {
                if (noDeleteMap.size() > 0 && getKeyManagementType() == KeyManagementType.None)
                    return noDeleteMap;
                else
                    return null;
            }
            else // also check target dataset
                return checkTargetDupesAndDelete(isDemographic, noDeleteMap, sbIn, uriMap);
        }
        catch (BatchValidationException vex)
        {
            if (vex != errors)
            {
                for (ValidationException validationException : vex.getRowErrors())
                {
                    errors.addRowError(validationException);
                }
            }
            return null;
        }
        catch (UnexpectedException unex)
        {
            errors.addRowError(new ValidationException(unex.getMessage()));
            return null;
        }
    }

    private HashMap<String, Object[]> checkTargetDupesAndDelete(final boolean demographic, final LinkedHashMap<String, Object[]> noDeleteMap, StringBuilder sbIn, final Map<String, Object[]> uriMap)
    {
        // duplicate keys found that should be deleted
        final Set<String> deleteSet = new HashSet<>();

        TableInfo tinfo = getStorageTableInfo();
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause((demographic ?"ParticipantId":"LSID") + " IN (" + sbIn + ")", new Object[]{});
        if (isShared())
        {
            Container rowsContainer = getContainer();
            if (getDataSharingEnum() != DataSharing.NONE)
                rowsContainer = getDefinitionContainer();
            filter.addCondition(new FieldKey(null,"Container"), rowsContainer);
        }

        new TableSelector(tinfo, filter, null).forEachMap(orig -> {
            String lsid = (String) orig.get("LSID");
            String uniq = demographic ? (String)orig.get("ParticipantID"): lsid;
            Object[] keys = uriMap.get(uniq);
            boolean replace = Boolean.TRUE.equals(keys[3]);
            if (replace)
            {
                deleteSet.add(lsid);
            }
            else
            {
                noDeleteMap.put(uniq, keys);
            }

        });

        // If we have duplicates, and we don't have an auto-keyed dataset,
        // then we cannot proceed.
        if (noDeleteMap.size() > 0 && getKeyManagementType() == KeyManagementType.None)
            return noDeleteMap;

        if (deleteSet.size() == 0)
            return null;

        SimpleFilter deleteFilter = new SimpleFilter();
        StringBuilder sbDelete = new StringBuilder();
        String sep = "";
        for (String s : deleteSet)
        {
            if (s.contains(("'")))
                s = s.replaceAll("'","''");
            sbDelete.append(sep).append("'").append(s).append("'");
            sep = ", ";
        }
        deleteFilter.addWhereClause("LSID IN (" + sbDelete + ")", new Object[]{});
        Table.delete(tinfo, deleteFilter);

        return null;
    }

    /**
     * Gets the current highest key value for a server-managed key field.
     * If no data is returned, this method returns 0.
     */
    int getMaxKeyValue()
    {
        TableInfo tInfo = getStorageTableInfo();
        SQLFragment sqlf = new SQLFragment("SELECT COALESCE(MAX(CAST(_key AS INTEGER)), 0) FROM ").append(tInfo.getFromSQL("_"));
        Integer newKey = new SqlSelector(tInfo.getSchema(),sqlf).getObject(Integer.class);
        return newKey.intValue();
    }


/*    public boolean verifyUniqueKeys()
    {
        ResultSet rs = null;
        try
        {
            var colKey = getKeyPropertyName()==null ? null : getStorageTableInfo().getColumn(getKeyPropertyName());

            TableInfo tt = getStorageTableInfo();
            String cols = isDemographicData() ? "participantid" :
                    null == colKey ? "participantid, sequencenum" :
                    "participantid, sequencenum, " + colKey.getSelectName();

            rs = Table.executeQuery(DbSchema.get("study"), "SELECT " + cols + " FROM "+ tt.getFromSQL("ds") + " GROUP BY " + cols + " HAVING COUNT(*) > 1", null);
            if (rs.next())
                return false;
            return true;
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


    // WHEN isDemographic() or getKeyPropertyName() changes we need to regenerate the LSIDS for this dataset
    public void regenerateLSIDS()
    {
    }  */



    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DatasetDefinition that = (DatasetDefinition) o;

        if (_datasetId != that._datasetId) return false;
        // The _studyDateBased member variable is populated lazily in the getter,
        // so go through the getter instead of relying on the variable to be populated
        return getStudy() != null ? getStudy().equals(that.getStudy()) : that.getStudy() == null;
    }

    @Override
    public int hashCode()
    {
        int result = _study != null ? _study.hashCode() : 0;
        result = 31 * result + _datasetId;
        return result;
    }

    @Override
    public void afterPropertiesSet()
    {
        if (null == _isShared)
        {
            Study s = getStudy();
            if (null != s)
                _isShared = s.getShareDatasetDefinitions();
        }
    }


    private static class DatasetDefObjectFactory extends BeanObjectFactory<DatasetDefinition>
    {
        DatasetDefObjectFactory()
        {
            super(DatasetDefinition.class);
            assert !_readableProperties.remove("storageTableInfo");
            assert !_readableProperties.remove("domain");
        }
    }


    static
    {
        ObjectFactory.Registry.register(DatasetDefinition.class, new DatasetDefObjectFactory());
    }


    @Override
    public Map<String, Object> getDatasetRow(User u, String lsid)
    {
        if (null == lsid)
            return null;
        List<Map<String, Object>> rows = getDatasetRows(u, Collections.singleton(lsid));
        assert rows.size() <= 1 : "Expected zero or one matching row, but found " + rows.size();
        return rows.isEmpty() ? null : rows.get(0);
    }


    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getDatasetRows(User u, Collection<String> lsids)
    {
        // Unfortunately we need to use two tableinfos: one to get the column names with correct casing,
        // and one to get the data.  We should eventually be able to convert to using Query completely.
        TableInfo queryTableInfo = getTableInfo(u);

        DatasetSchemaTableInfo tInfo = getDatasetSchemaTableInfo(u);
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("lsid"), lsids);

        Set<String> selectColumns = new TreeSet<>();
        List<ColumnInfo> alwaysIncludedColumns = tInfo.getColumns("created", "createdby", "lsid", "sourcelsid", "QCState");

        for (ColumnInfo col : tInfo.getColumns())
        {
            // special handling for lsids and keys -- they're not user-editable,
            // but we want to display them
            if (!col.isUserEditable())
            {
                if (!(alwaysIncludedColumns.contains(col) ||
                        col.isKeyField() ||
                        col.getName().equalsIgnoreCase(getKeyPropertyName())))
                {
                    continue;
                }
            }
            selectColumns.add(col.getName());
            if (col.isMvEnabled())
            {
                // include the indicator column for MV enabled fields
                selectColumns.add(col.getMvColumnName().getName());
            }
        }

        List<Map<String, Object>> datas = new ArrayList<>(new TableSelector(tInfo, selectColumns, filter, null).getMapCollection());

        if (datas.isEmpty())
            return datas;

        if (datas.get(0) instanceof ArrayListMap)
        {
            ((ArrayListMap)datas.get(0)).getFindMap().remove("_key");
        }

        List<Map<String, Object>> canonicalDatas = new ArrayList<>(datas.size());

        for (Map<String, Object> data : datas)
        {
            canonicalDatas.add(canonicalizeDatasetRow(data, queryTableInfo.getColumns()));
        }

        return canonicalDatas;
    }

        // change a map's keys to have proper casing just like the list of columns
    private Map<String,Object> canonicalizeDatasetRow(Map<String,Object> source, List<ColumnInfo> columns)
    {
        CaseInsensitiveHashMap<String> keyNames = new CaseInsensitiveHashMap<>();
        for (ColumnInfo col : columns)
        {
            keyNames.put(col.getName(), col.getName());
        }

        Map<String,Object> result = new CaseInsensitiveHashMap<>();

        for (Map.Entry<String,Object> entry : source.entrySet())
        {
            String key = entry.getKey();
            String newKey = keyNames.get(key);
            if (newKey != null)
                key = newKey;
            else if ("_row".equals(key))
                continue;
            result.put(key, entry.getValue());
        }

        return result;
    }

    private void deleteProvenance(Container c, User u, Collection<String> lsids)
    {
        ProvenanceService.get().deleteProvenanceByLsids(c, u, lsids, true, Set.of(StudyPublishService.STUDY_PUBLISH_PROTOCOL_LSID));
    }

    @Override
    public void deleteDatasetRows(User u, Collection<String> lsids)
    {
        // Need to fetch the old item in order to log the deletion
        List<Map<String, Object>> oldDatas = getDatasetRows(u, lsids);

        try (Transaction transaction = ensureTransaction())
        {
            deleteProvenance(getContainer(), u, lsids);
            deleteRows(lsids);

            new DatasetAuditHandler(this).addAuditEvent(u, getContainer(), getTableInfo(u), AuditBehaviorType.DETAILED, null, QueryService.AuditAction.DELETE, oldDatas, null);

            transaction.commit();
        }
    }

    public static void cleanupOrphanedDatasetDomains()
    {
        DbSchema s = StudySchema.getInstance().getSchema();
        if (null == s)
            return;

        List<Integer> ids = new SqlSelector(s, "SELECT domainid FROM exp.domaindescriptor WHERE domainuri like '%:StudyDataset%Folder-%' and domainuri not in (SELECT typeuri from study.dataset)").getArrayList(Integer.class);
        for (Integer id : ids)
        {
            Domain domain = PropertyService.get().getDomain(id.intValue());
            try
            {
                domain.delete(null);
            }
            catch (DomainNotFoundException x)
            {
                DomainDescriptor domainDescriptor = OntologyManager.getDomainDescriptor(domain.getTypeId());
                if (domainDescriptor != null)
                {
                    _log.error("Likely domain project/container mismatch for " + domain + ". Container: " + domainDescriptor.getContainer().getPath() + ", marked as project: " + domainDescriptor.getProject().getPath());
                }
                _log.error("Failed to delete orphaned dataset domain " + domain + " in container " + domain.getContainer().getPath(), x);
            }
        }
    }

    public static class Builder implements org.labkey.api.data.Builder<DatasetDefinition>
    {
        private final String _name;
        private String _label;
        private Boolean _isShowByDefault = true;
        private Integer _categoryId;
        private String _visitDatePropertyName;
        private String _keyPropertyName;
        private KeyManagementType _keyManagementType = KeyManagementType.None;
        private String _description;
        private Boolean _demographicData = false;   //demographic information, sequenceNum
        private Integer _cohortId;
        private Integer _publishSourceId;           // the identifier of the published data source
        private PublishSource _publishSource;       // the type of published data source (assay, sample type, ...)
        private String _tag;
        private String _type = Dataset.TYPE_STANDARD;
        private String _datasharing;
        private Boolean _useTimeKeyField = false;
        private Integer _datasetId;
        private StudyImpl _study;

        public Builder(String name)
        {
            _name = name;
        }

        public Builder setDescription(String description)
        {
            _description = description;
            return this;
        }

        public Builder setShowByDefault(Boolean isShowByDefault)
        {
            _isShowByDefault = isShowByDefault;
            return this;
        }

        public Builder setType(String type)
        {
            _type = type;
            return this;
        }

        public Builder setDemographicData(Boolean demographicData)
        {
            _demographicData = demographicData;
            return this;
        }

        public Builder setUseTimeKeyField(Boolean useTimeKeyField)
        {
            _useTimeKeyField = useTimeKeyField;
            return this;
        }

        public Builder setKeyManagementType(KeyManagementType keyManagementType)
        {
            _keyManagementType = keyManagementType;
            return this;
        }

        public Builder setCohortId(Integer cohortId)
        {
            _cohortId = cohortId;
            return this;
        }

        public Builder setTag(String tag)
        {
            _tag = tag;
            return this;
        }

        public Builder setLabel(String label)
        {
            _label = label;
            return this;
        }

        public Builder setVisitDatePropertyName(String visitDatePropertyName)
        {
            _visitDatePropertyName = visitDatePropertyName;
            return this;
        }

        public Builder setCategoryId(Integer categoryId)
        {
            _categoryId = categoryId;
            return this;
        }

        public Builder setKeyPropertyName(String keyPropertyName)
        {
            _keyPropertyName = keyPropertyName;
            return this;
        }

        public Builder setPublishSourceId(Integer publishSourceId)
        {
            _publishSourceId = publishSourceId;
            return this;
        }

        public Builder setPublishSource(PublishSource publishSource)
        {
            _publishSource = publishSource;
            return this;
        }

        public Builder setDataSharing(String dataSharing)
        {
            _datasharing = dataSharing;
            return this;
        }

        public Builder setDatasetId(Integer datasetId)
        {
            _datasetId = datasetId;
            return this;
        }

        public Builder setStudy(StudyImpl study)
        {
            _study = study;
            return this;
        }

        public Integer getDatasetId()
        {
            return _datasetId;
        }

        public StudyImpl getStudy()
        {
            return _study;
        }

        @Override
        public DatasetDefinition build()
        {
            DatasetDefinition dsd = new DatasetDefinition(_study, _datasetId, _name, _label != null ? _label : _name, null, null, null);
            dsd.setShowByDefault(_isShowByDefault);
            dsd.setType(_type);
            dsd.setDemographicData(_demographicData);
            dsd.setUseTimeKeyField(_useTimeKeyField);
            dsd.setKeyManagementType(_keyManagementType);
            dsd.setDescription(_description);
            dsd.setCohortId(_cohortId);
            dsd.setTag(_tag);
            dsd.setVisitDatePropertyName(_visitDatePropertyName);
            if (_categoryId != null)
                dsd.setCategoryId(_categoryId);
            if (_keyPropertyName != null)
                dsd.setKeyPropertyName(_keyPropertyName);
            if (_publishSourceId != null)
            {
                dsd.setPublishSourceId(_publishSourceId);
                assert _publishSource != null : "PublishSource must be set if a publish source ID is specified";
                dsd.setPublishSourceType(_publishSource.name());
            }
            if (_datasharing != null)
                dsd.setDataSharing(_datasharing);

            return dsd;
        }
    }

    public static class TestCleanupOrphanedDatasetDomains extends Assert
    {
        @Test
        public void test()
        {
            cleanupOrphanedDatasetDomains();
        }
    }
}
