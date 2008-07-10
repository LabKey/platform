/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.*;
import org.labkey.api.view.HttpView;
import org.labkey.study.StudySchema;
import org.apache.log4j.Logger;
import org.apache.log4j.Category;
import org.apache.commons.lang.StringUtils;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jan 6, 2006
 * Time: 10:29:31 AM
 */
public class DataSetDefinition extends AbstractStudyEntity<DataSetDefinition> implements Cloneable
{
    // standard string to use in URLs etc.
    public static final String DATASETKEY = "datasetId";
    private static Category _log = Logger.getInstance(DataSetDefinition.class);
   
    private Study _study;
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
        "lsid"
    };

    private static final CaseInsensitiveHashSet BASE_DEFAULT_FIELD_NAMES =
        new CaseInsensitiveHashSet(BASE_DEFAULT_FIELD_NAMES_ARRAY);


    public DataSetDefinition()
    {
    }


    public DataSetDefinition(Study study, int dataSetId, String name, String label, String category, String typeURI)
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

    @Deprecated
    public DataSetDefinition(Study study, int dataSetId, String name, String category, String typeURI)
    {
        this(study, dataSetId, name, name, category, typeURI);
    }

    public static boolean isDefaultFieldName(String fieldName, Study study)
    {
        if (study.isDateBased())
        {
            if ("Date".equalsIgnoreCase(fieldName) ||
                "VisitDate".equalsIgnoreCase(fieldName) ||
                "Day".equalsIgnoreCase(fieldName))
                return true;
        }
        else
        {
            if ("VisitSequenceNum".equalsIgnoreCase(fieldName))
                return true;
        }
        return BASE_DEFAULT_FIELD_NAMES.contains(fieldName);
    }


    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
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
    public synchronized TableInfo getTableInfo(User user) throws ServletException
    {
        return getTableInfo(user, true, true);
    }


    public synchronized TableInfo getTableInfo(User user, boolean checkPermission, boolean materialized) throws ServletException
    {
        if (checkPermission && !canRead(user))
            HttpView.throwUnauthorized();

        if (materialized)
            return getMaterializedTempTableInfo();
        else
            return getJoinTableInfo();
    }


    public void materializeInBackground()
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
                                getMaterializedTempTableInfo();
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
            Logger.getInstance(DataSetDefinition.class).error("TempTable disappeared? " +  tinfoMat.getTempTableName());
        }
    }


    private static final Map<String, MaterializedLockObject> materializedCache = new HashMap<String,MaterializedLockObject>();

    private synchronized TableInfo getMaterializedTempTableInfo()
    {
        //noinspection UnusedAssignment
        boolean debug=false;
        //noinspection ConstantConditions
        assert debug=true;

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
                    TableInfo tinfoFrom = getJoinTableInfo();
                    if (!tinfoProp.getColumnNameSet().equals(tinfoFrom.getColumnNameSet()))
                    {
                        StringBuilder msg = new StringBuilder("unexpected difference in columns sets\n");
                        msg.append("  tinfoProp: " + StringUtils.join(tinfoProp.getColumnNameSet(),",") + "\n");
                        msg.append("  tinfoFrom: " + StringUtils.join(tinfoFrom.getColumnNameSet(),",") + "\n");
                        _log.error(msg);
                        tinfoMat = null;
                    }
                }
                if (tinfoMat == null)
                {
                    TableInfo tinfoFrom = getJoinTableInfo();
                    tinfoMat = materialize(tinfoFrom, tempName);

                    mlo.tinfoFrom = tinfoFrom;
                    mlo.tinfoMat = tinfoMat;
                }
                TempTableTracker.getLogger().debug("DataSetDefinition returning " + tinfoMat.getFromSQL());
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
        Table.execute(tinfoFrom.getSchema(), "CREATE INDEX IX_" + shortName + " ON " + fullName + "(ParticipantId,SequenceNum)", null);

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


    private synchronized TableInfo getJoinTableInfo()
    {
        if (null == _tableInfoProperties)
            _tableInfoProperties = new StudyDataTableInfo(this);
        return _tableInfoProperties;
    }


    public Study getStudy()
    {
        if (null == _study)
            _study = StudyManager.getInstance().getStudy(getContainer());
        return _study;
    }


    public boolean canRead(User user)
    {
        int perm = getStudy().getACL().getPermissions(user);
        if (0 != (perm & ACL.PERM_READ))
            return true;
        if (0 == (perm & ACL.PERM_READOWN))
            return false;

        int[] groups = getStudy().getACL().getGroups(ACL.PERM_READOWN, user);
        int dsPerm = getACL().getPermissions(groups);
        return 0 != (dsPerm & ACL.PERM_READ);
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


    private static class StudyDataTableInfo extends SchemaTableInfo
    {
        int _datasetId;

        StudyDataTableInfo(DataSetDefinition def)
        {
            super("StudyData_" + def.getDataSetId(), StudySchema.getInstance().getSchema());
            Container c = def.getContainer();
            Study study = StudyManager.getInstance().getStudy(c);
            _datasetId = def.getDataSetId();

            TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData();
            TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            // StudyData columns
            List<ColumnInfo> columnsBase = studyData.getColumns("lsid","participantid","sourcelsid", "created","modified");
            for (ColumnInfo col : columnsBase)
            {
                if (!"participantid".equals(col.getColumnName()))
                    col.setUserEditable(false);
                columns.add(newDatasetColumnInfo(this, col));
            }
            ColumnInfo sequenceNumCol = newDatasetColumnInfo(this, studyData.getColumn("sequenceNum"));
            if (study.isDateBased())
            {
                sequenceNumCol.setNullable(true);
                sequenceNumCol.setIsHidden(true);
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
                    visitDateCol.setIsHidden(true);
                    visitDateCol.setUserEditable(false);
                    dayColumn.setIsHidden(true);
                }
            }

            if (def.isDemographicData())
            {
                sequenceNumCol.setIsHidden(true);
                sequenceNumCol.setUserEditable(false);
            }

            columns.add(sequenceNumCol);
            // Property columns
            ColumnInfo[] columnsLookup = OntologyManager.getColumnsForType(def.getTypeURI(), this, c);
            columns.addAll(Arrays.asList(columnsLookup));
            ColumnInfo visitRowId = newDatasetColumnInfo(this, participantVisit.getColumn("VisitRowId"));
            visitRowId.setIsHidden(true);
            visitRowId.setUserEditable(false);
            columns.add(visitRowId);

            // If we have an extra key, and it's server-managed, hide it
            if (def.isKeyPropertyManaged())
            {
                for (ColumnInfo col : columns)
                {
                    if (col.getName().equals(def.getKeyPropertyName()))
                    {
                        col.setIsHidden(true);
                        col.setUserEditable(false);
                    }
                }
            }

            // HACK reset colMap
            colMap = null;

            _pkColumnNames = Arrays.asList("LSID");

//          <UNDONE> just add a lookup column to the columnlist for VisitDate
            selectName = new SQLFragment(
                    "(SELECT SD.lsid, SD.ParticipantId, SD.SourceLSID, SD.SequenceNum, SD.Created, SD.Modified, SD._VisitDate AS Date, PV.Day, PV.VisitRowId\n" +
                    "  FROM " + studyData + " SD LEFT OUTER JOIN " + participantVisit + " PV ON SD.Container=PV.Container AND SD.ParticipantId=PV.ParticipantId AND SD.SequenceNum=PV.SequenceNum \n"+ 
                    "  WHERE SD.container='" + c.getId() + "' AND SD.datasetid=" + _datasetId + ") " + getAliasName());
            
//            <UNDONE> parameters don't work in selectName
//                    "  WHERE PV.container=? AND SD.container=? AND SD.datasetid=?) " + getAliasName());
//            selectName.add(c.getId());
//            selectName.add(c.getId());
//            selectName.add(datasetId);
//            </UNDONE>
        }

        @Override
        public String getAliasName()
        {
            return "Dataset" + _datasetId;
        }

        void setSelectName(String name)
        {
            selectName = new SQLFragment(name);
        }
    }


    static ColumnInfo newDatasetColumnInfo(StudyDataTableInfo tinfo, ColumnInfo from)
    {
        ColumnInfo c = new ColumnInfo(from, tinfo);
        return c;
    }


    static final Set<PropertyDescriptor> standardPropertySet = new HashSet<PropertyDescriptor>();
    static final Map<String,PropertyDescriptor> standardPropertyMap = new CaseInsensitiveHashMap<PropertyDescriptor>();

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

    @Override
    protected boolean supportsACLUpdate()
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

    public Cohort getCohort()
    {
        if (_cohortId == null)
            return null;
        return Table.selectObject(StudySchema.getInstance().getTableInfoCohort(), _cohortId, Cohort.class);
    }
}
