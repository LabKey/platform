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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections15.MultiMap;
import org.apache.commons.collections15.multimap.MultiHashMap;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.MvColumn;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.RawValueColumn;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorContext;
import org.labkey.api.gwt.client.ui.domain.CancellationException;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:29:31 AM
 */
public class DataSetDefinition extends AbstractStudyEntity<DataSetDefinition> implements Cloneable, DataSet
{
    // standard string to use in URLs etc.
    public static final String DATASETKEY = "datasetId";
    private static final Object MANAGED_KEY_LOCK = new Object();
    private static Logger _log = Logger.getLogger(DataSetDefinition.class);

    private StudyImpl _study;
    private int _dataSetId;
    private String _name;
    private String _typeURI;
    private String _category;
    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private @NotNull KeyManagementType _keyManagementType = KeyManagementType.None;
    private String _description;
    private boolean _demographicData; //demographic information, sequenceNum
    private Integer _cohortId;
    private Integer _protocolId; // indicates that dataset came from an assay. Null indicates no source assay
    private String _fileName; // Filename from the original import  TODO: save this at import time and load it from db

    private static final String[] BASE_DEFAULT_FIELD_NAMES_ARRAY = new String[]
    {
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
        "Dataset",
        "ParticipantSequenceKey",
        // The following columns names don't refer to actual built-in dataset columns, but
        // they're used by import ('replace') or are commonly used/confused synonyms for built-in column names
        "replace",
        "visit",
        "participant"
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
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "Dataset",
        "ParticipantSequenceKey"
    };

    static final CaseInsensitiveHashSet DEFAULT_ABSOLUTE_DATE_FIELDS;
    static final CaseInsensitiveHashSet DEFAULT_RELATIVE_DATE_FIELDS;
    static final CaseInsensitiveHashSet DEFAULT_VISIT_FIELDS;
    private static final CaseInsensitiveHashSet HIDDEN_DEFAULT_FIELDS = new CaseInsensitiveHashSet(HIDDEN_DEFAULT_FIELD_NAMES_ARRAY);

    static
    {
        DEFAULT_ABSOLUTE_DATE_FIELDS = new CaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_ABSOLUTE_DATE_FIELDS.addAll(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY);

        DEFAULT_RELATIVE_DATE_FIELDS = new CaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(DEFAULT_ABSOLUTE_DATE_FIELD_NAMES_ARRAY);
        DEFAULT_RELATIVE_DATE_FIELDS.addAll(DEFAULT_RELATIVE_DATE_FIELD_NAMES_ARRAY);

        DEFAULT_VISIT_FIELDS = new CaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_VISIT_FIELDS.addAll(DEFAULT_VISIT_FIELD_NAMES_ARRAY);
    }

    public DataSetDefinition()
    {
    }


    public DataSetDefinition(StudyImpl study, int dataSetId, String name, String label, String category, String typeURI)
    {
        _study = study;
        setContainer(_study.getContainer());
        _dataSetId = dataSetId;
        _name = name;
        _label = label;
        _category = category;
        _typeURI = typeURI;
        _showByDefault = true;
    }


    public static boolean isDefaultFieldName(String fieldName, Study study)
    {
        String subjectCol = StudyService.get().getSubjectColumnName(study.getContainer());
        if (subjectCol.equalsIgnoreCase(fieldName))
            return true;
        
        switch (study.getTimepointType())
        {
            case VISIT:
                return DEFAULT_VISIT_FIELDS.contains(fieldName);
            case CONTINUOUS:
                return DEFAULT_ABSOLUTE_DATE_FIELDS.contains(fieldName);
            case DATE:
            default:
                return DEFAULT_RELATIVE_DATE_FIELDS.contains(fieldName);
        }
    }


    public static boolean showOnManageView(String fieldName, Study study)
    {
        return !HIDDEN_DEFAULT_FIELDS.contains(fieldName);
    }


    public Set<String> getDefaultFieldNames()
    {
        TimepointType timepointType = getStudy().getTimepointType();
        Set<String> fieldNames =
                timepointType == TimepointType.VISIT ? DEFAULT_VISIT_FIELDS :
                timepointType == TimepointType.CONTINUOUS ? DEFAULT_ABSOLUTE_DATE_FIELDS:
                DEFAULT_RELATIVE_DATE_FIELDS;

        return Collections.unmodifiableSet(fieldNames);
    }


    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getFileName()
    {
        if (null == _fileName)
        {
            NumberFormat dsf = new DecimalFormat("dataset000.tsv");

            return dsf.format(getDataSetId());
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

    public String getCategory()
    {
        return _category;
    }

    public void setCategory(String category)
    {
        verifyMutability();
        _category = category;
    }

    public int getDataSetId()
    {
        return _dataSetId;
    }

    public void setDataSetId(int dataSetId)
    {
        verifyMutability();
        _dataSetId = dataSetId;
    }

    public String getTypeURI()
    {
        return _typeURI;
    }

    public void setTypeURI(String typeURI)
    {
        verifyMutability();
        _typeURI = typeURI;
    }


    public String getPropertyURI(String column)
    {
        PropertyDescriptor pd = DataSetDefinition.getStandardPropertiesMap().get(column);
        if (null != pd)
            return pd.getPropertyURI();
        return _typeURI + "." + column;
    }


    public VisitDataSetType getVisitType(int visitRowId)
    {
        VisitDataSet vds = getVisitDataSet(visitRowId);
        if (vds == null)
            return VisitDataSetType.NOT_ASSOCIATED;
        else if (vds.isRequired())
            return VisitDataSetType.REQUIRED;
        else
            return VisitDataSetType.OPTIONAL;
    }


    public List<VisitDataSet> getVisitDataSets()
    {
        return Collections.unmodifiableList(StudyManager.getInstance().getMapping(this));
    }


    public VisitDataSet getVisitDataSet(int visitRowId)
    {
        List<VisitDataSet> dataSets = getVisitDataSets();
        for (VisitDataSet vds : dataSets)
        {
            if (vds.getVisitRowId() == visitRowId)
                return vds;
        }
        return null;
    }


    public int getRowId()
    {
        return getDataSetId();
    }

    public Object getPrimaryKey()
    {
        return getRowId();
    }


    public static TableInfo getTemplateTableInfo()
    {
        return StudySchema.getInstance().getSchema().getTable("studydatatemplate");
    }
    

    /**
     * Get table info representing dataset.  This relies on the DataSetDefinition being removed from
     * the cache if the dataset type changes.  The temptable version also relies on the dataset being
     * uncached when data is updated.
     *
     * see StudyManager.importDatasetTSV()
     */
    public TableInfo getTableInfo(User user) throws UnauthorizedException
    {
        return getTableInfo(user, true);
    }


    public TableInfo getTableInfo(User user, boolean checkPermission) throws UnauthorizedException
    {
        //noinspection ConstantConditions
        if (user == null && checkPermission)
            throw new IllegalArgumentException("user cannot be null");

        if (checkPermission && !canRead(user) && user != User.getSearchUser())
            HttpView.throwUnauthorized();

        return new DatasetSchemaTableInfo(this, user);
    }


    /** why do some datasets have a typeURI, but no domain? */
    private synchronized Domain ensureDomain()
    {
        if (null == getTypeURI())
            throw new IllegalStateException();
        Domain d = getDomain();
        if (null == d)
        {
            _domain = PropertyService.get().createDomain(getContainer(), getTypeURI(), getName());
            try
            {
                _domain.save(null);
            }
            catch (ChangePropertyDescriptorException x)
            {
                throw new RuntimeException(x);
            }
        }
        return _domain;
    }


    private synchronized TableInfo loadStorageTableInfo()
    {
        if (null == getTypeURI())
            return null;

        Domain d = ensureDomain();
        DomainKind kind = getDomainKind();

        // create table may set storageTableName() so uncache _domain
        if (null == d.getStorageTableName())
            _domain = null;

        TableInfo ti = StorageProvisioner.createTableInfo(kind, d, StudySchema.getInstance().getSchema());

        TableInfo template = getTemplateTableInfo();

        for (PropertyStorageSpec pss : kind.getBaseProperties())
        {
            ColumnInfo c = ti.getColumn(pss.getName());
            ColumnInfo t = template.getColumn(pss.getName());
            if (null != t)
                c.setExtraAttributesFrom(t);
        }

        return ti;
    }


    /**
     *  just a wrapper for StorageProvisioner.create()
     */
    public synchronized void provisionTable()
    {
        _domain = null;
        loadStorageTableInfo();
        StudyManager.getInstance().uncache(this);
    }


    private TableInfo _storageTable = null;
    

    /** I think the caching semantics of the dataset are such that I can cache the StorageTableInfo in a member */
    public TableInfo getStorageTableInfo() throws UnauthorizedException
    {
        if (null == _storageTable)
            _storageTable = loadStorageTableInfo();
        return _storageTable;
    }


    public int deleteRows(User user, Date cutoff)
    {
        assert StudySchema.getInstance().getSchema().getScope().isTransactionActive();
        int count;

        TableInfo table = getStorageTableInfo();

        try
        {
            CPUTimer time = new CPUTimer("purge");
            time.start();

            SQLFragment studyDataFrag = new SQLFragment("DELETE FROM " + table + "\n");
            if (cutoff != null)
                studyDataFrag.append(" AND _VisitDate > ?").add(cutoff);
            count = Table.execute(StudySchema.getInstance().getSchema(), studyDataFrag);

            time.stop();
            _log.debug("purgeDataset " + getDisplayString() + " " + DateUtil.formatDuration(time.getTotal()/1000));
        }
        catch (SQLException s)
        {
            String sqlState = StringUtils.defaultString("");
            if ("42P01".equals(sqlState) || "42S02".equals(sqlState)) // UNDEFINED TABLE
                return 0;
            throw new RuntimeSQLException(s);
        }
        finally
        {
            StudyManager.fireDataSetChanged(this);
        }
        return count;
    }


    public boolean isDemographicData()
    {
        return _demographicData;
    }


    public void setDemographicData(boolean demographicData)
    {
        _demographicData = demographicData;
    }


    public StudyImpl getStudy()
    {
        if (null == _study)
            _study = StudyManager.getInstance().getStudy(getContainer());
        return _study;
    }


    public boolean canRead(User user)
    {
        //if the study security type is basic read or basic write, use the container's policy instead of the
        //study's policy. This will enable us to "remember" the study-level role assignments in case we want
        //to switch back to them in the future
        SecurityType securityType = getStudy().getSecurityType();
        SecurityPolicy studyPolicy = (securityType == SecurityType.BASIC_READ || securityType == SecurityType.BASIC_WRITE) ?
                SecurityManager.getPolicy(getContainer()) : SecurityManager.getPolicy(getStudy());


        //need to check both the study's policy and the dataset's policy
        //users that have read permission on the study can read all datasets
        //users that have read-some permission on the study must also have read permission on this dataset
        return studyPolicy.hasPermission(user, ReadPermission.class) ||
                (studyPolicy.hasPermission(user, ReadSomePermission.class) && SecurityManager.getPolicy(this).hasPermission(user, ReadPermission.class));
    }

    public boolean canWrite(User user)
    {
        if (!canRead(user))
            return false;

        if (!getStudy().getContainer().getPolicy().hasPermission(user, UpdatePermission.class))
            return false;

        SecurityType securityType = getStudy().getSecurityType();

        if (securityType == SecurityType.BASIC_READ || securityType == SecurityType.ADVANCED_READ)
            return false; // Dataset rows are not editable

        if (securityType == SecurityType.BASIC_WRITE)
        {
            return true;
        }

        return SecurityManager.getPolicy(getStudy()).hasPermission(user, UpdatePermission.class) ||
            SecurityManager.getPolicy(this).hasPermission(user, UpdatePermission.class);
    }


    /** most external users should use this */
    public String getVisitDateColumnName()
    {
        if (null == _visitDatePropertyName && getStudy().getTimepointType() != TimepointType.VISIT)
            _visitDatePropertyName = "Date"; //Todo: Allow alternate names
        return _visitDatePropertyName;
    }


    public String getVisitDatePropertyName()
    {
        return _visitDatePropertyName;
    }


    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        _visitDatePropertyName = visitDatePropertyName;
    }

    
    /**
     * Returns the key names for display purposes.
     * If demographic data, visit keys are suppressed
     */
    public String[] getDisplayKeyNames()
    {
        List<String> keyNames = new ArrayList<String>();
        keyNames.add(StudyService.get().getSubjectColumnName(getContainer()));
        if (!isDemographicData())
        {
            keyNames.add(getStudy().getTimepointType() == TimepointType.VISIT ? "SequenceNum" : "Date");
        }
        if (getKeyPropertyName() != null)
            keyNames.add(getKeyPropertyName());

        return keyNames.toArray(new String[keyNames.size()]);
    }

    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        _keyPropertyName = keyPropertyName;
    }

    public void setKeyManagementType(@NotNull KeyManagementType type)
    {
        _keyManagementType = type;
    }

    @NotNull
    public KeyManagementType getKeyManagementType()
    {
        return _keyManagementType;
    }

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

    private static class AutoCompleteDisplayColumnFactory implements DisplayColumnFactory
    {
        private String _completionBase;

        public AutoCompleteDisplayColumnFactory(Container studyContainer, SpecimenService.CompletionType type)
        {
            _completionBase = SpecimenService.get().getCompletionURLBase(studyContainer, type);
        }

        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            return new DataColumn(colInfo)
            {
                @Override
                protected String getAutoCompleteURLPrefix()
                {
                    return _completionBase;
                }
            };
        }
    }


    /**
     * NOTE the constructor takes a USER in order that some lookup columns can be property
     * verified/constructed
     *
     * CONSIDER: we could use a way to delay permission checking and final schema construction for lookups
     * so that this object can be cached...
     */

    public static class DatasetSchemaTableInfo extends SchemaTableInfo
    {
        private Container _container;
        ColumnInfo _ptid;

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


        DatasetSchemaTableInfo(DataSetDefinition def, final User user)
        {
            super(def.getLabel(), StudySchema.getInstance().getSchema());
            _container = def.getContainer();
            Study study = StudyManager.getInstance().getStudy(_container);

            _storage = def.getStorageTableInfo();
            this._template = getTemplateTableInfo();
            
            // PartipantId

            {
            // StudyData columns
            // NOTE (MAB): I think it was probably wrong to alias participantid to subjectname here
            // That probably should have been done only in the StudyQuerySchema
            // CONSIDER: remove this aliased column
            ColumnInfo ptidCol = getStorageColumn("ParticipantId");
            ColumnInfo wrapped = newDatasetColumnInfo(this, ptidCol, getParticipantIdURI());
            wrapped.setName("ParticipantId");
            String subject = StudyService.get().getSubjectColumnName(_container);
            if ("ParticipantId".equalsIgnoreCase(subject))
                _ptid = wrapped;
            else
                _ptid = new AliasedColumn(this, subject, wrapped);
            columns.add(_ptid);
            }

            // base columns

            for (String name : Arrays.asList("lsid","ParticipantSequenceKey","sourcelsid","Created","CreatedBy","Modified","ModifiedBy"))
            {
                ColumnInfo col = getStorageColumn(name);
                if (null == col) continue;
                ColumnInfo wrapped = newDatasetColumnInfo(this, col, uriForName(col.getName()));
                wrapped.setName(name);
                wrapped.setUserEditable(false);
                columns.add(wrapped);
            }

            // SequenceNum

            ColumnInfo sequenceNumCol = newDatasetColumnInfo(this, getStorageColumn("SequenceNum"), getSequenceNumURI());
            sequenceNumCol.setName("SequenceNum");
            sequenceNumCol.setDisplayColumnFactory(new AutoCompleteDisplayColumnFactory(_container, SpecimenService.CompletionType.VisitId));
            sequenceNumCol.setMeasure(false);
            if (def.isDemographicData())
            {
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }
            if (study.getTimepointType() != TimepointType.VISIT)
            {
                sequenceNumCol.setNullable(true);
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }
            columns.add(sequenceNumCol);

            // Date

            if (study.getTimepointType() != TimepointType.VISIT)
            {
                ColumnInfo visitDateCol = newDatasetColumnInfo(this, getStorageColumn("Date"), getVisitDateURI());
                visitDateCol.setNullable(false);
                columns.add(visitDateCol);
            }

            // QCState

            ColumnInfo qcStateCol = newDatasetColumnInfo(this, getStorageColumn(DataSetTable.QCSTATE_ID_COLNAME), getQCStateURI());
            // UNDONE: make the QC column user editable.  This is turned off for now because DatasetSchemaTableInfo is not
            // a FilteredTable, so it doesn't know how to restrict QC options to those in the current container.
            // Note that QC state can still be modified via the standard update UI.
            qcStateCol.setUserEditable(false);
            columns.add(qcStateCol);

            // Property columns (see OntologyManager.getColumnsForType())

            Domain d = def.getDomain();
            DomainProperty[] properties = null==d ? new DomainProperty[0] : d.getProperties();
            for (DomainProperty p : properties)
            {
                ColumnInfo col = getStorageColumn(d, p);
                if (col == null)
                {
                    _log.error("didn't find column for property: " + p.getPropertyURI());
                    continue;
                }
                ColumnInfo wrapped = newDatasetColumnInfo(user, this, col, p.getPropertyDescriptor());
                columns.add(wrapped);

                // Set the FK if the property descriptor is configured as a lookup. DatasetSchemaTableInfos aren't
                // cached, so it's safe to include the current user 
                PropertyDescriptor pd = p.getPropertyDescriptor();
                if (null != pd && pd.getLookupQuery() != null)
                    wrapped.setFk(new PdLookupForeignKey(user, pd));

                if (p.isMvEnabled())
                {
                    ColumnInfo mvColumn = new ColumnInfo(wrapped.getName() + MvColumn.MV_INDICATOR_SUFFIX, this);
                    // MV indicators are strings
                    mvColumn.setSqlTypeName("VARCHAR");
                    mvColumn.setPropertyURI(wrapped.getPropertyURI());
                    mvColumn.setAlias(col.getAlias() + "_" + MvColumn.MV_INDICATOR_SUFFIX);
                    mvColumn.setNullable(true);
                    mvColumn.setUserEditable(false);
                    mvColumn.setHidden(true);
                    mvColumn.setMvIndicatorColumn(true);

                    ColumnInfo rawValueCol = new AliasedColumn(wrapped.getName() + RawValueColumn.RAW_VALUE_SUFFIX, wrapped);
                    rawValueCol.setLabel(getName());
                    rawValueCol.setUserEditable(false);
                    rawValueCol.setHidden(true);
                    rawValueCol.setMvColumnName(null); // This column itself does not allow QC
                    rawValueCol.setNullable(true); // Otherwise we get complaints on import for required fields

                    columns.add(mvColumn);
                    columns.add(rawValueCol);

                    wrapped.setDisplayColumnFactory(new MVDisplayColumnFactory());
                    wrapped.setMvColumnName(mvColumn.getName());
                }
            }

            // If we have an extra key, and it's server-managed, make it non-editable
            if (def.getKeyManagementType() != KeyManagementType.None)
            {
                for (ColumnInfo col : columns)
                {
                    if (col.getName().equals(def.getKeyPropertyName()))
                    {
                        col.setUserEditable(false);
                    }
                }
            }

            // Dataset

            ColumnInfo datasetColumn = new ExprColumn(this, "Dataset", new SQLFragment("CAST('" + def.getEntityId() + "' AS " + getSqlDialect().getGuidType() + ")"), Types.VARCHAR);
            LookupForeignKey datasetFk = new LookupForeignKey("entityid")
            {
                public TableInfo getLookupTableInfo()
                {
                    StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), user, true);
                    return schema.getTable("Datasets");
                }
            };
            datasetColumn.setFk(datasetFk);
            datasetColumn.setUserEditable(false);
            datasetColumn.setHidden(true);
            datasetColumn.setDimension(false);
            columns.add(datasetColumn);

            // reset colMap
            colMap = null;
            setPkColumnNames(Arrays.asList("LSID"));
        }


        public ColumnInfo getParticipantColumn()
        {
            return _ptid;
        }


        @Override
        public ColumnInfo getColumn(String name)
        {
            if ("ParticipantId".equalsIgnoreCase(name))
                return getParticipantColumn();
            return super.getColumn(name);
        }


        @Override
        public ButtonBarConfig getButtonBarConfig()
        {
            // Check first to see if this table has explicit button configuration.  This currently will
            // never be the case, since dataset tableinfo's don't have a place to declare button config,
            // but future changes to enable hard dataset tables may enable this.
            ButtonBarConfig config = super.getButtonBarConfig();
            if (config != null)
                return config;

//            // If no button config was found for this dataset, fall back to the button config on StudyData.  This
//            // lets users configure buttons that should appear on all datasets.
//            StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), _user, true);
//            try
//            {
//                TableInfo studyData = schema.getTable(StudyQuerySchema.STUDY_DATA_TABLE_NAME);
//                return studyData.getButtonBarConfig();
//            }
//            catch (UnauthorizedException e)
//            {
//                return null;
//            }
            return null;
        }


        @Override
        public String getSelectName()
        {
            return null;
        }

        @Override
        @NotNull
        public SQLFragment getFromSQL(String alias)
        {
            if (null == _storage)
            {
                SqlDialect d = getSqlDialect();
                SQLFragment from = new SQLFragment();
                from.appendComment("<DataSetDefinition: " + getName() + ">", d); // UNDONE stash name
                String comma = " ";
                from.append("(SELECT ");
                for (ColumnInfo ci : _template.getColumns())
                {
                    from.append(comma).append(NullColumnInfo.nullValue(ci.getSqlTypeName())).append(" AS ").append(ci.getName());
                    comma = ", ";
                }
                from.append("\nWHERE 0=1) AS ").append(alias);
                from.appendComment("</DataSetDefinition>", d);
                return from;
            }
            else
            {
                return _storage.getFromSQL(alias);
            }
        }
    }


    static ColumnInfo newDatasetColumnInfo(TableInfo tinfo, ColumnInfo from, final String propertyURI)
    {
        ColumnInfo result = new ColumnInfo(from, tinfo)
        {
            @Override
            public String getPropertyURI()
            {
                return null != propertyURI ? propertyURI : super.getPropertyURI();
            }
        };
        // Hidden doesn't get copied with the default set of properties
        result.setHidden(from.isHidden());
        return result;
    }

    
    static ColumnInfo newDatasetColumnInfo(User user, TableInfo tinfo, ColumnInfo from, PropertyDescriptor p)
    {
        ColumnInfo ci = newDatasetColumnInfo(tinfo, from, p.getPropertyURI());
        // We are currently assuming the db column name is the same as the propertyname
        // I want to know if that changes
        assert ci.getName().equalsIgnoreCase(p.getName());
        ci.setName(p.getName());
        ci.setAlias(from.getAlias());
        return ci;
    }


    private static final Set<PropertyDescriptor> standardPropertySet = new HashSet<PropertyDescriptor>();
    private static final Map<String, PropertyDescriptor> standardPropertyMap = new CaseInsensitiveHashMap<PropertyDescriptor>();

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
                    PropertyType type = PropertyType.getFromClass(col.getJavaObjectClass());
                    PropertyDescriptor pd = new PropertyDescriptor(
                            propertyURI, type.getTypeUri(), name, ContainerManager.getSharedContainer());
                    standardPropertySet.add(pd);
                }
            }
            return standardPropertySet;
        }
    }


    Domain _domain = null;

    public synchronized Domain getDomain()
    {
        if (null == _domain)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getTypeURI());
        }
        return _domain;
    }


    public DomainKind getDomainKind()
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


    private static String uriForName(String name)
    {
        final String StudyURI = "http://cpas.labkey.com/Study#";
        assert getStandardPropertiesMap().get(name).getPropertyURI().equalsIgnoreCase(StudyURI + name);
        return getStandardPropertiesMap().get(name).getPropertyURI();
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
        return uriForName(DataSetTable.QCSTATE_ID_COLNAME);
    }


    @Override
    protected boolean supportsPolicyUpdate()
    {
        return true;
    }

    public Integer getCohortId()
    {
        return _cohortId;
    }

    public void setCohortId(Integer cohortId)
    {
        _cohortId = cohortId;
    }

    @Nullable
    public CohortImpl getCohort()
    {
        if (_cohortId == null)
            return null;
        return Table.selectObject(StudySchema.getInstance().getTableInfoCohort(), _cohortId, CohortImpl.class);
    }

    public Integer getProtocolId()
    {
        return _protocolId;
    }

    public void setProtocolId(Integer protocolId)
    {
        _protocolId = protocolId;
    }

    @Override
    public String toString()
    {
        return "DataSetDefinition: " + getLabel() + " " + getDataSetId();
    }

    public void deleteRows(User user, Collection<String> rowLSIDs)
    {
        Container c = getContainer();

        TableInfo data = getStorageTableInfo();
        SimpleFilter filter = new SimpleFilter();
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
                scope.commitTransaction();

            StudyManager.fireDataSetChanged(this);
        }
        catch (SQLException s)
        {
            throw new RuntimeSQLException(s);
        }
        finally
        {
            if (startTransaction)
                scope.closeConnection();
        }
    }


    /**
     * dataMaps have keys which are property URIs, and values which have already been converted.
     */
    public List<String> importDatasetData(Study study, User user, List<Map<String, Object>> dataMaps, long lastModified, List<String> errors, boolean checkDuplicates, boolean ensureObjects, QCState defaultQCState, Logger logger)
            throws SQLException
    {
        if (dataMaps.size() == 0)
            return Collections.emptyList();

        TableInfo tinfo = getTableInfo(user, false);
        Map<String, QCState> qcStateLabels = new CaseInsensitiveHashMap<QCState>();

        boolean needToHandleQCState = tinfo.getColumn(DataSetTable.QCSTATE_ID_COLNAME) != null;

        if (needToHandleQCState)
        {
            for (QCState state : StudyManager.getInstance().getQCStates(study.getContainer()))
                qcStateLabels.put(state.getLabel(), state);
        }

        //
        // Try to collect errors early.
        // Try not to be too repetitive, stop each loop after one error
        //

        // In certain cases (e.g., QC Columns), we have multiple columns with the same
        // property URI. We don't want to complain about conversion errors multiple
        // times, so we keep a set around in case we run into one and only report it once.
        MultiMap<Integer, String> rowToConversionErrorURIs = new MultiHashMap<Integer, String>();

        int rowNumber = 0;
        for (Map<String, Object> dataMap : dataMaps)
        {
            rowNumber++;
            if (needToHandleQCState)
            {
                String qcStateLabel = (String) dataMap.get(DataSetTable.QCSTATE_LABEL_COLNAME);
                // We have a non-null QC state column value.  We need to check to see if this is a known state,
                // and mark it for addition if not.
                if (qcStateLabel != null && qcStateLabel.length() > 0 && !qcStateLabels.containsKey(qcStateLabel))
                    qcStateLabels.put(qcStateLabel, null);
            }

            for (ColumnInfo col : tinfo.getColumns())
            {
                // lsid is generated
                if (col.getName().equalsIgnoreCase("lsid"))
                    continue;

                Object val = dataMap.get(col.getPropertyURI());

                boolean valueMissing;

                if (val == null)
                {
                    valueMissing = true;
                }
                else if (val instanceof MvFieldWrapper)
                {
                    MvFieldWrapper mvWrapper = (MvFieldWrapper)val;

                    if (mvWrapper.isEmpty())
                    {
                        valueMissing = true;
                    }
                    else
                    {
                        valueMissing = false;

                        if (col.isMvEnabled() && !MvUtil.isValidMvIndicator(mvWrapper.getMvIndicator(), getContainer()))
                        {
                            String columnName = col.getName() + MvColumn.MV_INDICATOR_SUFFIX;
                            errors.add(columnName + " must be a valid MV indicator.");
                            break;
                        }
                    }
                }
                else
                {
                    valueMissing = false;
                }

                if (valueMissing && !col.isNullable() && col.isUserEditable())
                {
                    // Demographic data gets special handling for visit or date fields, depending on the type of study,
                    // since there is usually only one entry for demographic data per dataset
                    if (isDemographicData())
                    {
                        if (study.getTimepointType() != TimepointType.VISIT)
                        {
                            if (col.getName().equalsIgnoreCase("Date"))
                            {
                                dataMap.put(col.getPropertyURI(), study.getStartDate());
                                continue;
                            }
                        }
                        else
                        {
                            if (col.getName().equalsIgnoreCase("SequenceNum"))
                            {
                                dataMap.put(col.getPropertyURI(), 0);
                                continue;
                            }
                        }
                    }

                    errors.add("Row " + rowNumber + " does not contain required field " + col.getName() + ".");
                }
                else if (val == StudyManager.CONVERSION_ERROR)
                {
                    if (!rowToConversionErrorURIs.containsValue(rowNumber - 1, col.getPropertyURI()))
                    {
                        // Only emit the error once for a given property uri and row
                        errors.add("Row " + rowNumber + " data type error for field " + col.getName() + "."); // + " '" + String.valueOf(val) + "'.");
                        rowToConversionErrorURIs.put(rowNumber - 1, col.getPropertyURI());
                    }
                }
            }

            if (errors.size() > 0)
                return Collections.emptyList();
        }
        if (logger != null) logger.debug("checked for missing values");

        String keyPropertyURI = null;
        String keyPropertyName = getKeyPropertyName();

        if (keyPropertyName != null)
        {
            ColumnInfo col = tinfo.getColumn(keyPropertyName);
            if (null != col)
                keyPropertyURI = col.getPropertyURI();
        }

        if (checkDuplicates)
        {
            checkForDuplicates(study, user, dataMaps, errors, logger, keyPropertyURI, keyPropertyName);
        }
        if (errors.size() > 0)
            return Collections.emptyList();

        if (getKeyManagementType() == KeyManagementType.RowId)
        {
            // If additional keys are managed by the server, we need to synchronize around
            // increments, as we're imitating a sequence.
            synchronized (MANAGED_KEY_LOCK)
            {
                return insertData(study, user, dataMaps, lastModified, errors, ensureObjects, defaultQCState, logger, qcStateLabels, needToHandleQCState, keyPropertyURI);
            }
        }
        else
        {
            return insertData(study, user, dataMaps, lastModified, errors, ensureObjects, defaultQCState, logger, qcStateLabels, needToHandleQCState, keyPropertyURI);
        }
    }

    private void checkForDuplicates(Study study, User user, List<Map<String, Object>> dataMaps, List<String> errors, Logger logger, String keyPropertyURI, String keyPropertyName)
            throws SQLException
    {
        String participantIdURI = DataSetDefinition.getParticipantIdURI();
        String visitSequenceNumURI = DataSetDefinition.getSequenceNumURI();
        String visitDateURI = DataSetDefinition.getVisitDateURI();
        HashMap<String, Map> failedReplaceMap = checkAndDeleteDupes(user, study, dataMaps);

        if (null != failedReplaceMap && failedReplaceMap.size() > 0)
        {
            StringBuilder error = new StringBuilder();
            error.append("Only one row is allowed for each ").append(StudyService.get().getSubjectNounSingular(getContainer()));

            if (!isDemographicData())
            {
                error.append(study.getTimepointType() != TimepointType.DATE ? "/Date" : "/Visit");

                if (getKeyPropertyName() != null)
                    error.append("/").append(getKeyPropertyName()).append(" Triple.  ");
                else
                    error.append(" Pair.  ");
            }
            else if (getKeyPropertyName() != null)
            {
                error.append("/").append(getKeyPropertyName()).append(" Pair.  ");
            }

            error.append("Duplicates were found in the database or imported data.");
            errors.add(error.toString());

            for (Map.Entry<String, Map> e : failedReplaceMap.entrySet())
            {
                Map m = e.getValue();
                String err = "Duplicate: " + StudyService.get().getSubjectNounSingular(getContainer()) + " = " + m.get(participantIdURI);
                if (!isDemographicData())
                {
                    if (study.getTimepointType() != TimepointType.VISIT)
                        err = err + "Date = " + m.get(visitDateURI);
                    else
                        err = err + ", VisitSequenceNum = " + m.get(visitSequenceNumURI);
                }
                if (keyPropertyURI != null)
                    err += ", " + keyPropertyName + " = " + m.get(keyPropertyURI);
                errors.add(err);
            }
        }
        if (logger != null) logger.debug("checked for duplicates");
    }

    private List<String> insertData(Study study, User user, List<Map<String, Object>> dataMaps, long lastModified, List<String> errors, boolean ensureObjects, QCState defaultQCState, Logger logger, Map<String, QCState> qcStateLabels, boolean needToHandleQCState, String keyPropertyURI)
            throws SQLException
    {
        DbScope scope = ExperimentService.get().getSchema().getScope();
        boolean startedTransaction = false;

        try
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
                        newState = StudyManager.getInstance().insertQCState(user, newState);
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
                if (logger != null) logger.debug("handled qc state");
            }

            //
            // Use OntologyManager for bulk insert
            //
            // CONSIDER: it would be nice if we could use the Table/TableInfo methods here

            // Need to generate keys if the server manages them
            if (getKeyManagementType() == KeyManagementType.RowId)
            {
                int currentKey = getMaxKeyValue();

                for (int i = 0; i < dataMaps.size(); i++)
                {
                    Map<String, Object> dataMap = dataMaps.get(i);
                    // Only insert if there isn't already a value
                    if (dataMap.get(keyPropertyURI) == null)
                    {
                        currentKey++;
                        // Create a new map because RowMaps don't work correctly doing put() in all scenarios.
                        // TODO - once the RowMap implementation is fixed, remove this extra map creation
                        dataMap = new HashMap<String, Object>(dataMap);
                        dataMap.put(keyPropertyURI, currentKey);
                        dataMaps.set(i, dataMap);
                    }
                }
                if (logger != null) logger.debug("generated keys");
            }
            else if (getKeyManagementType() == KeyManagementType.GUID)
            {
                for (int i = 0; i < dataMaps.size(); i++)
                {
                    Map<String, Object> dataMap = dataMaps.get(i);
                    // Only insert if there isn't already a value
                    if (dataMap.get(keyPropertyURI) == null)
                    {
                        // Create a new map because RowMaps don't work correctly doing put() in all scenarios.
                        // TODO - once the RowMap implementation is fixed, remove this extra map creation
                        dataMap = new HashMap<String, Object>(dataMap);
                        dataMap.put(keyPropertyURI, GUID.makeGUID());
                        dataMaps.set(i, dataMap);
                    }
                }
                if (logger != null) logger.debug("generated keys");
            }

            long start = System.currentTimeMillis();
            List<String> imported = _insertPropertyMaps(study, user, dataMaps, lastModified, ensureObjects, logger, scope);
            long end = System.currentTimeMillis();
            _log.info("imported " + getName() + " : " + DateUtil.formatDuration(end-start));

            if (startedTransaction)
            {
                if (logger != null) logger.debug("starting commit...");
                scope.commitTransaction();
                startedTransaction = false;
                if (logger != null) logger.debug("commit complete");
            }
            StudyManager.fireDataSetChanged(this);
            return imported;
        }
        catch (ValidationException ve)
        {
            for (ValidationError error : ve.getErrors())
                errors.add(error.getMessage());
            return Collections.emptyList();
        }
        finally
        {
            if (startedTransaction)
            {
                scope.closeConnection();
            }
        }
    }

    /** @return the LSID prefix to be used for this dataset's rows */
    public String getURNPrefix()
    {
        return "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":Study.Data-" + getContainer().getRowId() + ":" + getDataSetId() + ".";
    }

     /** @return a SQL expression that generates the LSID for a dataset row */
    public SQLFragment getLSIDSQL()
    {
        SQLFragment visitSQL;
        if (_study.getTimepointType() != TimepointType.VISIT)
        {
            visitSQL = StudyManager.sequenceNumFromDateSQL("date");
        }
        else
        {
            visitSQL = new SQLFragment("CAST (sequencenum AS VARCHAR)");
        }
        SQLFragment sql = StudyManager.getSchema().getSqlDialect().concatenate(
                new SQLFragment("?", getURNPrefix()),
                visitSQL,
                new SQLFragment("'.'"),
                new SQLFragment("participantid"));
        if (getKeyPropertyName() != null)
        {
            sql = StudyManager.getSchema().getSqlDialect().concatenate(
                    sql, 
                    new SQLFragment("'.'"),
                    new SQLFragment("\"" + getKeyPropertyName().toLowerCase() + "\""));
        }
        return sql;
    }

    /*
     * Actually persist rows to the database.  These maps are keyed by property URI.
     *
     * These maps have QC states and keys generated.  Should behave like OntologyManager.insertTabDelimited()
     *
     * TODO: should OntolgoyManager.insertTabDelimited handle this case too (share code with other materialized domains)
     *
     */
    private List<String> _insertPropertyMaps(Study study, User user, List<Map<String, Object>> dataMaps, long lastModified, boolean ensureObjects, Logger logger, DbScope scope)
            throws SQLException, ValidationException
    {
        TimepointType timetype = study.getTimepointType();
        NumberFormat sequenceFormat = new DecimalFormat("0.0000");
        assert ExperimentService.get().getSchema().getScope().isTransactionActive();

        List<String> imported = new ArrayList<String>(dataMaps.size());
        DatasetImportHelper helper = null;
        Connection conn = null;
        PreparedStatement stmt = null;

        try
        {
            conn = scope.getConnection();
            String typeURI = getTypeURI();
            Container c = study.getContainer();

            PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(typeURI, c);
            helper = new DatasetImportHelper(user, conn, c, this, lastModified);

            ValidatorContext validatorCache = new ValidatorContext(c, user);

            List<ValidationError> errors = new ArrayList<ValidationError>();
            Map<Integer, IPropertyValidator[]> validatorMap = new HashMap<Integer, IPropertyValidator[]>();

            // cache all the property validators for this upload
            for (PropertyDescriptor pd : pds)
            {
                IPropertyValidator[] validators = PropertyService.get().getPropertyValidators(pd);
                if (validators.length > 0)
                    validatorMap.put(pd.getPropertyId(), validators);
            }

            // UNDONE: custom INSERT VALUES and use with bulk operations
            // CONSIDER: or use QueryUpdateService for the dataset
            // This is just a temp fix to get some data in!
            // NOTE: we are assuming column names and property names match exactly

            Map<String,PropertyDescriptor> propertyNameMap = new CaseInsensitiveHashMap<PropertyDescriptor>(pds.length * 2);
            for (PropertyDescriptor pd : pds)
                propertyNameMap.put(pd.getName(), pd);
            for (PropertyDescriptor pd : pds)
                propertyNameMap.put(pd.getPropertyURI(), pd);
            propertyNameMap.put("ptid", propertyNameMap.get("participantid"));

            Map<PropertyDescriptor,PropertyType> propertyTypeMap = new IdentityHashMap<PropertyDescriptor, PropertyType>();
            for (PropertyDescriptor pd : pds)
                propertyTypeMap.put(pd, PropertyType.getFromURI(pd.getConceptURI(), pd.getRangeURI()));

            Pair<Object,String> valuePair = new Pair<Object,String>(null,null);

            TableInfo table = getStorageTableInfo();
            scope = table.getSchema().getScope();
            Parameter.ParameterMap parameterMap = Table.insertStatement(conn, user, table);
            stmt = parameterMap.getStatement();

            for (Map row : dataMaps)
            {
                if (Thread.currentThread().isInterrupted())
                    throw new CancellationException();

                parameterMap.clearParameters();
                
                for (Map.Entry<String,Object> e : (Set<Map.Entry<String,Object>>)row.entrySet())
                {
                    PropertyDescriptor pd = propertyNameMap.get(e.getKey());
                    if (null == pd) continue;
                    
                    PropertyType type = propertyTypeMap.get(pd);
                    valuePair.first = e.getValue();
                    valuePair.second = null;
                    if (null == valuePair.first && pd.isRequired())
                    {
                        throw new ValidationException("Missing value for required property " + pd.getName());
                    }
                    if (validatorMap.containsKey(pd.getPropertyId()))
                    {
                        OntologyManager.validateProperty(validatorMap.get(pd.getPropertyId()), pd, valuePair.first, errors, validatorCache);
                        if (!errors.isEmpty())
                            throw new ValidationException(errors);
                    }
                    try
                    {
                        String name = pd.getName();

                        OntologyManager.convertValuePair(pd, type, valuePair);

                        parameterMap.put(name, valuePair.first);
                        parameterMap.put(name + "_" + MvColumn.MV_INDICATOR_SUFFIX, valuePair.second);
                    }
                    catch (ConversionException x)
                    {
                        throw new ValidationException("Could not convert '" + e.getValue() + "' for field " + pd.getName() + ", should be of type " + type.getJavaType().getSimpleName());
                    }
                }

                String lsid = helper.getURI(row);
                String participantId = helper.getParticipantId(row);
                double sequenceNum = helper.getSequenceNum(row);
                Object key = helper.getKey(row);
                String participantSequenceKey = participantId + "|" + sequenceFormat.format(sequenceNum);
                Date visitDate = helper.getVisitDate(row);
                Integer qcState = helper.getQCState(row);
                String sourceLsid = helper.getSourceLsid(row);

                parameterMap.put("participantsequencekey", participantSequenceKey);
                parameterMap.put("participantid", participantId);
                parameterMap.put("sequencenum", sequenceNum);
                parameterMap.put("_key", key==null ? "" : String.valueOf(key));
                parameterMap.put("lsid", lsid);
                parameterMap.put("qcstate", qcState);
                parameterMap.put("sourcelsid", sourceLsid);
                if (timetype != TimepointType.VISIT)
                    parameterMap.put("date", visitDate);

                stmt.execute();

                imported.add(lsid);
                // UNDONE: OntologyManager.validateProperty
            }
        }
        finally
        {
            if (null != helper)
                helper.done();
            ResultSetUtil.close(stmt);
            if (null != conn)
                scope.releaseConnection(conn);
        }
        return imported;
    }


//    public void insertIntoMaterialized(User user, Collection<String> lsids)
//            throws SQLException
//    {
//        TableInfo tempTableInfo = getMaterializedTempTableInfo(user, false);
//        if (tempTableInfo != null)
//        {
//            // Update the materialized temp table if it's still around
//            SimpleFilter tempTableFilter = new SimpleFilter();
//            tempTableFilter.addInClause("LSID", lsids);
//            SQLFragment sqlSelect = Table.getSelectSQL(getTableInfo(user, false, false), null, null, null);
//            SQLFragment sqlSelectInto = new SQLFragment();
//            sqlSelectInto.append("INSERT INTO ").append(tempTableInfo).append(" SELECT * FROM (");
//            sqlSelectInto.append(sqlSelect);
//            sqlSelectInto.append(") x ");
//            sqlSelectInto.append(tempTableFilter.getSQLFragment(tempTableInfo.getSqlDialect()));
//
//            Table.execute(tempTableInfo.getSchema(), sqlSelectInto);
//        }
//    }

    /**
     * If all the dupes can be replaced, delete them. If not return the ones that should NOT be replaced
     * and do not delete anything
     */
    private HashMap<String, Map> checkAndDeleteDupes(User user, Study study, List<Map<String, Object>> rows) throws SQLException
    {
        if (null == rows || rows.size() == 0)
            return null;

        Container c = study.getContainer();
        DatasetImportHelper helper = new DatasetImportHelper(user, null, c, this, 0);

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

        TableInfo tinfo = getStorageTableInfo();
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
        if (noDeleteMap.size() > 0 && getKeyManagementType() == KeyManagementType.None)
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
        Table.delete(tinfo, deleteFilter);
//        OntologyManager.deleteOntologyObjects(c, deleteSet.toArray(new String[deleteSet.size()]));

        return null;
    }

    /**
     * Gets the current highest key value for a server-managed key field.
     * If no data is returned, this method returns 0.
     */
    private int getMaxKeyValue() throws SQLException
    {
        TableInfo tInfo = getStorageTableInfo();
        Integer newKey = Table.executeSingleton(tInfo.getSchema(),
                "SELECT COALESCE(MAX(CAST(_key AS INTEGER)), 0) FROM " + tInfo,
                null,
                Integer.class
                );
        return newKey.intValue();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DataSetDefinition that = (DataSetDefinition) o;

        if (_dataSetId != that._dataSetId) return false;
        // The _study member variable is populated lazily in the getter,
        // so go through the getter instead of relying on the variable to be populated
        if (getStudy() != null ? !getStudy().equals(that.getStudy()) : that.getStudy() != null) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _study != null ? _study.hashCode() : 0;
        result = 31 * result + _dataSetId;
        return result;
    }

    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("c(\\d+)_(.+)");

    public static void purgeOrphanedDatasets()
    {
        Connection conn = null;
        try
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            conn = scope.getConnection();
            ResultSet tablesRS = conn.getMetaData().getTables(scope.getDatabaseName(), StudySchema.getInstance().getDatasetSchemaName(), null, new String[]{"TABLE"});
            while (tablesRS.next())
            {
                String tableName = tablesRS.getString("TABLE_NAME");
                boolean delete = true;
                Matcher matcher = TABLE_NAME_PATTERN.matcher(tableName);
                if (matcher.matches())
                {
                    int containerRowId = Integer.parseInt(matcher.group(1));
                    String datasetName = matcher.group(2);
                    Container c = ContainerManager.getForRowId(containerRowId);
                    if (c != null)
                    {
                        StudyImpl study = StudyManager.getInstance().getStudy(c);
                        if (study != null)
                        {
                            for (DataSetDefinition dataset : study.getDataSets())
                            {
                                if (dataset.getName().equalsIgnoreCase(datasetName))
                                {
                                    delete = false;
                                }
                            }
                        }
                    }
                }
                if (delete)
                {
                    Statement statement = null;
                    try
                    {
                        statement = conn.createStatement();
                        statement.execute("DROP TABLE " + StudySchema.getInstance().getDatasetSchemaName() + "." + scope.getSqlDialect().makeLegalIdentifier(tableName));
                    }
                    finally
                    {
                        if (statement != null) { try { statement.close(); } catch (SQLException e) {} }
                    }
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (conn != null)
            {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }



    private static class DataSetDefObjectFactory extends BeanObjectFactory
    {
        DataSetDefObjectFactory()
        {
            super(DataSetDefinition.class);
            boolean found;
            found = _readableProperties.remove("storageTableInfo");
            assert found;
            found = _readableProperties.remove("domain");
            assert found;
        }
    }

    static
    {
        ObjectFactory.Registry.register(DataSetDefinition.class, new DataSetDefObjectFactory());        
    }

    /** UPGRADE
     *
     * upgrade happens in steps to maximize the chance that either no data is deleted unless the entire upgrade works
     *
     * 1) provision all tables
     * 2) copy all data into provisioned tables
     * 3) delete data from OntologyManager
     * 
     ****/

    public static void upgradeAll() throws SQLException
    {
        // find all containers with datasets
        List<Container> allContainers = new ArrayList<Container>();
        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), new SQLFragment("SELECT DISTINCT container FROM study.dataset"));
        while (rs.next())
        {
            String id = rs.getString(1);
            Container c = ContainerManager.getForId(id);
            if (null == c)
            {
                ModuleUpgrader.getLogger().error("Found orphaned dataset with containerid=" + id);
                continue;
            }
            allContainers.add(c);
        }
        ResultSetUtil.close(rs);

        // find all datasets
        List<DataSetDefinition> defs = new ArrayList<DataSetDefinition>();
        for (Container c : allContainers)
        {
            Study study = StudyManager.getInstance().getStudy(c);
            DataSetDefinition[] arr = StudyManager.getInstance().getDataSetDefinitions(study);
            if (null == arr) continue;
            defs.addAll(Arrays.asList(arr));
        }

        List<DataSetDefinition> defsVerify = new ArrayList<DataSetDefinition>();
        ModuleUpgrader.getLogger().info("STUDY UPGRADE drop old materialized tables");
        for (DataSetDefinition def : defs)
        {
            defsVerify.add(def.upgradeVerifyDomain());
        }
        defs = defsVerify;

        ModuleUpgrader.getLogger().info("STUDY UPGRADE drop old materialized tables");
        for (DataSetDefinition def : defs)
            def.dropOldMaterializedTable();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE create new tables");
        for (DataSetDefinition def : defs)
            def.upgradeProvision();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE copy data");
        for (DataSetDefinition def : defs)
            def.upgradeCopy();

//        CPUTimer.dumpAllTimers(ModuleUpgrader.getLogger());
        ModuleUpgrader.getLogger().info("STUDY UPGRADE resuming SQL script");
        // delete exp.objectproperty is in the upgrade script
    }


    private DataSetDefinition upgradeVerifyDomain() throws SQLException
    {
        String typeURI = getTypeURI();
        if (null != typeURI)
        {
            OntologyManager.ensureDomainDescriptor(typeURI, getName(), getContainer());
            return this;
        }
        else
        {
            DataSetDefinition def = this.createMutable();
            String domainURI = StudyManager.getInstance().getDomainURI(getContainer(), null, def);
            OntologyManager.ensureDomainDescriptor(domainURI, def.getName(), getContainer());
            def.setTypeURI(domainURI);
            StudyManager.getInstance().updateDataSetDefinition(null, def);
            return StudyManager.getInstance().getDataSetDefinition(getStudy(), getDataSetId());
        }
    }
    

    private void upgradeProvision() throws SQLException
    {
        if (null == getTypeURI())
            return;
        provisionTable();
    }


    private void upgradeCopy() throws SQLException
    {
        TableInfo fromTable = new StudyDataTableInfoUpgrade(this);
        TableInfo toTable = getStorageTableInfo();

        if (null == toTable)
        {
            throw new IllegalStateException("Unprovisioned dataset: " + getName());
        }

        Map<String,ColumnInfo> colMap = new CaseInsensitiveHashMap<ColumnInfo>();
        for (ColumnInfo c : fromTable.getColumns())
        {
            if (null != c.getPropertyURI())
                colMap.put(c.getPropertyURI(), c);
            colMap.put(c.getName(), c);
        }

        SQLFragment insertInto = new SQLFragment("INSERT INTO " + toTable.getSelectName() + " (");
        SQLFragment select = new SQLFragment("SELECT " );
        Map<String,SQLFragment> joinMap = new HashMap<String,SQLFragment>();
        String comma = "";
        for (ColumnInfo to : toTable.getColumns())
        {
            ColumnInfo from = colMap.get(to.getPropertyURI());
            if (null == from)
                from = colMap.get(to.getName());
            if (null == from)
            {
                String name = to.getName().toLowerCase();
                if ("modifiedby".equals(name) || "createdby".equals(name))
                {
                    continue;
                }
                else if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
                {
                    from = colMap.get(name.substring(0,name.length()-(MvColumn.MV_INDICATOR_SUFFIX.length()+1)) + MvColumn.MV_INDICATOR_SUFFIX);
                    if (null == from)
                        continue;
                }
                else
                {
                    ModuleUpgrader.getLogger().error("Could not copy column: " + getContainer().getId()+"-"+getContainer().getPath() + " " + getDataSetId() + "-" + getName() + " " + to.getName());
                    continue;
                }
            }
            insertInto.append(comma).append(to.getSelectName());
            select.append(comma).append(from.getValueSql("SD"));
            from.declareJoins("SD", joinMap);
            comma = ", ";
        }
        insertInto.append(")\n");
        insertInto.append(select);
        insertInto.append("\n FROM ").append(fromTable.getFromSQL("SD"));
        for (SQLFragment j : joinMap.values())
            insertInto.append(j);

        ModuleUpgrader.getLogger().info("Migrating data for [" + getContainer().getPath() + "]  '" + getName() + "'");
        ModuleUpgrader.getLogger().info(insertInto.toString());
        Table.execute(StudySchema.getInstance().getSchema(), insertInto);
    }


    @Deprecated
    private static class StudyDataTableInfoUpgrade extends SchemaTableInfo
    {
        private Container _container;
        int _datasetId;
        SQLFragment _fromSql;

        StudyDataTableInfoUpgrade(DataSetDefinition def)
        {
            super(def.getLabel(), StudySchema.getInstance().getSchema());
            _container = def.getContainer();
            _datasetId = def.getDataSetId();

            // getTableInfoStudyData is the new UNION version
            //TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData(study, user);
            TableInfo studyData = StudySchema.getInstance().getSchema().getTable("StudyData");

            List<ColumnInfo> columnsBase = studyData.getColumns("_key","lsid","participantid","ParticipantSequenceKey","sourcelsid", "created","modified","sequenceNum","qcstate","participantsequencekey");
            for (ColumnInfo col : columnsBase)
            {
                ColumnInfo wrapped = new ColumnInfo(col, this);
                columns.add(wrapped);
            }
            if (def.getStudy().getTimepointType() != TimepointType.VISIT)
            {
                ColumnInfo wrapped = new AliasedColumn(this, "Date", studyData.getColumn("_VisitDate"));
                columns.add(wrapped);
            }

            // Property columns
            ColumnInfo[] columnsLookup = OntologyManager.getColumnsForType(def.getTypeURI(), this, def.getContainer(), null);
            columns.addAll(Arrays.asList(columnsLookup));

            // HACK reset colMap
            colMap = null;

            _fromSql = new SQLFragment(
                    "SELECT *\n" +
                    "  FROM " + studyData.getSelectName() + "\n"+
                    "  WHERE container=? AND datasetid=?");
            _fromSql.add(_container);
            _fromSql.add(_datasetId);
        }

        @Override
        public String getSelectName()
        {
            return null;
        }


        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            return _fromSql;
        }
    }

    private void dropOldMaterializedTable()
    {
        DbSchema schema = StudySchema.getInstance().getSchema();
        Connection conn=null;
        try
        {
            // avoid logging sql exception
            //Table.execute(schema, "DROP TABLE studydataset." + schema.getScope().getSqlDialect().makeLegalIdentifier(getCacheString()), new Object[0]);
            conn = schema.getScope().getConnection();
            conn.prepareStatement("DROP TABLE studydataset." + schema.getScope().getSqlDialect().makeLegalIdentifier(getCacheString())).execute();
        }
        catch (SQLException x)
        {
            /* */
        }
        finally
        {
            schema.getScope().releaseConnection(conn);
        }
    }

    private String getCacheString()
    {
        return "c" + getContainer().getRowId() + "_" + getName().toLowerCase();
    }
}


/*** TODO
 [ ] verify synchronize/transact updates to domain/storage table
 [N] test column rename, name collisions
 [N] we seem to still be orphaning tables in the studydataset schema
 [ ] exp StudyDataSetColumn usage of getStudyDataTable()
 // FUTURE
 [ ] don't use subjectname alias at this level
 [ ] remove _Key columns
 [ ] make OntologyManager.insertTabDelimited could handle materialized domains (maybe two subclasses?)
 [ ] clean up architecture of import/queryupdateservice
 ***/