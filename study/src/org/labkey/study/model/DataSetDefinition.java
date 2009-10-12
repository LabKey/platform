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

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.Cache;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.study.StudySchema;
import org.labkey.study.query.DataSetTable;
import org.labkey.study.query.DataSetsTable;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.sql.Types;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:29:31 AM
 */
public class DataSetDefinition extends AbstractStudyEntity<DataSetDefinition> implements Cloneable, DataSet
{
    // standard string to use in URLs etc.
    public static final String DATASETKEY = "datasetId";
    private static Category _log = Logger.getInstance(DataSetDefinition.class);

    private StudyImpl _study;
    private int _dataSetId;
    private String _name;
    private String _typeURI;
    private String _category;
    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private boolean _keyPropertyManaged; // if true, the extra key is a sequence, managed by the server
    private String _description;
    private boolean _demographicData; //demographic information, sequenceNum
    private transient TableInfo _tableInfoProperties;
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
        "Modified",
        "sourcelsid",
        "QCState",
        "visitRowId",
        "lsid",
        "Dataset"
    };

    private static final String[] DEFAULT_DATE_FIELD_NAMES_ARRAY = new String[]
    {
        "Date",
        "VisitDate",
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
        "Dataset"
    };

    private static final CaseInsensitiveHashSet DEFAULT_DATE_FIELDS;
    private static final CaseInsensitiveHashSet DEFAULT_VISIT_FIELDS;
    private static final CaseInsensitiveHashSet HIDDEN_DEFAULT_FIELDS = new CaseInsensitiveHashSet(HIDDEN_DEFAULT_FIELD_NAMES_ARRAY);

    static
    {
        DEFAULT_DATE_FIELDS = new CaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);
        DEFAULT_DATE_FIELDS.addAll(DEFAULT_DATE_FIELD_NAMES_ARRAY);

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
        if (study.isDateBased())
        {
            return DEFAULT_DATE_FIELDS.contains(fieldName);
        }
        else
        {
            return DEFAULT_VISIT_FIELDS.contains(fieldName);
        }
    }

    public static boolean showOnManageView(String fieldName, Study study)
    {
        return !HIDDEN_DEFAULT_FIELDS.contains(fieldName);
    }

    public Set<String> getDefaultFieldNames()
    {
        Set<String> fieldNames;
        if (getStudy().isDateBased())
            fieldNames = DEFAULT_DATE_FIELDS;
        else
            fieldNames = DEFAULT_VISIT_FIELDS;

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

    /**
     * Get table info representing dataset.  This relies on the DataSetDefinition being removed from
     * the cache if the dataset type changes.  The temptable version also relies on the dataset being
     * uncached when data is updated.
     *
     * see StudyManager.importDatasetTSV()
     */
    public synchronized TableInfo getTableInfo(User user) throws UnauthorizedException
    {
        return getTableInfo(user, true, true);
    }


    public synchronized TableInfo getTableInfo(@NotNull User user, boolean checkPermission, boolean materialized) throws UnauthorizedException
    {
        //noinspection ConstantConditions
        if (user == null)
            throw new IllegalArgumentException("user cannot be null");

        if (checkPermission && !canRead(user))
            HttpView.throwUnauthorized();

        if (materialized)
            return getMaterializedTempTableInfo(user);
        else
            return getJoinTableInfo(user);
    }


    public void materializeInBackground(final User user)
    {
        Runnable task = new Runnable()
        {
            public void run()
            {
                unmaterialize();
                JobRunner.getDefault().submit(new Runnable()
                        {
                            public void run()
                            {
                                getMaterializedTempTableInfo(user);
                            }
                        });
            }
        };

        if (getScope().isTransactionActive())
            getScope().addCommitTask(task);
        else
            task.run();
    }

    public boolean isDemographicData()
    {
        return _demographicData;
    }

    public void setDemographicData(boolean demographicData)
    {
        _demographicData = demographicData;
    }


    /**
     * materializedCache is a cache of the _LAST_ temp table that was materialized for this DatasetDefinition.
     * There may also be temp tables floating around waiting to be garbage collected (see TempTableTracker).
     */
    private static class MaterializedLockObject
    {
        Table.TempTableInfo tinfoMat = null;
        String tempTableName = null;

        // for debugging
        TableInfo tinfoFrom = null;
        long lastVerify = 0;

        void verify()
        {
            synchronized (this)
            {
                long now = System.currentTimeMillis();
                if (null == tinfoMat || lastVerify + 5* Cache.MINUTE > now)
                    return;
                tempTableName = tinfoMat.getTempTableName();
                lastVerify = now;
                boolean ok = tinfoMat.verify();
                if (ok)
                    return;
                tinfoMat = null;
                tinfoFrom = null;
            }
            // cache is not OK
            // Since this should never happen let's preemptively assume the entire dataset temptable cache is tofu
            synchronized (materializedCache)
            {
                materializedCache.clear();
            }
            Logger.getInstance(DataSetDefinition.class).error("TempTable disappeared? " +  tempTableName);
        }
    }


    private static final Map<String, MaterializedLockObject> materializedCache = new HashMap<String,MaterializedLockObject>();

    private synchronized TableInfo getMaterializedTempTableInfo(User user)
    {
        String tempName = getCacheString();

        MaterializedLockObject mlo;

        // if we're in a trasaction we don't want to pollute the cache (can't tell whether this user
        // is changing dataset data or not)

        if (getScope().isTransactionActive())
        {
            mlo = new MaterializedLockObject();
        }
        else
        {
            synchronized(materializedCache)
            {
                mlo = materializedCache.get(tempName);
                if (null == mlo)
                {
                    mlo = new MaterializedLockObject();
                    materializedCache.put(tempName, mlo);
                }
            }
        }

        // prevent multiple threads from materializing the same dataset
        synchronized(mlo)
        {
            try
            {
                mlo.verify();
                TableInfo tinfoProp = mlo.tinfoFrom;
                Table.TempTableInfo tinfoMat = mlo.tinfoMat;

                if (tinfoMat != null)
                {
                    TableInfo tinfoFrom = getJoinTableInfo(user);
                    if (!tinfoProp.getColumnNameSet().equals(tinfoFrom.getColumnNameSet()))
                    {
                        StringBuilder msg = new StringBuilder("unexpected difference in columns sets\n");
                        msg.append("  tinfoProp: ").append(StringUtils.join(tinfoProp.getColumnNameSet(),",")).append("\n");
                        msg.append("  tinfoFrom: ").append(StringUtils.join(tinfoFrom.getColumnNameSet(), ",")).append("\n");
                        _log.error(msg);
                        tinfoMat = null;
                    }
                }
                if (tinfoMat == null)
                {
                    TableInfo tinfoFrom = getJoinTableInfo(user);
                    tinfoMat = materialize(tinfoFrom, tempName);

                    mlo.tinfoFrom = tinfoFrom;
                    mlo.tinfoMat = tinfoMat;
                }
                TempTableTracker.getLogger().debug("DataSetDefinition returning " + tinfoMat.getSelectName());
                return tinfoMat;
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
    }


    private Table.TempTableInfo materialize(TableInfo tinfoFrom, String tempName)
            throws SQLException
    {
        //noinspection UnusedAssignment
        boolean debug=false;
        //noinspection ConstantConditions
        assert debug=true;

        Table.TempTableInfo tinfoMat;
        tinfoMat = Table.createTempTable(tinfoFrom, tempName);
        String fullName = tinfoMat.getTempTableName();
        String shortName = fullName.substring(1+fullName.lastIndexOf('.'));
        Table.execute(tinfoFrom.getSchema(), "CREATE INDEX IX_" + shortName + "_seq ON " + fullName + "(SequenceNum)", null);
        Table.execute(tinfoFrom.getSchema(), "CREATE INDEX IX_" + shortName + "_ptidsequencekey ON " + fullName + "(ParticipantSequenceKey)", null);
        Table.execute(tinfoFrom.getSchema(), "CREATE INDEX IX_" + shortName + "_ptid_seq ON " + fullName + "(ParticipantId,SequenceNum)", null);

        //noinspection ConstantConditions
        if (debug)
        {
            // NOTE: any PropetyDescriptor we hold onto will look like a leak
            // CONSIDER: make MemTracker aware of this cache
            for (ColumnInfo col : tinfoFrom.getColumns())
            {
                if (col instanceof PropertyColumn)
                    assert MemTracker.remove(((PropertyColumn)col).getPropertyDescriptor());
            }
        }
        return tinfoMat;
    }


    public void unmaterialize()
    {
        Runnable task = new Runnable()
        {
            public void run()
            {
                MaterializedLockObject mlo;
                synchronized(materializedCache)
                {
                    String tempName = getCacheString();
                    mlo = materializedCache.get(tempName);
                }
                if (null == mlo)
                    return;
                synchronized (mlo)
                {
                    if (mlo.tinfoMat != null)
                        TempTableTracker.getLogger().debug("DataSetDefinition unmaterialize(" + mlo.tinfoMat.getTempTableName() + ")");
                    mlo.tinfoFrom = null;
                    mlo.tinfoMat = null;
                    StudyManager.fireUnmaterialized(DataSetDefinition.this);
                }
            }
        };

        DbScope scope = getScope();
        if (scope.isTransactionActive())
            scope.addCommitTask(task);
        else
            task.run();
    }


    private DbScope getScope()
    {
        return StudySchema.getInstance().getSchema().getScope();
    }


    private String getCacheString()
    {
        return "Study"+getContainer().getRowId()+"DataSet"+getDataSetId();
    }


    private synchronized TableInfo getJoinTableInfo(User user)
    {
        if (null == _tableInfoProperties)
            _tableInfoProperties = new StudyDataTableInfo(this, user);
        return _tableInfoProperties;
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

    public String getVisitDatePropertyName()
    {
        if (null == _visitDatePropertyName && getStudy().isDateBased())
            _visitDatePropertyName = "Date"; //Todo: Allow alternate names
        return _visitDatePropertyName;
    }

    public void setVisitDatePropertyName(String visitDatePropertyName)
    {
        _visitDatePropertyName = visitDatePropertyName;
    }

    /**
     * Returns the key names for display purposes.
     * If demographic data, visit keys are supressed
     */
    public String[] getDisplayKeyNames()
    {
        List<String> keyNames = new ArrayList<String>();
        keyNames.add("ParticipantId");
        if (!isDemographicData())
        {
            keyNames.add(getStudy().isDateBased() ? "Date" : "SequenceNum");
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

    public boolean isKeyPropertyManaged()
    {
        return _keyPropertyManaged;
    }

    public void setKeyPropertyManaged(boolean keyPropertyManaged)
    {
        _keyPropertyManaged = keyPropertyManaged;
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

    private static class StudyDataTableInfo extends SchemaTableInfo
    {
        int _datasetId;
        SQLFragment _fromSql;

        StudyDataTableInfo(DataSetDefinition def, final User user)
        {
            super("StudyData_" + def.getDataSetId(), StudySchema.getInstance().getSchema());
            final Container c = def.getContainer();
            Study study = StudyManager.getInstance().getStudy(c);
            _datasetId = def.getDataSetId();

            TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData();
            TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            final TableInfo datasetTable = StudySchema.getInstance().getTableInfoDataSet();
            // StudyData columns
            List<ColumnInfo> columnsBase = studyData.getColumns("lsid","participantid","ParticipantSequenceKey","sourcelsid", "created","modified");
            for (ColumnInfo col : columnsBase)
            {
                boolean ptid = "participantid".equals(col.getColumnName());
                if (!ptid)
                    col.setUserEditable(false);
                ColumnInfo wrapped = newDatasetColumnInfo(this, col);
                if (ptid)
                    wrapped.setDisplayColumnFactory(new AutoCompleteDisplayColumnFactory(c, SpecimenService.CompletionType.ParticipantId));
                columns.add(wrapped);
            }
            ColumnInfo sequenceNumCol = newDatasetColumnInfo(this, studyData.getColumn("sequenceNum"));
            sequenceNumCol.setDisplayColumnFactory(new AutoCompleteDisplayColumnFactory(c, SpecimenService.CompletionType.VisitId));

            if (study.isDateBased())
            {
                sequenceNumCol.setNullable(true);
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
                ColumnInfo visitDateCol = newDatasetColumnInfo(this, studyData.getColumn("_visitDate"));
                visitDateCol.setName("Date");
                visitDateCol.setNullable(false);
                columns.add(visitDateCol);

                ColumnInfo dayColumn = newDatasetColumnInfo(this, participantVisit.getColumn("Day"));
                dayColumn.setUserEditable(false);
                columns.add(dayColumn);

                if (def.isDemographicData())
                {
                    visitDateCol.setHidden(true);
                    visitDateCol.setUserEditable(false);
                    dayColumn.setHidden(true);
                }
            }

            if (def.isDemographicData())
            {
                sequenceNumCol.setHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            columns.add(sequenceNumCol);

            ColumnInfo qcStateCol = newDatasetColumnInfo(this, studyData.getColumn(DataSetTable.QCSTATE_ID_COLNAME));
            // UNDONE: make the QC column user editable.  This is turned off for now because StudyDataTableInfo is not
            // a FilteredTable, so it doesn't know how to restrict QC options to those in the current container.
            // Note that QC state can still be modified via the standard update UI.
            qcStateCol.setUserEditable(false);
            columns.add(qcStateCol);

            // Property columns
            ColumnInfo[] columnsLookup = OntologyManager.getColumnsForType(def.getTypeURI(), this, c, user);
            columns.addAll(Arrays.asList(columnsLookup));
            ColumnInfo visitRowId = newDatasetColumnInfo(this, participantVisit.getColumn("VisitRowId"));
            visitRowId.setHidden(true);
            visitRowId.setUserEditable(false);
            columns.add(visitRowId);

            // If we have an extra key, and it's server-managed, make it non-editable
            if (def.isKeyPropertyManaged())
            {
                for (ColumnInfo col : columns)
                {
                    if (col.getName().equals(def.getKeyPropertyName()))
                    {
                        col.setUserEditable(false);
                    }
                }
            }

            // Add the dataset table via a foreign key lookup
            String datasetSql = "(SELECT D.entityid FROM " + datasetTable + " D WHERE " +
                    "D.container ='" + c.getId() + "' AND D.datasetid = " + _datasetId + ")";

            ColumnInfo datasetColumn = new ExprColumn(this, "Dataset", new SQLFragment(datasetSql), Types.VARCHAR);
            LookupForeignKey datasetFk = new LookupForeignKey("entityid")
            {
                public TableInfo getLookupTableInfo()
                {
                    return new DataSetsTable(new StudyQuerySchema(StudyManager.getInstance().getStudy(c), user, true));
                }
            };
            datasetColumn.setFk(datasetFk);
            datasetColumn.setUserEditable(false);
            datasetColumn.setHidden(true);
            columns.add(datasetColumn);

            // HACK reset colMap
            colMap = null;

            _pkColumnNames = Arrays.asList("LSID");

//          <UNDONE> just add a lookup column to the columnlist for VisitDate
            _fromSql = new SQLFragment(
                    "SELECT SD.container, SD.lsid, SD.ParticipantId, SD.ParticipantSequenceKey, SD.SourceLSID, SD.SequenceNum, SD.QCState, SD.Created, SD.Modified, SD._VisitDate AS Date, PV.Day, PV.VisitRowId\n" +
                    "  FROM " + studyData.getSelectName() + " SD LEFT OUTER JOIN " + participantVisit.getSelectName() + " PV ON SD.Container=PV.Container AND SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum \n"+
                    "  WHERE SD.container=? AND SD.datasetid=?");
            _fromSql.add(c);
            _fromSql.add(_datasetId);
        }

        @Override
        public String getSelectName()
        {
            return null;
        }

        @Override
        @NotNull
        public SQLFragment getFromSQL()
        {
            return _fromSql;
        }
    }


    static ColumnInfo newDatasetColumnInfo(StudyDataTableInfo tinfo, ColumnInfo from)
    {
        ColumnInfo c = new ColumnInfo(from, tinfo);
        return c;
    }


    private static final Set<PropertyDescriptor> standardPropertySet = new HashSet<PropertyDescriptor>();
    private static final Map<String, PropertyDescriptor> standardPropertyMap = new CaseInsensitiveHashMap<PropertyDescriptor>();

    public static Set<PropertyDescriptor> getStandardPropertiesSet()
    {
        synchronized(standardPropertySet)
        {
            if (standardPropertySet.isEmpty())
            {
                TableInfo info = StudySchema.getInstance().getTableInfoStudyData();
                for (ColumnInfo col : info.getColumns())
                {
                    String propertyURI = col.getPropertyURI();
                    if (propertyURI == null)
                        continue;
                    String name = col.getName();
                    // hack: _visitdate is private, but we want VisitDate (for now)
                    if (name.equalsIgnoreCase("_VisitDate"))
                        name = "VisitDate";
                    PropertyType type = PropertyType.getFromClass(col.getJavaObjectClass());
                    PropertyDescriptor pd = new PropertyDescriptor(
                            propertyURI, type.getTypeUri(), name, ContainerManager.getSharedContainer());
                    standardPropertySet.add(pd);
                }
            }
            return standardPropertySet;
        }
    }

    public Domain getDomain()
    {
        return PropertyService.get().getDomain(getContainer(), getTypeURI());
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
        assert getStandardPropertiesMap().get(name).getPropertyURI().equals(StudyURI + name);
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
}
