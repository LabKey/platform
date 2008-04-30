package org.labkey.study.model;

import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.MemTracker;
import org.labkey.api.view.HttpView;
import org.labkey.study.StudySchema;

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
   
    private Study _study;
    private int _dataSetId;
    private String _name;
    private String _typeURI;
    private String _category;
    private String _visitDatePropertyName;
    private String _keyPropertyName;
    private String _description;
    private boolean _demographicData; //demographic information, sequenceNum
    private transient TableInfo _tableInfoProperties;
    private Integer _cohortId;


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
     *
     * DatasetDefinitions may get thrown out of mememory even though there is a perfectly good
     * materialized dataset in the database.  Since these are expensive, try to hook them up again.
     *
     * There may also be temp tables floating around waiting to be garbage collected, we don't care about those
     * (see TempTableTracker).
     */
    private static class MaterializedLockObject
    {
        final Object materializeLock = new Object();
        TableInfo tinfoFrom = null;    // for debugging
        TableInfo tinfoMat = null;
    }
    private static final Map<String, MaterializedLockObject> materializedCache = new HashMap<String,MaterializedLockObject>();


    private synchronized TableInfo getMaterializedTempTableInfo()
    {
        //noinspection UnusedAssignment
        boolean debug=false;
        //noinspection ConstantConditions
        assert debug=true;

        String tempName = getCacheString();

        MaterializedLockObject p;

        // if we're in a trasaction we don't want to pollute the cache (can't tell whether this user
        // is changing dataset data or not)

        if (getScope().isTransactionActive())
        {
            p = new MaterializedLockObject();
        }
        else
        {
            synchronized(materializedCache)
            {
                p = materializedCache.get(tempName);
                if (null == p)
                {
                    p = new MaterializedLockObject();
                    materializedCache.put(tempName, p);
                }
            }
        }

        // prevent multiple threads from materializing the same dataset
        synchronized(p.materializeLock)
        {
            try
            {
                TableInfo tinfoProp = p.tinfoFrom;
                Table.TempTableInfo tinfoMat = (Table.TempTableInfo)p.tinfoMat;

                if (tinfoMat == null)
                {
                    TableInfo tinfoFrom = getJoinTableInfo();
                    tinfoMat = materialize(tinfoFrom, tempName);

                    p.tinfoFrom = tinfoFrom;
                    p.tinfoMat = tinfoMat;
                }
                else
                {
                    //noinspection ConstantConditions
                    if (debug)
                    {
                        TableInfo tinfoFrom = getJoinTableInfo();
                        assert tinfoProp.getColumnNameSet().equals( tinfoFrom.getColumnNameSet());
                    }
                }
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
        final String tempName = getCacheString();
        DbScope scope = getScope();

        Runnable task = new Runnable()
        {
            public void run()
            {
                synchronized(materializedCache)
                {
                    materializedCache.remove(tempName);
                }
            }
        };

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
            _tableInfoProperties = new StudyDataTableInfo(getContainer(), getTypeURI(), getDataSetId());
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

    public String[] getKeyNames()
    {
        String[] props = new String[_keyPropertyName == null ? 2 : 3];
        props[0] = "ParticipantId";
        props[1] = getStudy().isDateBased() ? "Date" : "SequenceNum";
        if (null != _keyPropertyName)
            props[2] = _keyPropertyName;

        return props;
    }
    
    public String getKeyPropertyName()
    {
        return _keyPropertyName;
    }

    public void setKeyPropertyName(String keyPropertyName)
    {
        _keyPropertyName = keyPropertyName;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }


    static class StudyDataTableInfo extends SchemaTableInfo
    {
        int _datasetId;

        StudyDataTableInfo(Container c, String typeURI, int datasetId)
        {
            super("StudyData_" + datasetId, StudySchema.getInstance().getSchema());
            Study study = StudyManager.getInstance().getStudy(c);
            _datasetId = datasetId;

            TableInfo studyData = StudySchema.getInstance().getTableInfoStudyData();
            TableInfo participantVisit = StudySchema.getInstance().getTableInfoParticipantVisit();
            // StudyData columns
            ColumnInfo[] columnsBase = studyData.getColumns("lsid","participantid","sourcelsid", "created","modified");
            for (ColumnInfo col : columnsBase)
            {
                if (!"participantid".equals(col.getColumnName()))
                    col.setUserEditable(false);
                columnList.add(newDatasetColumnInfo(this, col));
            }
            ColumnInfo sequenceNumCol = newDatasetColumnInfo(this, studyData.getColumn("sequenceNum"));
            if (study.isDateBased())
            {
                sequenceNumCol.setNullable(true);
                ColumnInfo visitDateCol = newDatasetColumnInfo(this, studyData.getColumn("_visitDate"));
                visitDateCol.setName("Date");
                visitDateCol.setNullable(false);
                columnList.add(visitDateCol);
                columnList.add(newDatasetColumnInfo(this, participantVisit.getColumn("Day")));
            }
            columnList.add(sequenceNumCol);
            // Property columns
            ColumnInfo[] columnsLookup = OntologyManager.getColumnsForType(typeURI, this, c);
            columnList.addAll(Arrays.asList(columnsLookup));
            ColumnInfo visitRowId = newDatasetColumnInfo(this, participantVisit.getColumn("VisitRowId"));
            visitRowId.setIsHidden(true);
            visitRowId.setUserEditable(false);
            columnList.add(visitRowId);
            // HACK reset colMap
            colMap = null;

            _pkColumnNames = new String[] { "LSID" };

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
