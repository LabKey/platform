package org.labkey.experiment.api;

import org.labkey.api.exp.api.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.data.*;
import org.labkey.api.util.*;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipeRoot;
import org.apache.log4j.Logger;
import org.apache.commons.lang.StringUtils;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.*;
import java.io.IOException;
import java.io.File;

import org.labkey.experiment.*;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.experiment.pipeline.MoveRunsPipelineJob;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;

public class ExperimentServiceImpl implements ExperimentService.Interface
{
    public static String PROTOCOLS_FOLDER = "protocols";

    private DatabaseCache<MaterialSource> materialSourceCache;

    static private final Logger _log = Logger.getLogger(ExperimentServiceImpl.class);
    // following are used in URLs to generate lineage graphs
    public static String TYPECODE_MATERIAL = "M";
    public static String TYPECODE_DATA = "D";
    public static String TYPECODE_PROT_APP = "A";
    public static final String DEFAULT_MATERIAL_SOURCE_NAME = "Unspecified";

    private Set<ExperimentRunFilter> _runFilters = new TreeSet<ExperimentRunFilter>();
    private Set<ExperimentDataHandler> _dataHandlers = new HashSet<ExperimentDataHandler>();
    private Set<RunExpansionHandler> _expansionHanders = new HashSet<RunExpansionHandler>();
    protected Map<String, DataType> _dataTypes = new HashMap<String, DataType>();
    protected Map<String, ProtocolImplementation> _protocolImplementations = new HashMap<String, ProtocolImplementation>();

    private synchronized DatabaseCache<MaterialSource> getMaterialSourceCache()
    {
        if (materialSourceCache == null)
        {
            materialSourceCache = new DatabaseCache<MaterialSource>(getExpSchema().getScope(),300);
        }
        return materialSourceCache;
    }

    public ExpRunImpl getExpRun(int rowid)
    {
        ExperimentRun run = getExperimentRun(rowid);
        if (run == null)
            return null;
        return new ExpRunImpl(run);
    }

    public ExpRun getExpRun(String lsid)
    {
        ExperimentRun run = getExperimentRun(lsid);
        if (run == null)
            return null;
        return new ExpRunImpl(run);
    }

    public ExpRun[] getExpRuns(Container container, ExpProtocol parentProtocol, ExpProtocol childProtocol)
    {
        try
        {
            SQLFragment sql = new SQLFragment(" SELECT ER.* "
                        + " FROM exp.ExperimentRun ER "
                        + " WHERE ER.Container = ? ");
            sql.add(container.getId());
            if (parentProtocol != null)
            {
                sql.append("\nAND ER.ProtocolLSID = ?");
                sql.add(parentProtocol.getLSID());
            }
            if (childProtocol != null)
            {
                sql.append("\nAND ER.RowId IN (SELECT PA.RunId "
                    + " FROM exp.ProtocolApplication PA "
                    + " WHERE PA.ProtocolLSID = ? ) ");
                sql.add(childProtocol.getLSID());
            }
            ExperimentRun[] runs = Table.executeQuery(getSchema(), sql.getSQL(), sql.getParams().toArray(new Object[0]), ExperimentRun.class);
            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

    }

    public ExpRunImpl createExperimentRun(Container container, String name)
    {
        ExperimentRun run = new ExperimentRun();
        run.setName(name);
        run.setLSID(generateGuidLSID(container, "Run"));
        run.setContainer(container.getId());
        return new ExpRunImpl(run);
    }

    public ExpDataImpl getExpData(int rowid)
    {
        try
        {
            Data data = Table.selectObject(getTinfoData(), Table.ALL_COLUMNS, new SimpleFilter("RowId", rowid), null, Data.class);
            if (data == null)
                return null;
            return new ExpDataImpl(data);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpDataImpl getExpData(String lsid)
    {
        try
        {
            Data data = Table.selectObject(getTinfoData(), Table.ALL_COLUMNS, new SimpleFilter("LSID", lsid), null, Data.class);
            if (data == null)
                return null;
            return new ExpDataImpl(data);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpData[] getExpDatas(Container container, DataType type)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("Container", container.getId());
            if (type != null)
                filter.addWhereClause(Lsid.namespaceFilter("LSID", type.getNamespacePrefix()), null);
            return ExpDataImpl.fromDatas(Table.select(getTinfoData(), Table.ALL_COLUMNS, filter, null, Data.class));
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpData[0];
        }
    }

    protected ExpDataImpl createData(Container container, String lsidString)
    {
        Data data = new Data();
        Lsid lsid = new Lsid(lsidString);
        data.setLSID(lsidString);
        data.setName(lsid.getObjectId());
        data.setCpasType("Data");
        data.setContainer(container.getId());
        return new ExpDataImpl(data);
    }

    public ExpDataImpl createData(Container container, DataType type)
    {
        return createData(container, type, generateGuidLSID(container, type));
    }

    public ExpDataImpl createData(Container container, DataType type, String name)
    {
        return createData(container, generateLSID(container, type, name));
    }

    public ExpMaterialImpl getExpMaterial(int rowid)
    {
        Material material = getMaterial(rowid);
        if (material == null)
            return null;
        return new ExpMaterialImpl(material);
    }

    public ExpMaterialImpl createExpMaterial()
    {
        return new ExpMaterialImpl(new Material());
    }

    public ExpMaterialImpl getExpMaterial(String lsid)
    {
        Material material = getMaterial(lsid);
        if (material == null)
            return null;
        return new ExpMaterialImpl(material);
    }

    public ExpSampleSet getSampleSet(int rowid)
    {
        MaterialSource ms = getMaterialSource(rowid);
        if (ms == null)
            return null;
        return new ExpSampleSetImpl(ms);
    }

    public ExpSampleSetImpl getSampleSet(String lsid)
    {
        MaterialSource ms = getMaterialSource(lsid);
        if (ms == null)
            return null;
        return new ExpSampleSetImpl(ms);
    }

    public ExpSampleSet[] getSampleSets(Container container, boolean includeOtherContainers)
    {
        MaterialSource[] result;
        try
        {
            SimpleFilter filter = new SimpleFilter();
            if (includeOtherContainers)
            {
                filter.addClause(new SimpleFilter.SQLClause("Container = ? OR Container = ? OR Container = ?", new Object[] {container.getId(), container.getProject().getId(), ContainerManager.getSharedContainer().getId()}, "Container"));
            }
            else
            {
                filter.addCondition("Container", container.getId());
            }

            result = Table.select(getTinfoMaterialSource(), Table.ALL_COLUMNS, filter, new Sort("Name"), MaterialSource.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        MaterialSource[] sources = result;
        ExpSampleSet[] ret = new ExpSampleSet[sources.length];
        for (int i = 0; i < sources.length; i ++)
        {
            ret[i] = new ExpSampleSetImpl(sources[i]);
        }
        return ret;
    }

    public ExpExperimentImpl getExpExperiment(int rowid)
    {
        Experiment experiment = Table.selectObject(getTinfoExperiment(), (Integer) rowid, Experiment.class);
        if (null != experiment)
        {
            return new ExpExperimentImpl(experiment);
        }
        return null;
    }

    public ExpExperiment createExpExperiment(Container container, String name)
    {
        Experiment exp = new Experiment();
        exp.setContainer(container.getId());
        exp.setName(name);
        exp.setLSID(generateLSID(container, ExpExperiment.class, name));
        return new ExpExperimentImpl(exp);
    }

    public ExpExperiment getExpExperiment(String lsid)
    {
        Experiment exp = getExperiment(lsid);
        if (exp == null)
            return null;
        return new ExpExperimentImpl(exp);
    }

    public ExpExperimentImpl[] getExpExperimentsForRun(String lsid)
    {
        Experiment[] experiments = getExperimentsForRun(lsid);
        ExpExperimentImpl[] ret = new ExpExperimentImpl[experiments.length];
        for (int i = 0; i < experiments.length; i ++)
        {
            ret[i] = new ExpExperimentImpl(experiments[i]);
        }
        return ret;
    }

    public ExpProtocol getExpProtocol(int rowid)
    {
        Protocol protocol = getProtocol(rowid);
        if (protocol == null)
            return null;
        return new ExpProtocolImpl(protocol);
    }

    public ExpProtocolImpl getExpProtocol(String lsid)
    {
        Protocol protocol = getProtocol(lsid);
        if (protocol == null)
            return null;
        return new ExpProtocolImpl(protocol);
    }

    public ExpProtocolImpl[] getProtocolsForExperiment(int rowId)
    {
        try
        {
            String sql = "SELECT p.* FROM " + getTinfoProtocol() + " p, " + getTinfoExperimentRun() + " r WHERE p.LSID = r.ProtocolLSID AND r.RowId IN (SELECT ExperimentRunId FROM " + getTinfoRunList() + " WHERE ExperimentId = ?)";
            return ExpProtocolImpl.fromProtocols(Table.executeQuery(getSchema(), sql, new Object[] { rowId }, Protocol.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpProtocolImpl getExpProtocol(Container container, String name)
    {
        return getExpProtocol(generateLSID(container, "Protocol", name));
    }

    public ExpProtocolImpl createExpProtocol(Container container, String name, ExpProtocol.ApplicationType type)
    {
        ExpProtocolImpl existing = getExpProtocol(container, name);
        if (existing != null)
        {
            throw new IllegalArgumentException("Protocol " + existing.getLSID() + " already exists.");
        }
        Protocol protocol = new Protocol();
        protocol.setName(name);
        protocol.setLSID(generateLSID(container, "Protocol", name));
        protocol.setContainer(container.getId());
        protocol.setApplicationType(type.toString());
        protocol.setOutputDataType("Data");
        protocol.setOutputMaterialType("Material");
        return new ExpProtocolImpl(protocol);
    }

    public ExpRunTable createRunTable(String alias)
    {
        return new ExpRunTableImpl(alias);
    }

    public ExpDataTable createDataTable(String alias)
    {
        return new ExpDataTableImpl(alias);
    }

    public ExpSampleSetTable createSampleSetTable(String alias)
    {
        return new ExpSampleSetTableImpl(alias);
    }

    public ExpProtocolTableImpl createProtocolTable(String alias)
    {
        return new ExpProtocolTableImpl(alias);
    }

    public ExpExperimentTable createExperimentTable(String alias)
    {
        return new ExpExperimentTableImpl(alias);
    }

    public ExpMaterialTable createMaterialTable(String alias, QuerySchema schema)
    {
        return new ExpMaterialTableImpl(alias, schema);
    }

    public ExpProtocolApplicationTable createProtocolApplicationTable(String alias)
    {
        return new ExpProtocolApplicationTableImpl(alias);
    }


    private String getNamespacePrefix(Class<? extends ExpObject> clazz)
    {
        if (clazz == ExpData.class)
            return "Data";
        if (clazz == ExpMaterial.class)
            return "Material";
        if (clazz == ExpProtocol.class)
            return "Protocol";
        if (clazz == ExpRun.class)
            return "Run";
        if (clazz == ExpExperiment.class)
            return "Experiment";
        if (clazz == ExpSampleSet.class)
            return "SampleSet";
        if (clazz == ExpProtocolApplication.class)
            return "ProtocolApplication";
        throw new IllegalArgumentException("Invalid class " + clazz.getName());
    }

    private String generateGuidLSID(Container container, String lsidPrefix)
    {
        return generateLSID(container, lsidPrefix, GUID.makeGUID());
    }

    private String generateLSID(Container container, String lsidPrefix, String objectName)
    {
        String str = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":" + lsidPrefix + ".Folder-" + container.getRowId() + ":" + objectName;
        return new Lsid(str).toString();
    }

    public String generateGuidLSID(Container container, Class<? extends ExpObject> clazz)
    {
        return generateGuidLSID(container, getNamespacePrefix(clazz));
    }

    public String generateGuidLSID(Container container, DataType type)
    {
        return generateGuidLSID(container, type.getNamespacePrefix());
    }

    public String generateLSID(Container container, Class<? extends ExpObject> clazz, String name)
    {
        return generateLSID(container, getNamespacePrefix(clazz), name);
    }

    public String generateLSID(Container container, DataType type, String name)
    {
        return generateLSID(container, type.getNamespacePrefix(), name);
    }

    public ExpObject findObjectFromLSID(String lsid) throws Exception
    {
        Identifiable id = LsidManager.get().getObject(lsid);
        if (id instanceof ExpObject)
        {
            return (ExpObject)id;
        }
        throw new IllegalArgumentException("Unsupported type : " + id);
    }

    public ExpSampleSetImpl getSampleSet(Container container, String name)
    {
        return getSampleSet(generateLSID(container, ExpSampleSet.class, name));
    }

    public ExpSampleSetImpl lookupActiveSampleSet(Container container)
    {
        MaterialSource materialSource = lookupActiveMaterialSource(container);
        if (materialSource == null)
        {
            return null;
        }
        return new ExpSampleSetImpl(materialSource);
    }

    public void setActiveSampleSet(Container container, ExpSampleSet sampleSet)
    {
        try
        {
            String materialSourceLSID = sampleSet.getLSID();
            MaterialSource current = lookupActiveMaterialSource(container);
            if (current == null)
            {
                if (materialSourceLSID == null)
                {
                    // No current value, no new value
                    return;
                }
                else
                {
                    // No current value, so need to insert a new row
                    String sql = "INSERT INTO " + getTinfoActiveMaterialSource() + " (Container, MaterialSourceLSID) " +
                        "VALUES (?, ?)";
                    Table.execute(getExpSchema(), sql, new Object[] { container.getId(), materialSourceLSID });
                }
            }
            else
            {
                if (materialSourceLSID == null)
                {
                    // Current value exists, needs to be deleted
                    String sql = "DELETE FROM " + getTinfoActiveMaterialSource() + " WHERE Container = ?";
                    Table.execute(getExpSchema(), sql, new Object[] { container.getId() });
                }
                else
                {
                    // Current value exists, needs to be changed
                    String sql = "UPDATE " + getTinfoActiveMaterialSource() + " SET MaterialSourceLSID = ? WHERE Container = ?";
                    Table.execute(getExpSchema(), sql, new Object[] { materialSourceLSID, container.getId() });
                }
            }

            PropertyDescriptor prop = ensureMaterialInputRole(container, "Material", materialSourceLSID);
            if (materialSourceLSID != null && !materialSourceLSID.equals(prop.getRangeURI()))
            {
                prop.setRangeURI(materialSourceLSID);
                OntologyManager.updatePropertyDescriptor(prop);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void beginTransaction() throws SQLException
    {
        getExpSchema().getScope().beginTransaction();
    }

    public void commitTransaction() throws SQLException
    {
        getExpSchema().getScope().commitTransaction();
    }

    public boolean isTransactionActive()
    {
        return getExpSchema().getScope().isTransactionActive();
    }

    public void rollbackTransaction()
    {
        getExpSchema().getScope().rollbackTransaction();
    }

    public QueryView createExperimentRunWebPart(ViewContext context, ExperimentRunFilter filter, boolean moveButton)
    {
        ExperimentRunListView view = ExperimentRunListView.createView(context, filter, false);
        view.setShowDeleteButton(true);
        view.setShowAddToRunGroupButton(true);
        view.setShowMoveRunsButton(moveButton);
        view.setTitle("Experiment Runs");
        ActionURL url = context.getActionURL().clone();
        url.setPageFlow("Experiment");
        url.setAction("showRuns.view");
        url.deleteParameters();
        url.addParameter("experimentRunFilter", filter.getDescription());
        view.setTitleHref(url.toString());
        return view;
    }

    public DbSchema getSchema()
    {
        return getExpSchema();
    }

    public List<ExpRun> loadXar(XarSource source, PipelineJob pipelineJob, boolean reloadExistingRuns) throws ExperimentException
    {
        XarReader reader = new XarReader(source, pipelineJob);
        reader.parseAndLoad(reloadExistingRuns);
        return reader.getExperimentRuns();
    }

    public Map<String, PropertyDescriptor> getDataInputRoles(Container container)
    {
        LinkedHashMap<String, PropertyDescriptor> ret = new LinkedHashMap<String, PropertyDescriptor>();
        PropertyDescriptor[] pds = OntologyManager.getPropertiesForType(getDataInputRoleDomainURI(container), container);
        for (PropertyDescriptor pd : pds)
        {
            ret.put(pd.getName(), pd);
        }
        return ret;
    }

    public Map<String, PropertyDescriptor> getMaterialInputRoles(Container container)
    {
        return getDomainProperties(container, getMaterialInputRoleDomainURI(container));
    }

    public String getDataInputRolePropertyURI(Container container, String role)
    {
        return getDataInputRoleDomainURI(container) + "#" + role;
    }

    private Map<String, PropertyDescriptor> getDomainProperties(Container container, String domainURI)
    {
        TreeMap<String, PropertyDescriptor> ret = new TreeMap<String, PropertyDescriptor>();
        for (PropertyDescriptor pd : OntologyManager.getPropertiesForType(domainURI, container))
        {
            ret.put(pd.getName(), pd);
        }
        return ret;
    }

    private PropertyDescriptor ensureInputRole(Container container, String domainURI, String roleName, String typeURI) throws SQLException
    {
        if (roleName == null)
            return null;
        if (typeURI == null)
        {
            typeURI = PropertyType.STRING.getTypeUri();
        }
        Map<String, PropertyDescriptor> map = getDomainProperties(container, domainURI);
        PropertyDescriptor ret = map.get(roleName);
        if (ret != null)
            return ret;
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
        {
            dd = new DomainDescriptor(domainURI, container);
            OntologyManager.insertOrUpdateDomainDescriptor(dd);
        }
        String propertyURI = domainURI + "#" + roleName;
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(propertyURI, container);
        if (pd == null)
        {
            pd = new PropertyDescriptor(propertyURI, typeURI, roleName, container);
            pd = OntologyManager.insertPropertyDescriptor(pd);
        }
        return OntologyManager.insertOrUpdatePropertyDescriptor(pd, dd);
    }


    public PropertyDescriptor ensureDataInputRole(User user, Container container, String roleName, ExpData data) throws SQLException
    {
        return ensureInputRole(container, getDataInputRoleDomainURI(container), roleName, null);
    }

    public PropertyDescriptor ensureMaterialInputRole(Container container, String roleName, ExpMaterial material) throws SQLException
    {
        String typeURI = null;
        if (material != null)
        {
            ExpSampleSet ss = material.getSampleSet();
            if (ss != null)
            {
                typeURI = ss.getLSID();
            }
        }
        return ensureMaterialInputRole(container, roleName, typeURI);
    }

    public PropertyDescriptor ensureMaterialInputRole(Container container, String roleName, String typeURI) throws SQLException
    {
        return ensureInputRole(container, getMaterialInputRoleDomainURI(container), roleName, typeURI);
    }

    public String getDataInputRoleDomainURI(Container container)
    {
        return generateLSID(container, "Domain", "DataInputRole");
    }

    public String getMaterialInputRoleDomainURI(Container container)
    {
        return generateLSID(container, "Domain", "MaterialInputRole");
    }

    public ExpDataImpl[] deleteExperimentRunForMove(int runId, Container container, User user) throws SQLException, ExperimentException
    {
        ExpDataImpl[] datasToDelete = getAllDataOwnedByRun(runId);

        deleteRun(runId, container, datasToDelete, user);
        return datasToDelete;
    }


    private void deleteRun(int runId, Container container, ExpData[] datasToDelete, User user)
        throws SQLException
    {
        ExperimentRun run = getExperimentRun(runId);
        if (run == null)
        {
            return;
        }
        if (user == null || !ContainerManager.getForId(run.getContainer()).hasPermission(user, ACL.PERM_DELETE))
        {
            throw new SQLException("Attempting to delete an ExperimentRun without having delete permissions for its container");
        }
        DbCache.remove(getTinfoExperimentRun(), getCacheKey(run.getLSID()));

        beforeDeleteData(datasToDelete);
        //delete run properties and all children
        OntologyManager.deleteOntologyObject(run.getLSID(), container, true);

        String sql = " ";
        sql += "  DELETE FROM exp.ProtocolApplicationParameter WHERE ProtocolApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + runId + ") ;";

        sql += " UPDATE " + getTinfoData() + " SET SourceApplicationId = NULL, RunId = NULL, SourceProtocolLSID = NULL  " +
                " WHERE RowId IN (SELECT exp.Data.RowId FROM exp.Data " +
                " INNER JOIN exp.DataInput ON exp.Data.RowId = exp.DataInput.DataId " +
                " INNER JOIN exp.ProtocolApplication PAOther ON exp.DataInput.TargetApplicationId = PAOther.RowId " +
                " INNER JOIN exp.ProtocolApplication PA ON exp.Data.SourceApplicationId = PA.RowId " +
                " WHERE PAOther.RunId <> PA.RunId AND PA.RunId = " + runId + ") ; ";

        sql += " UPDATE " + getTinfoMaterial() + " SET SourceApplicationId = NULL, RunId = NULL, SourceProtocolLSID = NULL  " +
                " WHERE RowId IN (SELECT exp.Material.RowId FROM exp.Material " +
                " INNER JOIN exp.MaterialInput ON exp.Material.RowId = exp.MaterialInput.MaterialId " +
                " INNER JOIN exp.ProtocolApplication PAOther ON exp.MaterialInput.TargetApplicationId = PAOther.RowId " +
                " INNER JOIN exp.ProtocolApplication PA ON exp.Material.SourceApplicationId = PA.RowId " +
                " WHERE PAOther.RunId <> PA.RunId AND PA.RunId = " + runId + ") ; ";

        sql += "  DELETE FROM exp.DataInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + runId + ") ;";
        sql += "  DELETE FROM exp.MaterialInput WHERE TargetApplicationId IN (SELECT RowId FROM exp.ProtocolApplication WHERE RunId = " + runId + ") ;";
        sql += "  DELETE FROM exp.DataInput WHERE DataId IN (SELECT RowId FROM exp.Data WHERE RunId = " + runId + ") ;";
        sql += "  DELETE FROM exp.MaterialInput WHERE MaterialId IN (SELECT RowId FROM exp.Material WHERE RunId = " + runId + ") ;";

        sql += "  DELETE FROM exp.Data WHERE RunId = " + runId + " ; ";
        sql += "  DELETE FROM exp.Material WHERE RunId = " + runId + " ; ";
        sql += "  DELETE FROM exp.ProtocolApplication WHERE RunId = " + runId + " ; ";
        sql += "  DELETE FROM exp.RunList WHERE ExperimentRunId = " + runId + " ; ";
        sql += "  DELETE FROM exp.ExperimentRun WHERE RowId = " + runId + " ; ";

        Table.execute(getExpSchema(), sql, new Object[]{});
    }


    private static DbSchema getExpSchema()
    {
        return DbSchema.get("exp");
    }

    public TableInfo getTinfoExperiment()
    {
        return getExpSchema().getTable("Experiment");
    }

    public TableInfo getTinfoExperimentRun()
    {
        return getExpSchema().getTable("ExperimentRun");
    }

    public TableInfo getTinfoExperimentRunMaterialInputs()
    {
        return getExpSchema().getTable("ExperimentRunMaterialInputs");
    }

    public TableInfo getTinfoExperimentRunMaterialOutputs()
    {
        return getExpSchema().getTable("ExperimentRunMaterialOutputs");
    }

    public TableInfo getTinfoExperimentRunDataInputs()
    {
        return getExpSchema().getTable("ExperimentRunDataInputs");
    }

    public TableInfo getTinfoExperimentRunDataOutputs()
    {
        return getExpSchema().getTable("ExperimentRunDataOutputs");
    }

    public TableInfo getTinfoProtocol()
    {
        return getExpSchema().getTable("Protocol");
    }

    public TableInfo getTinfoProtocolAction()
    {
        return getExpSchema().getTable("ProtocolAction");
    }

    public TableInfo getTinfoProtocolActionPredecessor()
    {
        return getExpSchema().getTable("ProtocolActionPredecessor");
    }

    public TableInfo getTinfoProtocolParameter()
    {
        return getExpSchema().getTable("ProtocolParameter");
    }

    public TableInfo getTinfoMaterial()
    {
        return getExpSchema().getTable("Material");
    }

    public TableInfo getTinfoMaterialInput()
    {
        return getExpSchema().getTable("MaterialInput");
    }

    public TableInfo getTinfoMaterialSource()
    {
        return getExpSchema().getTable("MaterialSource");
    }

    public TableInfo getTinfoMaterialSourceWithProject()
    {
        return getExpSchema().getTable("MaterialSourceWithProject");
    }

    public TableInfo getTinfoActiveMaterialSource()
    {
        return getExpSchema().getTable("ActiveMaterialSource");
    }

    public TableInfo getTinfoData()
    {
        return getExpSchema().getTable("Data");
    }

    public TableInfo getTinfoDataInput()
    {
        return getExpSchema().getTable("DataInput");
    }

    public TableInfo getTinfoProtocolApplication()
    {
        return getExpSchema().getTable("ProtocolApplication");
    }

    public TableInfo getTinfoProtocolActionDetails()
    {
        return getExpSchema().getTable("ProtocolActionStepDetailsView");
    }

    public TableInfo getTinfoProtocolApplicationParameter()
    {
        return getExpSchema().getTable("ProtocolApplicationParameter");
    }

    public TableInfo getTinfoProtocolActionPredecessorLSIDView()
    {
        return getExpSchema().getTable("ProtocolActionPredecessorLSIDView");
    }

    public TableInfo getTinfoRunList ()
    {
        return getExpSchema().getTable("RunList");
    }

    /**
     * return the object of any known experiment type that is identified with the LSID
     *
     * @return Object identified by this lsid or null if lsid not found
     */
    public Identifiable getObject(Lsid lsid)
    {
        LsidType type = findType(lsid);

        return null != type ? type.getObject(lsid) : null;
    }

    static final String findTypeSql = "SELECT Type FROM exp.AllLsid WHERE Lsid = ?";

    /**
     * @param lsid Full lsid we're looking for.
     * @return Object type for this lsid. Hmm should we reutnr a class
     */
    public LsidType findType(Lsid lsid)
    {
        //First check if we created this. If so, might be able to find without query
        if (AppProps.getInstance().getDefaultLsidAuthority().equals(lsid.getAuthority()))
        {
            LsidType type = LsidType.get(lsid.getNamespacePrefix());
            if (null != type)
                return type;
        }

        try
        {
            String typeName = Table.executeSingleton(getExpSchema(), findTypeSql, new Object[]{lsid.toString()}, String.class);
            return LsidType.get(typeName);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public Experiment getExperiment(String lsid)
    {
        if (null==lsid)
            return null;
        try
        {
            Experiment[] experiments =
                    Table.select(getTinfoExperiment(), Table.ALL_COLUMNS, new SimpleFilter("LSID", lsid), null, Experiment.class);
            if (null == experiments || experiments.length == 0)
                return null;
            else
            {
                return experiments[0];
            }
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ExpExperiment[] getExperiments(Container container)
    {
        Experiment[] experiments;
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", container.getId());
            Sort sort = new Sort("Name");
            sort.insertSort(new Sort("RowId"));
            experiments = Table.select(getTinfoExperiment(), Table.ALL_COLUMNS, filter, sort, Experiment.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        ExpExperiment[] ret = new ExpExperiment[experiments.length];
        for (int i = 0; i < experiments.length; i ++)
        {
            ret[i] = new ExpExperimentImpl(experiments[i]);
        }
        return ret;
    }

    public Experiment[] getExperimentsForRun(String runLsid)
    {
        Experiment[] experiments;
        try
        {
            final String sql= " SELECT E.* FROM " + getTinfoExperiment() + " E "
                            + " INNER JOIN " + getTinfoRunList() + " RL ON (E.RowId = RL.ExperimentId) "
                            + " INNER JOIN " + getTinfoExperimentRun() + " ER ON (ER.RowId = RL.ExperimentRunId) "
                            + " WHERE ER.LSID = ? ;"  ;

            experiments = Table.executeQuery(getExpSchema(), sql, new Object[]{runLsid}, Experiment.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        return experiments;
    }

    public ExperimentRun getExperimentRun(String LSID)
    {
        //Use main cache so updates/deletes through table layer get handled
        String cacheKey = getCacheKey(LSID);
        ExperimentRun run = (ExperimentRun) DbCache.get(getTinfoExperimentRun(), cacheKey);
        if (null != run)
            return run;

        SimpleFilter filter = new SimpleFilter("LSID", LSID);
        try
        {
            run = Table.selectObject(getTinfoExperimentRun(), Table.ALL_COLUMNS, filter, null, ExperimentRun.class);
            if (null != run)
                DbCache.put(getTinfoExperimentRun(), cacheKey, run);
            return run;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ExperimentRun getExperimentRun(int runId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", runId);
        try
        {
            return Table.selectObject(getTinfoExperimentRun(), Table.ALL_COLUMNS, filter, null, ExperimentRun.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public void clearCaches()
    {
        getMaterialSourceCache().clear();
    }

    public Protocol getProtocol(String protocolLSID)
    {
        try
        {
            return Table.selectObject(getTinfoProtocol(), Table.ALL_COLUMNS, new SimpleFilter("LSID", protocolLSID), null, Protocol.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public Protocol getProtocol(int rowId)
    {
        try
        {
            return Table.selectObject(getTinfoProtocol(), Table.ALL_COLUMNS, new SimpleFilter("RowId", rowId), null, Protocol.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

    }

    public ExpProtocolApplication getExpProtocolApplication(String lsid)
    {
        ProtocolApplication app = getProtocolApplication(lsid);
        return app == null ? null : new ExpProtocolApplicationImpl(app);
    }

    public ProtocolApplication getProtocolApplication(String lsid)
    {
        try
        {
            return Table.selectObject(getTinfoProtocolApplication(), Table.ALL_COLUMNS, new SimpleFilter("LSID", lsid), null, ProtocolApplication.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ProtocolAction[] getProtocolActions(int parentProtocolRowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ParentProtocolId", parentProtocolRowId);
        return Table.select(getTinfoProtocolAction(), Table.ALL_COLUMNS, filter, new Sort("+Sequence"), ProtocolAction.class);
    }

    public Material getMaterial(int materialId)
    {
        return Table.selectObject(getTinfoMaterial(), materialId, Material.class);
    }

    public Material getMaterial(String lsid)
    {
        try
        {
            return Table.selectObject(getTinfoMaterial(), Table.ALL_COLUMNS, new SimpleFilter("LSID", lsid), null, Material.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public List<Data> getRunInputData(String runLSID) throws SQLException
    {
        final String sql = "SELECT * FROM exp.ExperimentRunDataInputs WHERE RunLsid = ?";
        Map[] maps = Table.executeQuery(getExpSchema(), sql, new Object[]{runLSID}, Map.class);
        Map<String, List<Data>> data = getRunInputData(maps);
        List<Data> result = data.get(runLSID);
        if (result == null)
        {
            result = Collections.emptyList();
        }
        return result;
    }

    private Map<String, List<Data>> getRunInputData(Map[] maps)
    {
        Map<String, List<Data>>  outputMap = new HashMap<String, List<Data>>();
        BeanObjectFactory<Data> f = new BeanObjectFactory<Data>(Data.class);
        for (Map map : maps)
        {
            String runLSID = (String) map.get("RunLSID");
            List<Data> list = outputMap.get(runLSID);
            if (null == list)
            {
                list = new ArrayList<Data>();
                outputMap.put(runLSID, list);
            }
            Data d = f.fromMap(map);
            list.add(d);
        }
        return outputMap;
    }

    public List<Material> getRunInputMaterial(String runLSID) throws SQLException
    {
        final String sql = "SELECT * FROM " + getTinfoExperimentRunMaterialInputs() + " Where RunLSID = ?";
        Map[] maps = Table.executeQuery(getExpSchema(), sql, new Object[]{runLSID}, Map.class);
        Map<String, List<Material>> material = getRunInputMaterial(maps);
        List<Material> result = material.get(runLSID);
        if (result == null)
        {
            result = Collections.emptyList();
        }
        return result;
    }


    private Map<String, List<Material>> getRunInputMaterial(Map[] maps)
    {
        Map<String, List<Material>>  outputMap = new HashMap<String, List<Material>>();
        BeanObjectFactory<Material> f = new BeanObjectFactory<Material>(Material.class);
        for (Map map : maps)
        {
            String runLSID = (String) map.get("RunLSID");
            List<Material> list = outputMap.get(runLSID);
            if (null == list)
            {
                list = new ArrayList<Material>();
                outputMap.put(runLSID, list);
            }
            Material m = f.fromMap(map);
            list.add(m);
        }
        return outputMap;
    }

    public MaterialSource lookupActiveMaterialSource(Container c)
    {
        String sql = "SELECT ms.* " +
            "FROM " + getTinfoMaterialSource() + " ms, " + getTinfoActiveMaterialSource() + " ams " +
            "WHERE ms.lsid = ams.materialsourcelsid AND ams.container = ?";

        try
        {
            MaterialSource[] result = Table.executeQuery(getExpSchema(), sql, new Object[] {c.getId()}, MaterialSource.class);
            if (result.length == 1)
            {
                return result[0];
            }
            else if (result.length == 0)
            {
                return null;
            }
            else
            {
                throw new IllegalStateException("More than one active material source is set for container " + c);
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpSampleSet ensureActiveSampleSet(Container c) throws SQLException
    {
        MaterialSource result = lookupActiveMaterialSource(c);
        if (result == null)
        {
            return ensureDefaultSampleSet();
        }
        return new ExpSampleSetImpl(result);
    }

    public ExpSampleSet ensureDefaultSampleSet() throws SQLException
    {
        MaterialSource matSource = getMaterialSource(ExperimentService.get().getDefaultSampleSetLsid());

        if (null == matSource)
            return new ExpSampleSetImpl(createDefaultMaterialSource());
        else
            return new ExpSampleSetImpl(matSource);
    }

    private synchronized MaterialSource createDefaultMaterialSource() throws SQLException
    {
        //might have been created on another thread, so check within synch block
        MaterialSource matSource = getMaterialSource(ExperimentService.get().getDefaultSampleSetLsid());
        if (null == matSource)
        {
            matSource = new MaterialSource();
            matSource.setLSID(ExperimentService.get().getDefaultSampleSetLsid());
            matSource.setName(DEFAULT_MATERIAL_SOURCE_NAME);
            matSource.setMaterialLSIDPrefix(new Lsid("Sample", "Unspecified").toString() + "#");
            matSource.setContainer(ContainerManager.getSharedContainer().getId());
            matSource = insertMaterialSource(null, matSource, null);
        }

        return matSource;
    }

    public Material insertMaterial(User user, Material m) throws SQLException
    {
        return Table.insert(user, getTinfoMaterial(), m);
    }

    public Data insertData(User user, Data d) throws SQLException
    {
        return Table.insert(user, getTinfoData(), d);
    }

    public Map<String, ProtocolParameter> getProtocolParameters(int protocolRowId) throws SQLException
    {
        ProtocolParameter[] params = Table.select(getTinfoProtocolParameter(), Table.ALL_COLUMNS, new SimpleFilter("ProtocolId", protocolRowId), null, ProtocolParameter.class);
        Map<String, ProtocolParameter> result = new HashMap<String, ProtocolParameter>();
        for (ProtocolParameter param : params)
        {
            result.put(param.getOntologyEntryURI(), param);
        }
        return result;
    }

    public ExpDataImpl getDataByURL(File file, Container c) throws IOException
    {
        File canonicalFile = file.getCanonicalFile();
        String url = canonicalFile.toURI().toURL().toString();
        return getExpDataByURL(url, c);
    }

    public ExpDataImpl getExpDataByURL(String url, Container c)
    {
        try
        {
            Filter filter = new SimpleFilter().
                    addCondition("DataFileUrl", url).
                    addCondition("Container", c.getId());
            Sort sort = new Sort("-Created");
            Data data = Table.selectObject(getTinfoData(), Table.ALL_COLUMNS, filter, sort, Data.class);
            return data == null ? null : new ExpDataImpl(data);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Lsid getSampleSetLsid(String sourceName, Container container)
    {
        String setName = PageFlowUtil.encode(sourceName);

        return new Lsid("SampleSet", "Folder-" + String.valueOf(container.getRowId()), setName);
    }

    public void deleteExperimentByRowIds(Container container, int... selectedExperimentIds) throws SQLException, ExperimentException
    {
        if (selectedExperimentIds.length == 0)
            return;

        String experimentIds = StringUtils.join(toIntegers(selectedExperimentIds), ",");

        String sql = "SELECT LSID FROM exp.Experiment WHERE RowId IN (" + experimentIds + ")  AND Container = ? ;";
        String[] expLsids = Table.executeQuery(getExpSchema(), sql, new Object[]{container.getId()}, String.class);

        if (expLsids.length != selectedExperimentIds.length)
            _log.debug("deleteExperimentByRowIds:  LSIDs not found in container for all rowIds selected");

        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            sql = "DELETE FROM " + getTinfoRunList()
                    + " WHERE ExperimentId IN ("
                    + " SELECT E.RowId FROM " + getTinfoExperiment() + " E "
                    + " WHERE E.RowId IN ( " + experimentIds  + " ) "
                    + " AND E.Container = ? ); ";
            Table.execute(getExpSchema(), sql, new Object[]{container.getId()});

            for (String lsid: expLsids)
                    OntologyManager.deleteOntologyObject(container.getId(),lsid);

            sql = "  DELETE FROM " + getTinfoExperiment()
                    + " WHERE RowId IN ("+ experimentIds + ") "
                    + " AND Container = ? ";
            Table.execute(getExpSchema(), sql, new Object[]{container.getId()});

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }

    }

    public void dropRunsFromExperiment(String expLSID, int... selectedRunIds) throws SQLException
    {
        if (selectedRunIds.length == 0)
            return;
        ExpExperiment exp = getExpExperiment(expLSID);
        if (exp == null)
        {
            throw new SQLException("Attempting to remove Runs from an Experiment that does not exist");
        }

        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        String runIds = StringUtils.join(toIntegers(selectedRunIds), ",");

        String sql = " DELETE FROM " + getTinfoRunList() + " WHERE ExperimentId = ? "
                    + " AND ExperimentRunId IN ( " + runIds + " ) ; ";

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            Table.execute(getExpSchema(), sql, new Object[] {exp.getRowId()} );

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    public ExpExperimentImpl addRunsToExperiment(int expId, int... selectedRunIds) throws SQLException
    {
        ExpExperimentImpl exp = getExpExperiment(expId);
        if (exp == null)
        {
            throw new SQLException("Attempting to add Runs to an Experiment that does not exist");
        }

        if (selectedRunIds.length == 0)
            return exp;

        boolean containingTrans = getExpSchema().getScope().isTransactionActive();
        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            ExperimentRun[] existingRunIds = getRunsForExperiment(exp.getLSID());
            Set<Integer> newRuns = new HashSet<Integer>();
            for (int runId : selectedRunIds)
                newRuns.add(new Integer(runId));

            for (ExperimentRun er : existingRunIds)
            {
                if (newRuns.contains(er.getRowId()))
                    newRuns.remove(er.getRowId());
            }

            String sql = " INSERT INTO " + getTinfoRunList() + " ( ExperimentId, ExperimentRunId )  VALUES ( ? , ? ) ";
            for (Integer runId : newRuns)
            {
                Table.execute(getExpSchema(), sql, new Object[]{exp.getRowId(), runId});
            }

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();

            return exp;
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    public void deleteExperimentRunsByRowIds(Container container, User user, int... selectedRunIds) throws SQLException, ExperimentException
    {
        if (selectedRunIds == null || selectedRunIds.length == 0)
            return;

        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();
            
            for (int runId : selectedRunIds)
            {
                // Grab these to delete after we've deleted the Data rows
                ExpDataImpl[] datasToDelete = getAllDataOwnedByRun(runId);

                deleteRun(runId, container, datasToDelete, user);

                for (ExpData data : datasToDelete)
                {
                    ExperimentDataHandler handler = data.findDataHandler();
                    handler.deleteData(data, container, user);
                }

                try
                {
                    FileUtil.deleteDir(ExperimentRunGraph.getFolderDirectory(container.getRowId()));
                }
                catch (IOException e)
                {
                    // Non-fatal
                    _log.error("Failed to clear cached experiment run graphs for container " + container, e);
                }
            }

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    private int[] getRelatedProtocolIds(int[] selectedProtocolIds) throws SQLException
    {
        Set<Integer> allIds = new HashSet<Integer>();
        for (int selectedProtocolId : selectedProtocolIds)
        {
            allIds.add(new Integer(selectedProtocolId));
        }

        Set<Integer> idsToCheck = new HashSet<Integer>(allIds);
        while (!idsToCheck.isEmpty())
        {
            String idsString = StringUtils.join(idsToCheck.iterator(), ",");
            idsToCheck = new HashSet<Integer>();

            StringBuilder sb = new StringBuilder();
            sb.append("SELECT ParentProtocolId FROM exp.ProtocolAction WHERE ChildProtocolId IN (");
            sb.append(idsString);
            sb.append(");");
            Integer[] newIds = Table.executeArray(getExpSchema(), sb.toString(), new Object[]{}, Integer.class);

            idsToCheck.addAll(Arrays.asList(newIds));

            sb = new StringBuilder();
            sb.append("SELECT ChildProtocolId FROM exp.ProtocolAction WHERE ParentProtocolId IN (");
            sb.append(idsString);
            sb.append(");");
            newIds = Table.executeArray(getExpSchema(), sb.toString(), new Object[]{}, Integer.class);

            idsToCheck.addAll(Arrays.asList(newIds));
            idsToCheck.removeAll(allIds);
            allIds.addAll(idsToCheck);
        }

        return toInts(allIds);
    }

    public List<ExpRun> getExpRunsForProtocolIds(boolean includeRelated, int... protocolIds) throws SQLException
    {
        if (protocolIds.length == 0)
        {
            return Collections.emptyList();
        }

        int[] allProtocolIds;
        if (includeRelated)
        {
            allProtocolIds = getRelatedProtocolIds(protocolIds);
        }
        else
        {
            allProtocolIds = protocolIds;
        }

        String[] allProtocolLSIDs = Table.executeArray(getExpSchema(),
                "SELECT LSID FROM exp.Protocol WHERE RowId IN (" + StringUtils.join(toIntegers(allProtocolIds), ",") + ");",
                new Object[]{},
                String.class);

        if (allProtocolLSIDs.length == 0)
        {
            return Collections.emptyList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM ");
        sb.append(getTinfoExperimentRun().getFromSQL());
        sb.append(" WHERE ProtocolLSID IN (");
        for (int i = 0; i < allProtocolLSIDs.length; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append("'");
            sb.append(allProtocolLSIDs[i]);
            sb.append("' ");
        }
        sb.append(")");
        return Arrays.asList(ExpRunImpl.fromRuns(Table.executeQuery(getExpSchema(), sb.toString(), new Object[]{}, ExperimentRun.class)));
    }

    public void deleteProtocolByRowIds(Container c, User user, int... selectedProtocolIds) throws SQLException, ExperimentException
    {
        if (selectedProtocolIds.length == 0)
            return;

        List<ExpRun> runs = getExpRunsForProtocolIds(false, selectedProtocolIds);
        for (ExpRun run : runs)
        {
            deleteExperimentRunsByRowIds(c, user, run.getRowId());
        }

        String protocolIds = StringUtils.join(toIntegers(selectedProtocolIds), ",");

        String sql = "SELECT * FROM exp.Protocol WHERE RowId IN (" + protocolIds + ");";
        Protocol[] protocols = Table.executeQuery(getExpSchema(), sql, new Object[]{}, Protocol.class);

        sql = "SELECT RowId FROM exp.ProtocolAction ";
        sql += " WHERE (ChildProtocolId IN (" + protocolIds + ")";
        sql += " OR ParentProtocolId IN (" + protocolIds + ") );";
        Integer[] actionIds = Table.executeArray(getExpSchema(), sql, new Object[]{}, Integer.class);

        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            if (actionIds.length > 0)
            {
                for (Protocol protocol : protocols)
                {
                    ExpProtocol protocolToDelete = new ExpProtocolImpl(protocol);
                    AssayProvider provider = AssayService.get().getProvider(protocolToDelete);
                    if (provider != null)
                    {
                        provider.deleteProtocol(protocolToDelete, user);
                    }
                }

                String actionIdsJoined = "(" + StringUtils.join(actionIds, ",") + ")";
                sql = "DELETE FROM exp.ProtocolActionPredecessor WHERE ActionId IN " + actionIdsJoined + " OR PredecessorId IN " + actionIdsJoined + ";";
                Table.execute(getExpSchema(), sql, new Object[]{});

                sql = "DELETE FROM exp.ProtocolAction WHERE RowId IN " + actionIdsJoined + ";";
                Table.execute(getExpSchema(), sql, new Object[]{});
            }

            sql = "DELETE FROM exp.ProtocolParameter WHERE ProtocolId IN (" + protocolIds + ");";
            Table.execute(getExpSchema(), sql, new Object[]{});

            for (Protocol protocol : protocols)
            {
                if (!protocol.getContainer().equals(c.getId()))
                {
                    throw new SQLException("Attemping to delete a Protocol from another container");
                }
                DbCache.remove(getTinfoProtocol(), getCacheKey(protocol.getLSID()));
                OntologyManager.deleteOntologyObject(c.getId(), protocol.getLSID());
            }

            sql = "  DELETE FROM exp.Protocol WHERE RowId IN (" + protocolIds + ");";
            Table.execute(getExpSchema(), sql, new Object[]{});

            sql = "SELECT RowId FROM exp.Protocol Where RowId NOT IN (SELECT ParentProtocolId from exp.ProtocolAction UNION SELECT ChildProtocolId FROM exp.ProtocolAction) AND Container = ?";
            int[] orphanedProtocolIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));
            deleteProtocolByRowIds(c, user, orphanedProtocolIds);

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    private Integer[] toIntegers(int[] ints)
    {
        Integer[] result = new Integer[ints.length];
        for (int i = 0; i < ints.length; i++)
        {
            result[i] = new Integer(ints[i]);
        }
        return result;
    }

    private int[] toInts(Integer[] integers)
    {
        return toInts(Arrays.asList(integers));
    }

    private int[] toInts(Collection<Integer> integers)
    {
        int[] result = new int[integers.size()];
        int i = 0;
        for (Integer integer : integers)
        {
            result[i] = integer.intValue();
            i++;
        }
        return result;
    }

    public void deleteMaterialByRowIds(Container container, int... selectedMaterialIds) throws SQLException
    {
        if (selectedMaterialIds.length == 0)
            return;

        String materialIds = StringUtils.join(toIntegers(selectedMaterialIds), ",");

        String sql = "SELECT * FROM exp.Material WHERE RowId IN (" + materialIds + ");";
        Material[] materials = Table.executeQuery(getExpSchema(), sql, new Object[]{}, Material.class);
        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            for (Material material : materials)
            {
                if (!material.getContainer().equals(container.getId()))
                {
                    throw new SQLException("Attemping to delete a Material from another container");
                }
                OntologyManager.deleteOntologyObject(container.getId(), material.getLSID());
            }
            Table.execute(getExpSchema(),
                    "DELETE FROM exp.MaterialInput WHERE MaterialId IN (" + materialIds + ")",
                    new Object[0]);

            Table.execute(getExpSchema(),
                    "DELETE FROM exp.Material WHERE RowId IN (" + materialIds + ")",
                    new Object[]{});


            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    public void deleteDataByRowIds(Container container, int... selectedDataIds) throws SQLException
    {
        if (selectedDataIds.length == 0)
            return;

        String dataIds = StringUtils.join(toIntegers(selectedDataIds), ",");

        String sql = "SELECT * FROM exp.Data WHERE RowId IN (" + dataIds + ");";
        Data[] datas = Table.executeQuery(getExpSchema(), sql, new Object[]{}, Data.class);
        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            beforeDeleteData(ExpDataImpl.fromDatas(datas));
            for (Data data : datas)
            {
                if (!data.getContainer().equals(container.getId()))
                {
                    throw new SQLException("Attemping to delete a Data from another container");
                }
                OntologyManager.deleteOntologyObject(container.getId(), data.getLSID());
            }
            sql = "  DELETE FROM exp.Data WHERE RowId IN (" + dataIds + ");";
            Table.execute(getExpSchema(), sql, new Object[]{});

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    public void deleteAllExpObjInContainer(Container c, User user) throws Exception
    {
        if (null == c)
            return;

        String sql = "SELECT RowId FROM " + getTinfoExperimentRun() + " WHERE Container = ? ;";
        int[] runIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));

        sql = "SELECT RowId FROM " + getTinfoExperiment() + " WHERE Container = ? ;";
        int[] expIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));

        sql = "SELECT RowId FROM " + getTinfoMaterialSource() + " WHERE Container = ? ;";
        int[] srcIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));

        sql = "SELECT RowId FROM " + getTinfoProtocol() + " WHERE Container = ? ;";
        int[] protIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));

        try
        {
            getExpSchema().getScope().beginTransaction();
            // first delete the runs in the container, as that should be fast.  Deletes all Materials, Data,
            // and protocol applications and associated properties and parameters that belong to the run
            for (int runId : runIds)
            {
                deleteExperimentRunsByRowIds(c, user, runId);
            }
            for (ListDefinition list : ListService.get().getLists(c).values())
            {
                list.delete(null);
            }

            OntologyManager.deleteAllObjects(c);
            // delete material sources
            // now call the specialized function to delete the Materials that belong to the Material Source,
            // including the toplevel properties of the Materials, of which there are often many
            for (int srcId : srcIds)
            {
                deleteSampleSet(srcId, c, user);
            }


            SimpleFilter containerFilter = new SimpleFilter("container", c.getId());
            Table.delete(getTinfoActiveMaterialSource(), containerFilter);

            // now delete protocols (including their nested actions and parameters.
            deleteProtocolByRowIds(c, user, protIds);

            // now delete starting materials that were not associated with a MaterialSource upload.
            // we get this list now so that it doesn't innclude all of the run-scoped Materials that were
            // deleted already
            sql = "SELECT RowId FROM exp.Material WHERE Container = ? ;";
            int[] matIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));
            deleteMaterialByRowIds(c, matIds);

            // same drill for data objects
            sql = "SELECT RowId FROM exp.Data WHERE Container = ? ;";
            int[] dataIds = toInts(Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class));
            deleteDataByRowIds(c, dataIds);

            // the only thing left is Experiment itself
            deleteExperimentByRowIds(c, expIds);

            getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }

    public void moveContainer(Container c, Container oldParent, Container newParent) throws SQLException, ExperimentException
    {
        if (null == c)
            return;

        String sql = "SELECT RowId FROM " + getTinfoMaterialSource() + " WHERE Container = ? ;";
        Integer[] srcIds = Table.executeArray(getExpSchema(), sql, new Object[]{c.getId()}, Integer.class);

        try
        {
            getExpSchema().getScope().beginTransaction();


            OntologyManager.moveContainer(c, oldParent, newParent);

            // do the same for all of its children
            Container[] aCon = ContainerManager.getAllChildren(c);
            for (Container ctemp : aCon)
            {
                OntologyManager.moveContainer(ctemp, oldParent, newParent);
            }

            // delete material sources
            // now call the specialized function to delete the Materials that belong to the Material Source,
            // including the toplevel properties of the Materials, of which there are often many
            for (Integer srcId : srcIds)
            {
//                deleteSampleSet(srcId, c);
            }


            getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            getExpSchema().getScope().closeConnection();
        }
    }

    public ExpDataImpl[] getAllDataOwnedByRun(int runId) throws SQLException
    {
        Filter filter = new SimpleFilter("RunId", runId);
        return ExpDataImpl.fromDatas(Table.select(getTinfoData(), Table.ALL_COLUMNS, filter, null, Data.class));
    }

    public ExpData[] getAllDataUsedByRun(int runId) throws SQLException
    {
        StringBuilder sql = new StringBuilder();
        sql.append("select d.* from ");
        sql.append(getTinfoDataInput());
        sql.append(" di, ");
        sql.append(getTinfoData());
        sql.append(" d, ");
        sql.append(getTinfoProtocolApplication());
        sql.append(" pa where di.targetapplicationid=pa.rowid and pa.runid=? and di.dataid=d.rowid");
        return ExpDataImpl.fromDatas(Table.executeQuery(getSchema(), sql.toString(), new Object[] { runId }, Data.class));
    }

    public void moveRuns(ViewBackgroundInfo info, Container sourceContainer, List<ExpRun> runs) throws SQLException, IOException
    {
        int[] rowIds = new int[runs.size()];
        for (int i = 0; i < runs.size(); i++)
        {
            rowIds[i] = runs.get(i).getRowId();
        }

        MoveRunsPipelineJob job = new MoveRunsPipelineJob(info, sourceContainer, rowIds);
        PipelineService.get().queueJob(job);
    }


    private String getCacheKey(String lsid)
    {
        return "LSID/" + lsid;
    }

    private void beforeDeleteData(ExpData[] datas)
    {
        try
        {
            Map<ExperimentDataHandler, List<ExpData>> handlers = new HashMap<ExperimentDataHandler, List<ExpData>>();
            for (ExpData data : datas)
            {
                ExperimentDataHandler handler = data.findDataHandler();
                List<ExpData> list = handlers.get(handler);
                if (list == null)
                {
                    list = new ArrayList<ExpData>();
                    handlers.put(handler, list);
                }
                list.add(data);
            }
            for (Map.Entry<ExperimentDataHandler, List<ExpData>> entry : handlers.entrySet())
            {
                entry.getKey().beforeDeleteData(entry.getValue());
            }
        }
        catch (ExperimentException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public MaterialSource getMaterialSource(int rowId)
    {
        MaterialSource source = getMaterialSourceCache().get(String.valueOf(rowId));
        if (null == source)
        {
            source = Table.selectObject(getTinfoMaterialSource(), rowId, MaterialSource.class);
            getMaterialSourceCache().put(String.valueOf(rowId), source);
        }
        return source;
    }

    public MaterialSource getMaterialSource(String lsid)
    {
        SimpleFilter filter = new SimpleFilter("LSID", lsid);
        MaterialSource source;
        try
        {
            source = Table.selectObject(getTinfoMaterialSource(), Table.ALL_COLUMNS, filter, null, MaterialSource.class);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }

        return source;
    }


    public MaterialSource insertMaterialSource(User user, MaterialSource source, DomainDescriptor dd) throws SQLException
    {
        assert 0 == source.getRowId();
        source = Table.insert(user, getTinfoMaterialSource(), source);
        Container container = ContainerManager.getForId(source.getContainer());
        if (dd == null)
        {
            Domain domain = PropertyService.get().getDomain(container, source.getLSID());
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(container, source.getLSID(), source.getName());
                try
                {
                    domain.save(user);
                }
                catch (Exception e)
                {
                    throw (SQLException)new SQLException().initCause(e);
                }
            }
        }

        getMaterialSourceCache().put(String.valueOf(source.getRowId()), source);
        ExpSampleSet activeSampleSet = lookupActiveSampleSet(container);
        if (activeSampleSet == null)
        {
            setActiveSampleSet(container, new ExpSampleSetImpl(source));
        }
        return source;
    }

    public String getDefaultSampleSetLsid()
    {
        return new Lsid("SampleSource", "Default").toString();
    }

    public List<ExpRun> getRunsUsingDatas(List<ExpData> datas) throws SQLException
    {
        StringBuilder dataRowIdSQL = new StringBuilder();
        String separator = "";
        for (ExpData data : datas)
        {
            dataRowIdSQL.append(separator);
            separator = ", ";
            dataRowIdSQL.append(data.getRowId());
        }

        ExperimentRun[] runs = Table.executeQuery(getExpSchema(), "SELECT * FROM " + getTinfoExperimentRun() + " WHERE \n" +
                "RowId IN " +
                "(SELECT pa.RunId FROM " + getTinfoProtocolApplication() + " pa, " + getTinfoDataInput() + " di " +
                "WHERE di.TargetApplicationId = pa.RowId AND di.DataID IN (" + dataRowIdSQL + ")) \n" +
                "OR RowId IN " +
                "(SELECT pa.RunId FROM " + getTinfoProtocolApplication() + " pa, " + getTinfoData() + " d " +
                "WHERE d.SourceApplicationId = pa.RowId AND d.RowId IN (" + dataRowIdSQL + "))", new Object[0], ExperimentRun.class);
        return Arrays.asList(ExpRunImpl.fromRuns(runs));
    }

    public ExpRun[] getRunsUsingMaterials(int... ids) throws SQLException
    {
        String materialRowIdSQL = StringUtils.join(toIntegers(ids), ", ");
        return ExpRunImpl.fromRuns(getRunsForMaterialList(materialRowIdSQL));
    }

    public List<ExpRun> runsDeletedWithInput(ExpRun[] runs) throws SQLException
    {
        List<ExpRun> ret = new ArrayList<ExpRun>();
        for (ExpRun run : runs)
        {
            ExpProtocol protocol = run.getProtocol();
            if (protocol != null)
            {
                ProtocolImplementation impl = protocol.getImplementation();
                if (impl != null)
                {
                    if (!impl.deleteRunWhenInputDeleted())
                    {
                        continue;
                    }
                }
            }
            ret.add(run);
        }
        return ret;
    }

    public ExpRun[] getRunsUsingSampleSets(ExpSampleSet... source) throws SQLException
    {
        Object[] params = new Object[source.length];
        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (int i = 0; i < source.length; i++)
        {
            params[i] = source[i].getLSID();
            sb.append(separator);
            sb.append("?");
            separator = ", ";
        }
        String materialRowIdSQL = "SELECT RowId FROM " + getTinfoMaterial() + " WHERE CpasType IN (" + sb.toString() + ")";
        return ExpRunImpl.fromRuns(getRunsForMaterialList(materialRowIdSQL, params));
    }

    private ExperimentRun[] getRunsForMaterialList(String materialRowIdSQL, Object... params) throws SQLException
    {
        Object[] doubledParams = new Object[params.length * 2];
        System.arraycopy(params, 0, doubledParams, 0, params.length);
        System.arraycopy(params, 0, doubledParams, params.length, params.length);
        return Table.executeQuery(getExpSchema(), "SELECT * FROM " + getTinfoExperimentRun() + " WHERE \n" +
            "RowId IN (SELECT RowId FROM " + getTinfoExperimentRun() + " WHERE RowId IN " +
                "(SELECT pa.RunId FROM " + getTinfoProtocolApplication() + " pa, " + getTinfoMaterialInput() + " mi " +
                "WHERE mi.TargetApplicationId = pa.RowId AND mi.MaterialID IN (" + materialRowIdSQL + ")) \n" +
            "UNION " +
                "(SELECT pa.RunId FROM " + getTinfoProtocolApplication() + " pa, " + getTinfoMaterial() + " m " +
                "WHERE m.SourceApplicationId = pa.RowId AND m.RowId IN (" + materialRowIdSQL + ")))", doubledParams, ExperimentRun.class);
    }

    public void deleteSampleSet(int rowId, Container c, User user) throws SQLException, ExperimentException
    {
        ExpSampleSet source = getSampleSet(rowId);
        if (null == source)
            throw new IllegalArgumentException("Can't find SampleSet with rowId " + rowId);
        if (!source.getContainer().equals(c))
        {
            throw new SQLException("Trying to delete a SampleSet from a different container");
        }
        boolean containingTrans = getExpSchema().getScope().isTransactionActive();

        try
        {
            if (!containingTrans)
                getExpSchema().getScope().beginTransaction();

            for (ExpRun run : getRunsUsingSampleSets(source))
            {
                deleteExperimentRunsByRowIds(run.getContainer(), user, run.getRowId());
            }

            //Delete all materials in this source
            SimpleFilter materialFilter = new SimpleFilter("CpasType", source.getLSID());
            Table.delete(getTinfoMaterial(), materialFilter);

            //Delete everything the ontology knows about this
            //includes all properties where this is the owner.
            OntologyManager.deleteOntologyObject(source.getContainer().getId(), source.getLSID());
            if (OntologyManager.getDomainDescriptor(source.getLSID(), c) != null)
            {
                try
                {
                    OntologyManager.deleteType(source.getLSID(), c);
                }
                catch (DomainNotFoundException e)
                {
                    throw new ExperimentException(e);
                }
            }

            Table.execute(getExpSchema(), "DELETE FROM " + getTinfoActiveMaterialSource() + " WHERE MaterialSourceLSID = ?", new Object[] { source.getLSID() } );

            Table.delete(getTinfoMaterialSource(), rowId, null);

            if (!containingTrans)
                getExpSchema().getScope().commitTransaction();
        }
        finally
        {
            if (!containingTrans)
                getExpSchema().getScope().closeConnection();
        }
    }

    public ExpRunImpl populateRun(ExpRunImpl expRun)
    {
        //todo cache populated runs
        try
        {
            Map<Integer, ExpMaterial> outputMaterialMap = new HashMap<Integer, ExpMaterial>();
            Map<Integer, ExpDataImpl> outputDataMap = new HashMap<Integer, ExpDataImpl>();

            int runId = expRun.getRowId();
            SimpleFilter filt = new SimpleFilter("RunId", runId);
            Sort sort = new Sort("ActionSequence, RowId");
            ExpProtocolApplicationImpl[] protocolSteps = ExpProtocolApplicationImpl.fromProtocolApplications(Table.select(getTinfoProtocolApplication(), getTinfoProtocolApplication().getColumns(), filt, sort, ProtocolApplication.class));
            expRun.setProtocolApplications(protocolSteps);
            Map<Integer, ExpProtocolApplication> protStepMap = new HashMap<Integer, ExpProtocolApplication>(protocolSteps.length);
            for (ExpProtocolApplicationImpl protocolStep : protocolSteps)
            {
                protStepMap.put(protocolStep.getRowId(), protocolStep);
                protocolStep.setInputMaterials(new ArrayList<ExpMaterial>());
                protocolStep.setInputDatas(new ArrayList<ExpData>());
                protocolStep.setOutputMaterials(new ArrayList<ExpMaterial>());
                protocolStep.setOutputDatas(new ArrayList<ExpData>());
            }

            sort = new Sort("RowId");

            ExpMaterial[] materials = ExpMaterialImpl.fromMaterials(Table.select(getTinfoMaterial(), getTinfoMaterial().getColumns(), filt, sort, Material.class));
            Map<Integer, ExpMaterial> runMaterialMap = new HashMap<Integer, ExpMaterial>(materials.length);
            for (ExpMaterial mat : materials)
            {
                runMaterialMap.put(mat.getRowId(), mat);
                ExpProtocolApplication sourceApplication = mat.getSourceApplication();
                Integer srcAppId = sourceApplication == null ? null : sourceApplication.getRowId();
                assert protStepMap.containsKey(srcAppId);
                protStepMap.get(srcAppId).getOutputMaterials().add(mat);
                mat.storeSourceApp(protStepMap.get(srcAppId));
                mat.storeSuccessorAppList(new ArrayList<ExpProtocolApplication>());
                mat.storeSuccessorRunIdList(new ArrayList<Integer>());
            }

            ExpDataImpl[] datas = ExpDataImpl.fromDatas(Table.select(getTinfoData(), getTinfoData().getColumns(), filt, sort, Data.class));
            Map<Integer, ExpDataImpl> runDataMap = new HashMap<Integer, ExpDataImpl>(datas.length);
            for (ExpDataImpl dat : datas)
            {
                runDataMap.put(dat.getRowId(), dat);
                Integer srcAppId = dat.getDataObject().getSourceApplicationId();
                assert protStepMap.containsKey(srcAppId);
                protStepMap.get(srcAppId).getOutputDatas().add(dat);
                dat.storeSourceApp(protStepMap.get(srcAppId));
                dat.storeSuccessorAppList(new ArrayList<ExpProtocolApplication>());
                dat.storeSuccessorRunIdList(new ArrayList<Integer>());
            }

            // get the set of starting materials, which do not belong to the run
            String materialSQL = "SELECT M.* "
                    + " FROM " + getTinfoMaterial().getFromSQL() + " M "
                    + " INNER JOIN " + getExpSchema().getTable("MaterialInput").getFromSQL() + " MI "
                    + " ON (M.RowId = MI.MaterialId) "
                    + " WHERE MI.TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExperimentService.EXPERIMENT_RUN_CPAS_TYPE + "')"
                    + "  ORDER BY RowId ;";
            String materialInputSQL = "SELECT MI.* "
                    + " FROM " + getExpSchema().getTable("MaterialInput").getFromSQL() + " MI "
                    + " WHERE MI.TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExperimentService.EXPERIMENT_RUN_CPAS_TYPE + "')"
                    + "  ORDER BY MI.MaterialId;";
            Object[] params = {new Integer(runId)};
            materials = ExpMaterialImpl.fromMaterials(Table.executeQuery(getExpSchema(), materialSQL, params, Material.class));
            MaterialInput[] materialInputs = Table.executeQuery(getExpSchema(), materialInputSQL, params, MaterialInput.class);
            assert materials.length == materialInputs.length;
            Map<Integer, ExpMaterial> startingMaterialMap = new HashMap<Integer, ExpMaterial>(materials.length);
            int index = 0;
            for (ExpMaterial mat : materials)
            {
                startingMaterialMap.put(mat.getRowId(), mat);
                MaterialInput input = materialInputs[index++];
                String roleName = null;
                if (input != null && input.getPropertyId() != null)
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(input.getPropertyId().intValue());
                    if (pd != null)
                    {
                        roleName = pd.getName();
                    }
                }
                expRun.getMaterialInputs().put(mat, roleName);
                mat.storeSuccessorAppList(new ArrayList<ExpProtocolApplication>());
            }

            // and starting data
            String dataSQL = "SELECT D.*"
                    + " FROM " + getTinfoData().getFromSQL() + " D "
                    + " INNER JOIN " + getTinfoDataInput().getFromSQL() + " DI "
                    + " ON (D.RowId = DI.DataId) "
                    + " WHERE DI.TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExperimentService.EXPERIMENT_RUN_CPAS_TYPE + "')"
                    + "  ORDER BY RowId ;";
            String dataInputSQL = "SELECT DI.*"
                    + " FROM " + getTinfoDataInput().getFromSQL() + " DI "
                    + " WHERE DI.TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? AND PA.CpasType= '" + ExperimentService.EXPERIMENT_RUN_CPAS_TYPE + "')"
                    + "  ORDER BY DataId;";
            datas = ExpDataImpl.fromDatas(Table.executeQuery(getExpSchema(), dataSQL, params, Data.class));
            DataInput[] dataInputs = Table.executeQuery(getExpSchema(), dataInputSQL, params, DataInput.class); 
            Map<Integer, ExpDataImpl> startingDataMap = new HashMap<Integer, ExpDataImpl>(datas.length);
            index = 0;
            for (ExpDataImpl dat : datas)
            {
                startingDataMap.put(dat.getRowId(), dat);
                DataInput input = dataInputs[index++];
                String roleName = null;
                if (input != null && input.getPropertyId() != null)
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(input.getPropertyId().intValue());
                    if (pd != null)
                    {
                        roleName = pd.getName();
                    }
                }
                expRun.getDataInputs().put(dat, roleName);
                dat.storeSuccessorAppList(new ArrayList<ExpProtocolApplication>());
            }

            // now hook up material inputs to processes in both directions
            dataSQL = "SELECT TargetApplicationId, MaterialId "
                    + " FROM " + getTinfoMaterialInput().getFromSQL()
                    + " WHERE TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? ) "
                    + " ORDER BY TargetApplicationId, MaterialId ;";
            ResultSet materialInputRS = null;
            try
            {
                materialInputRS = Table.executeQuery(getExpSchema(), dataSQL, new Object[]{new Integer(runId)});
                while (materialInputRS.next())
                {
                    Integer appId = materialInputRS.getInt("TargetApplicationId");
                    Integer matId = materialInputRS.getInt("MaterialId");
                    ExpProtocolApplication pa = protStepMap.get(appId);
                    ExpMaterial mat;

                    if (runMaterialMap.containsKey(matId))
                        mat = runMaterialMap.get(matId);
                    else
                        mat = startingMaterialMap.get(matId);

                    if (mat == null)
                    {
                        mat = getExpMaterial(matId);
                    }

                    pa.getInputMaterials().add(mat);
                    mat.retrieveSuccessorAppList().add(pa);

                    if (pa.getCpasType().equals(ExperimentService.EXPERIMENT_RUN_OUTPUT_CPAS_TYPE))
                    {
                        expRun.getMaterialOutputs().add(mat);
                        outputMaterialMap.put(mat.getRowId(), mat);
                    }
                }
            }
            finally
            {
                if (materialInputRS != null) { try { materialInputRS.close(); } catch (SQLException e) {} }
            }

            // now hook up data inputs in both directions
            dataSQL = "SELECT TargetApplicationId, DataId "
                    + " FROM " + getTinfoDataInput().getFromSQL()
                    + " WHERE TargetApplicationId IN "
                    + " (SELECT PA.RowId FROM " + getTinfoProtocolApplication().getFromSQL() + " PA "
                    + " WHERE PA.RunId = ? ) "
                    + " ORDER BY TargetApplicationId, DataId ;";

            ResultSet dataInputRS = null;
            try
            {
                dataInputRS = Table.executeQuery(getExpSchema(), dataSQL, new Object[]{new Integer(runId)});
                while (dataInputRS.next())
                {
                    Integer appId = dataInputRS.getInt("TargetApplicationId");
                    Integer datId = dataInputRS.getInt("DataId");
                    ExpProtocolApplication pa = protStepMap.get(appId);
                    ExpDataImpl dat;

                    if (runDataMap.containsKey(datId))
                        dat = runDataMap.get(datId);
                    else
                        dat = startingDataMap.get(datId);

                    pa.getInputDatas().add(dat);
                    dat.retrieveSuccessorAppList().add(pa);

                    if (pa.getCpasType().equals(ExperimentService.EXPERIMENT_RUN_OUTPUT_CPAS_TYPE))
                    {
                        expRun.getDataOutputs().add(dat);
                        outputDataMap.put(dat.getRowId(), dat);
                    }
                }
            }
            finally
            {
                if (dataInputRS != null ) { try { dataInputRS.close(); } catch (SQLException e) {} }
            }

            //For run summary view, need to know if other ExperimentRuns
            // use the outputs of this run.
            if (outputMaterialMap.keySet().size() > 0)
            {
                String inClause = StringUtils.join(outputMaterialMap.keySet().iterator(), ", ");
                dataSQL = "SELECT TargetApplicationId, MaterialId, PA.RunId "
                        + " FROM " + getTinfoMaterialInput().getFromSQL() + " M  "
                        + " INNER JOIN " + getTinfoProtocolApplication().getFromSQL() + " PA "
                        + " ON M.TargetApplicationId = PA.RowId "
                        + " WHERE MaterialId IN ( " + inClause + " ) "
                        + " AND PA.RunId <> ? "
                        + " ORDER BY TargetApplicationId, MaterialId ;";
                ResultSet materialOutputRS = null;
                try
                {
                    materialOutputRS = Table.executeQuery(getExpSchema(), dataSQL, new Object[]{new Integer(runId)});
                    while (materialOutputRS.next())
                    {
                        Integer successorRunId = materialOutputRS.getInt("RunId");
                        Integer matId = materialOutputRS.getInt("MaterialId");
                        ExpMaterial mat = outputMaterialMap.get(matId);
                        mat.retrieveSuccessorRunIdList().add(successorRunId);
                    }
                }
                finally
                {
                    if (materialOutputRS != null) { try { materialOutputRS.close(); } catch (SQLException e) {} }
                }
            }

            if (outputDataMap.keySet().size() > 0)
            {
                String inClause = StringUtils.join(outputDataMap.keySet().iterator(), ", ");
                dataSQL = "SELECT TargetApplicationId, DataId, PA.RunId "
                        + " FROM " + getTinfoDataInput().getFromSQL() + " D  "
                        + " INNER JOIN " + getTinfoProtocolApplication().getFromSQL() + " PA "
                        + " ON D.TargetApplicationId = PA.RowId "
                        + " WHERE DataId IN ( " + inClause + " ) "
                        + " AND PA.RunId <> ? "
                        + " ORDER BY TargetApplicationId, DataId ;";
                ResultSet dataOutputRS = null;
                try
                {
                    dataOutputRS = Table.executeQuery(getExpSchema(), dataSQL, new Object[]{new Integer(runId)});
                    while (dataOutputRS.next())
                    {
                        Integer successorRunId = dataOutputRS.getInt("RunId");
                        Integer datId = dataOutputRS.getInt("DataId");
                        ExpData dat = outputDataMap.get(datId);
                        dat.retrieveSuccessorRunIdList().add(successorRunId);
                    }
                }
                finally
                {
                    if (dataOutputRS != null) { try { dataOutputRS.close(); } catch (SQLException e) {} }
                }
            }

            return expRun;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public void trimRunTree(ExpRunImpl populatedRun, Integer id, String type) throws SQLException, ExperimentException
    {
        List<ExpProtocolApplication> listPA = new ArrayList<ExpProtocolApplication>();
        List<ExpMaterial> listM = new ArrayList<ExpMaterial>();
        List<ExpData> listD = new ArrayList<ExpData>();
        List<ExpProtocolApplication> ancestorPAStack = new ArrayList<ExpProtocolApplication>();
        List<ExpProtocolApplication> descendantPAStack = new ArrayList<ExpProtocolApplication>();
        ExpProtocolApplication [] apps = populatedRun.getProtocolApplications();

        boolean found = false;

        // support focus on a starting material that is not part of the run
        if (type.equals(TYPECODE_MATERIAL))
        {
            for (ExpMaterial m : populatedRun.getMaterialInputs().keySet())
                if (m.getRowId() == id.intValue())
                {
                    found = true;
                    listM.add(m);
                    listPA.addAll(m.retrieveSuccessorAppList());
                    descendantPAStack.addAll(m.retrieveSuccessorAppList());
                    break;
                }
        }
        if (type.equals(TYPECODE_DATA))
        {
            for (ExpData d : populatedRun.getDataInputs().keySet())
                if (d.getRowId() == id.intValue())
                {
                    found = true;
                    listD.add(d);
                    listPA.addAll(d.retrieveSuccessorAppList());
                    descendantPAStack.addAll(d.retrieveSuccessorAppList());
                    break;
                }
        }
        if (!found)
        {
            for (ExpProtocolApplication app : apps)
            {
                if (type.equals(TYPECODE_MATERIAL))
                {
                    List<ExpMaterial> outputMat = app.getOutputMaterials();
                    for (ExpMaterial m : outputMat)
                        if (m.getRowId() == id.intValue())
                        {
                            found = true;
                            listM.add(m);
                            listPA.addAll(m.retrieveSuccessorAppList());
                            descendantPAStack.addAll(m.retrieveSuccessorAppList());
                            if (null != m.getSourceApplication() && m.getRun() != null && populatedRun.getRowId() == m.getRun().getRowId())
                            {
                                listPA.add(m.retrieveSourceApp());
                                ancestorPAStack.add(m.retrieveSourceApp());
                            }
                            break;
                        }
                }
                if (type.equals(TYPECODE_DATA))
                {
                    for (ExpData d : app.getOutputDatas())
                    {
                        if (d.getRowId() == id.intValue())
                        {
                            found = true;
                            listD.add(d);
                            listPA.addAll(d.retrieveSuccessorAppList());
                            descendantPAStack.addAll(d.retrieveSuccessorAppList());
                            if (null != d.getSourceApplication() && d.getRun() != null && populatedRun.getRowId() == d.getRun().getRowId())
                            {
                                listPA.add(d.retrieveSourceApp());
                                ancestorPAStack.add(d.retrieveSourceApp());
                            }
                            break;
                        }
                    }
                }
                if (type.equals(TYPECODE_PROT_APP))
                {
                    if (app.getRowId() == id.intValue())
                    {
                        found = true;
                        listPA.add(app);
                        ancestorPAStack.add(app);
                        descendantPAStack.add(app);
                        break;
                    }
                }
                if (found)
                    break;
            }
        }
        if (!found)
            throw new ExperimentException("Specified node not found in Experiment Run");


        while (descendantPAStack.size() > 0)
        {
            ExpProtocolApplication pa = descendantPAStack.get(0);
            for (ExpMaterial m : pa.getOutputMaterials())
            {
                listM.add(m);
                descendantPAStack.addAll(m.retrieveSuccessorAppList());
            }
            for (ExpData d : pa.getOutputDatas())
            {
                listD.add(d);
                descendantPAStack.addAll(d.retrieveSuccessorAppList());
            }
            descendantPAStack.remove(pa);
            listPA.add(pa);
        }


        while (ancestorPAStack.size() > 0)
        {
            ExpProtocolApplication pa = ancestorPAStack.get(0);
            if (pa.getCpasType().equals(ExperimentService.EXPERIMENT_RUN_CPAS_TYPE))
                break;
            for (ExpMaterial m : pa.getInputMaterials())
            {
                listM.add(m);
                if (populatedRun.getMaterialInputs().containsKey(m))
                {
                    ExpProtocolApplication runNode = populatedRun.getProtocolApplications()[0];
                    assert runNode.getCpasType().equals(ExperimentService.EXPERIMENT_RUN_CPAS_TYPE);
                    listPA.add(runNode);
                    continue;
                }
                if (null != m.getSourceApplication() && m.getRun() != null && populatedRun.getRowId() == m.getRun().getRowId())
                    ancestorPAStack.add(m.retrieveSourceApp());
            }
            for (ExpData d : pa.getInputDatas())
            {
                listD.add(d);
                if (populatedRun.getDataInputs().containsKey(d))
                {
                    ExpProtocolApplication runNode = populatedRun.getProtocolApplications()[0];
                    assert runNode.getCpasType().equals(ExperimentService.EXPERIMENT_RUN_CPAS_TYPE);
                    listPA.add(runNode);
                    continue;
                }
                if (null != d.getSourceApplication() && d.getRun() != null && populatedRun.getRowId() == d.getRun().getRowId())
                    ancestorPAStack.add(d.retrieveSourceApp());
            }
            ancestorPAStack.remove(pa);
            listPA.add(pa);
        }

        ArrayList<ExpProtocolApplication> allPA = new ArrayList<ExpProtocolApplication>();
        ArrayList<ExpProtocolApplication> deletePA;
        ArrayList<ExpMaterial> deleteM;
        ArrayList<ExpData> deleteD;

        populatedRun.setProtocolApplications(null);

        for (ExpProtocolApplication app : apps)
        {
            if (listPA.contains(app))
            {
                allPA.add(app);
                deleteM = new ArrayList<ExpMaterial>();
                for (ExpMaterial m : app.getInputMaterials())
                {
                    if (listM.contains(m))
                    {
                        deletePA = new ArrayList<ExpProtocolApplication>();
                        for (ExpProtocolApplication p : m.retrieveSuccessorAppList())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            m.retrieveSuccessorAppList().remove(p);
                    }
                    else
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                {
                    app.getInputMaterials().remove(m);
                    populatedRun.getMaterialInputs().remove(m);
                }

                deleteD = new ArrayList<ExpData>();
                for (ExpData d : app.getInputDatas())
                {
                    if (listD.contains(d))
                    {
                        deletePA = new ArrayList<ExpProtocolApplication>();
                        for (ExpProtocolApplication p : d.retrieveSuccessorAppList())
                            if (!listPA.contains(p))
                                deletePA.add(p);
                        for (ExpProtocolApplication p : deletePA)
                            d.retrieveSuccessorAppList().remove(p);
                    }
                    else
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                {
                    app.getInputDatas().remove(d);
                    populatedRun.getDataInputs().remove(d);
                }

                deleteM = new ArrayList<ExpMaterial>();
                for (ExpMaterial m : app.getOutputMaterials())
                {
                    if (!listM.contains(m))
                        deleteM.add(m);
                }
                for (ExpMaterial m : deleteM)
                    app.getOutputMaterials().remove(m);


                deleteD = new ArrayList<ExpData>();
                for (ExpData d : app.getOutputDatas())
                {
                    if (!listD.contains(d))
                        deleteD.add(d);
                }
                for (ExpData d : deleteD)
                    app.getOutputDatas().remove(d);
            }
        }
        populatedRun.setProtocolApplications(allPA.toArray(new ExpProtocolApplicationImpl[allPA.size()]));
    }


    public ProtocolActionPredecessor[] getProtocolActionPredecessors(String parentProtocolLSID, String childProtocolLSID) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ChildProtocolLSID", childProtocolLSID);
        filter.addCondition("ParentProtocolLSID", parentProtocolLSID);
        return Table.select(getTinfoProtocolActionPredecessorLSIDView(), Table.ALL_COLUMNS, filter, new Sort("+PredecessorSequence"), ProtocolActionPredecessor.class);
    }

    public Data[] getOutputDataForApplication(int applicationId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("SourceApplicationId", applicationId);
            return Table.select(getTinfoData(), Table.ALL_COLUMNS, filter, null, Data.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Material[] getOutputMaterialForApplication(int applicationId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("SourceApplicationId", applicationId);
            return Table.select(getTinfoMaterial(), Table.ALL_COLUMNS, filter, null, Material.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpData[] getExpData(Container c) throws SQLException
    {
        return ExpDataImpl.fromDatas(Table.select(getTinfoData(), Table.ALL_COLUMNS, new SimpleFilter("Container", c.getId()), null, Data.class));
    }

    public Data[] getDataInputReferencesForApplication(int rowId) throws SQLException
    {
        String outputSQL = "SELECT exp.Data.* from exp.Data, exp.DataInput " +
                "WHERE exp.Data.RowId = exp.DataInput.DataId " +
                "AND exp.DataInput.TargetApplicationId = ?";
        return Table.executeQuery(getExpSchema(), outputSQL, new Object[]{rowId}, Data.class);
    }

    public DataInput[] getDataInputsForApplication(int applicationId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("TargetApplicationId", applicationId);
            return Table.select(getTinfoDataInput(), Table.ALL_COLUMNS, filter, null, DataInput.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Material[] getMaterialInputReferencesForApplication(int rowId) throws SQLException
    {
        String outputSQL = "SELECT exp.Material.* from exp.Material, exp.MaterialInput " +
                "WHERE exp.Material.RowId = exp.MaterialInput.MaterialId " +
                "AND exp.MaterialInput.TargetApplicationId = ?";
        return Table.executeQuery(getExpSchema(), outputSQL, new Object[]{rowId}, Material.class);
    }

    public MaterialInput[] getMaterialInputsForApplication(int applicationId)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("TargetApplicationId", applicationId);
            return Table.select(getTinfoMaterialInput(), Table.ALL_COLUMNS, filter, null, MaterialInput.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e); 
        }
    }

    public ProtocolApplicationParameter[] getProtocolApplicationParameters(int rowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ProtocolApplicationId", rowId);
        return Table.select(getTinfoProtocolApplicationParameter(), Table.ALL_COLUMNS, filter, null, ProtocolApplicationParameter.class);
    }

    public ProtocolActionStepDetail getProtocolActionStepDetail(String parentProtocolLSID, Integer actionSequence) throws XarFormatException, SQLException
    {
        String cmdSql = "SELECT * FROM exp.ProtocolActionStepDetailsView "
                + " WHERE ParentProtocolLSID = ? "
                + " AND Sequence = ? "
                + " ORDER BY Sequence";

        Object [] params = new Object[]{parentProtocolLSID, actionSequence};
        ProtocolActionStepDetail[] details = Table.executeQuery(getExpSchema(), cmdSql, params, ProtocolActionStepDetail.class);
        if (null == details || details.length == 0)
        {
            return null;
        }
        assert (details.length == 1);
        return details[0];
    }

    public ExpProtocolApplication[] getExpProtocolApplicationsForProtocolLSID(String protocolLSID) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ProtocolLSID", protocolLSID);
        return ExpProtocolApplicationImpl.fromProtocolApplications(Table.select(getTinfoProtocolApplication(), Table.ALL_COLUMNS, filter, null, ProtocolApplication.class));
    }

    public ExperimentRun[] getRunsForExperiment(String experimentLSID)
    {
        try
        {
            String sql = "SELECT ER.* FROM " +  getTinfoExperiment() + " E "
                    + " INNER JOIN " + getTinfoRunList()  + " RL ON (E.RowId = RL.ExperimentId) "
                    + " INNER JOIN " + getTinfoExperimentRun()  + " ER ON (ER.RowId = RL.ExperimentRunId) "
                    + " WHERE E.LSID = ?" ;

            return Table.executeQuery(getExpSchema(), sql, new Object[]{experimentLSID}, ExperimentRun.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Protocol saveProtocol(User user, Protocol protocol)
    {
        try
        {
            Protocol result;
            boolean newProtocol = protocol.getRowId() == 0;
            if (newProtocol)
            {
                result = Table.insert(user, getTinfoProtocol(), protocol);
            }
            else
            {
                result = Table.update(user, getTinfoProtocol(), protocol, protocol.getRowId(), null);
            }

            Collection<ProtocolParameter> protocolParams = protocol.retrieveProtocolParameters().values();
            if (!newProtocol)
            {
                Table.execute(getExpSchema(), "DELETE FROM exp.ProtocolParameter WHERE ProtocolId = ?", new Object[]{protocol.getRowId()});
            }
            for (ProtocolParameter param : protocolParams)
            {
                param.setProtocolId(result.getRowId());
                loadParameter(user, param, getTinfoProtocolParameter(), "ProtocolId", protocol.getRowId());
            }

            savePropertyCollection(protocol.retrieveObjectProperties(), protocol.getLSID(), protocol.getContainer(), !newProtocol);
            return result;
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void loadParameter(User user, AbstractParameter param,
                                   TableInfo tiValueTable,
                                   String pkName, int rowId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter(pkName, rowId);
        filter.addCondition("OntologyEntryURI", param.getOntologyEntryURI());
        Map<String, Object> existingValue = Table.selectObject(tiValueTable, filter, null, Map.class);
        if (existingValue == null)
        {
            Table.insert(user, tiValueTable, param);
        }
        else
        {
            throw new SQLException("Duplicate " + tiValueTable.getFromSQL() + " value, filter= " + filter + ". Existing parameter is " + existingValue + ", new value is " + param.getValue());
        }
    }

    public void savePropertyCollection(Map<String, ObjectProperty> propMap, String ownerLSID, String container, boolean clearExisting) throws SQLException
    {
        if (propMap.size() == 0)
            return;
        ObjectProperty[] props = propMap.values().toArray(new ObjectProperty[0]);
        // Todo - make this more efficient - don't delete all the old ones if they're the same
        if (clearExisting)
        {
            OntologyManager.deleteOntologyObject(container, ownerLSID);
            for (ObjectProperty prop : propMap.values())
            {
                prop.setObjectId(0);
            }
        }
        OntologyManager.insertProperties(container, props, ownerLSID);
        for (ObjectProperty prop : props)
        {
            Map<String, ObjectProperty> childProps = prop.retrieveChildProperties();
            if (childProps != null)
            {
                savePropertyCollection(childProps, ownerLSID, container, false);
            }
        }
    }

    public void insertProtocolPredecessor(User user, int actionRowId, int predecessorRowId) throws SQLException {
        Map<String, Object> mValsPredecessor = new HashMap<String, Object>();
        mValsPredecessor.put("ActionId", actionRowId);
        mValsPredecessor.put("PredecessorId", predecessorRowId);

        Table.insert(user, getTinfoProtocolActionPredecessor(), mValsPredecessor);
    }

    public ExpMaterial[] getMaterialsForSampleSet(String sampleSetLSID, Container container) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Container", container.getId());
        filter.addCondition("CpasType", sampleSetLSID);
        Sort sort = new Sort("Name");
        return ExpMaterialImpl.fromMaterials(Table.select(getTinfoMaterial(), Table.ALL_COLUMNS, filter, sort, Material.class));
    }

    public ExpRun getCreatingRun(File file, Container c)
        throws IOException
    {
        ExpDataImpl data = getDataByURL(file, c);
        if (data != null)
        {
            return data.getRun();
        }
        return null;
    }

    public static ExperimentServiceImpl get()
    {
        return (ExperimentServiceImpl)ExperimentService.get();
    }

    public Experiment insertExperiment(User user, Experiment exp) throws SQLException
    {
        return Table.insert(user, getTinfoExperiment(), exp);
    }

    /** @return all the Data objects from this run */
    private List<ExpData> ensureSimpleExperimentRunParameters(Collection<ExpMaterial> inputMaterials,
                                                     Collection<ExpData> inputDatas, Collection<ExpMaterial> outputMaterials,
                                                     Collection<ExpData> outputDatas, User user) throws SQLException
    {
        List<ExpData> result = new ArrayList<ExpData>();
        // insert input materials if they haven't been inserted already:
        for (ExpMaterial inputMaterial : inputMaterials)
        {
            if (inputMaterial.getRowId() == 0)
                inputMaterial.insert(user);
        }

        // insert input datas if they haven't been inserted already:
        for (ExpData inputData : inputDatas)
        {
            if (inputData.getRowId() == 0)
            {
                inputData.insert(user);
            }
        }
        result.addAll(inputDatas);

        // insert output materials if they haven't been inserted already:
        for (ExpMaterial outputMaterial : outputMaterials)
        {
            if (outputMaterial.getRowId() == 0)
                outputMaterial.insert(user);
        }

        // insert input datas if they haven't been inserted already:
        for (ExpData outputData : outputDatas)
        {
            if (outputData.getRowId() == 0)
            {
                outputData.insert(user);
            }
        }
        result.addAll(outputDatas);
        return result;
    }

    public ExpRun insertSimpleExperimentRun(ExpRun baseRun, Map<ExpMaterial, String> inputMaterials, Map<ExpData, String> inputDatas, Map<ExpMaterial, String> outputMaterials, Map<ExpData, String> outputDatas, ViewBackgroundInfo info, Logger log) throws SQLException, ExperimentException
    {
        ExpRunImpl run = (ExpRunImpl)baseRun;
        if (inputMaterials.isEmpty() && inputDatas.isEmpty())
        {
            throw new IllegalArgumentException("You must have at least one input to the run");
        }
        if (outputMaterials.isEmpty() && outputDatas.isEmpty())
        {
            throw new IllegalArgumentException("You must have at least one output to the run");
        }
        if (run.getFilePathRoot() == null)
        {
            throw new IllegalArgumentException("You must set the file path root on the experiment run");
        }

        boolean transactionOwner = !getSchema().getScope().isTransactionActive();
        if (transactionOwner)
            getSchema().getScope().beginTransaction();
        try
        {
            User user = info.getUser();
            if (run.getContainer() == null)
            {
                run.setContainer(info.getContainer());
            }
            Container runContainer = run.getContainer();
            Table.insert(user, getTinfoExperimentRun(), run.getDataObject());
            List<ExpData> insertedDatas = ensureSimpleExperimentRunParameters(inputMaterials.keySet(), inputDatas.keySet(), outputMaterials.keySet(), outputDatas.keySet(), user);

            ExpProtocolImpl parentProtocol = run.getProtocol();

            ProtocolAction[] actions = getProtocolActions(parentProtocol.getRowId());
            if (actions.length != 3)
            {
                throw new IllegalArgumentException("Protocol has the wrong number of steps for a simple protocol, it should have three");
            }
            ProtocolAction action1 = actions[0];
            assert action1.getSequence() == 1;
            assert action1.getChildProtocolId() == parentProtocol.getRowId();

            XarContext context = new XarContext("Simple Run Creation", run.getContainer(), user);
            context.addSubstitution("ExperimentRun.RowId", Integer.toString(run.getRowId()));

            Date date = new Date();

            ProtocolApplication protApp1 = new ProtocolApplication();
            protApp1.setActivityDate(date);
            protApp1.setActionSequence(action1.getSequence());
            protApp1.setCpasType(parentProtocol.getApplicationType().toString());
            protApp1.setRunId(run.getRowId());
            protApp1.setProtocolLSID(parentProtocol.getLSID());
            Map<String, ProtocolParameter> parentParams = parentProtocol.retrieveProtocolParameters();
            ProtocolParameter parentLSIDTemplateParam = parentParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            ProtocolParameter parentNameTemplateParam = parentParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            assert parentLSIDTemplateParam != null;
            assert parentNameTemplateParam != null;
            protApp1.setLSID(LsidUtils.resolveLsidFromTemplate(parentLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
            protApp1.setName(parentNameTemplateParam.getStringValue());

            protApp1 = Table.insert(user, getTinfoProtocolApplication(), protApp1);

            addDataInputs(inputDatas, runContainer, protApp1, user);
            addMaterialInputs(inputMaterials, runContainer, protApp1, user);

            ProtocolAction action2 = actions[1];
            assert action2.getSequence() == 10;
            Protocol protocol2 = getProtocol(action2.getChildProtocolId());

            ProtocolApplication protApp2 = new ProtocolApplication();
            protApp2.setActivityDate(date);
            protApp2.setActionSequence(action2.getSequence());
            protApp2.setCpasType(protocol2.getApplicationType());
            protApp2.setRunId(run.getRowId());
            protApp2.setProtocolLSID(protocol2.getLSID());

            Map<String, ProtocolParameter> coreParams = protocol2.retrieveProtocolParameters();
            ProtocolParameter coreLSIDTemplateParam = coreParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            ProtocolParameter coreNameTemplateParam = coreParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            assert coreLSIDTemplateParam != null;
            assert coreNameTemplateParam != null;
            protApp2.setLSID(LsidUtils.resolveLsidFromTemplate(coreLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
            protApp2.setName(coreNameTemplateParam.getStringValue());

            protApp2 = Table.insert(user, getTinfoProtocolApplication(), protApp2);

            addDataInputs(inputDatas, runContainer, protApp2, user);
            addMaterialInputs(inputMaterials, runContainer, protApp2, user);

            for (ExpMaterial outputMaterial : outputMaterials.keySet())
            {
                if (outputMaterial.getSourceApplication() != null)
                {
                    throw new IllegalArgumentException("Output material " + outputMaterial.getName() + " is already marked as being created by another protocol application");
                }
                if (outputMaterial.getSourceProtocol() != null)
                {
                    throw new IllegalArgumentException("Output material " + outputMaterial.getName() + " is already marked as being created by another protocol");
                }
                if (outputMaterial.getRun() != null)
                {
                    throw new IllegalArgumentException("Output material " + outputMaterial.getName() + " is already marked as being created by another run");
                }
                outputMaterial.setSourceApplication(new ExpProtocolApplicationImpl(protApp2));
                outputMaterial.setSourceProtocol(new ExpProtocolImpl(protocol2));
                outputMaterial.setRun(run);
                Table.update(user, getTinfoMaterial(), ((ExpMaterialImpl)outputMaterial)._object, outputMaterial.getRowId(), null);
            }

            for (ExpData outputData : outputDatas.keySet())
            {
                if (outputData.getSourceApplication() != null)
                {
                    throw new IllegalArgumentException("Output data " + outputData.getName() + " is already marked as being created by another protocol application");
                }
                if (outputData.getSourceProtocol() != null)
                {
                    throw new IllegalArgumentException("Output data " + outputData.getName() + " is already marked as being created by another protocol");
                }
                if (outputData.getRun() != null)
                {
                    throw new IllegalArgumentException("Output data " + outputData.getName() + " is already marked as being created by another run");
                }
                outputData.setSourceApplication(new ExpProtocolApplicationImpl(protApp2));
                outputData.setSourceProtocol(new ExpProtocolImpl(protocol2));
                outputData.setRun(run);
                Table.update(user, getTinfoData(), ((ExpDataImpl)outputData).getDataObject(), outputData.getRowId(), null);
            }

            ProtocolAction action3 = actions[2];
            assert action3.getSequence() == 20;

            Protocol outputProtocol = getProtocol(action3.getChildProtocolId());
            assert outputProtocol.getApplicationType().equals("ExperimentRunOutput");


            ProtocolApplication protApp3 = new ProtocolApplication();
            protApp3.setActivityDate(date);
            protApp3.setActionSequence(action3.getSequence());
            protApp3.setCpasType(outputProtocol.getApplicationType());
            protApp3.setRunId(run.getRowId());
            protApp3.setProtocolLSID(outputProtocol.getLSID());

            Map<String, ProtocolParameter> outputParams = outputProtocol.retrieveProtocolParameters();
            ProtocolParameter outputLSIDTemplateParam = outputParams.get(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            ProtocolParameter outputNameTemplateParam = outputParams.get(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            assert outputLSIDTemplateParam != null;
            assert outputNameTemplateParam != null;
            protApp3.setLSID(LsidUtils.resolveLsidFromTemplate(outputLSIDTemplateParam.getStringValue(), context, "ProtocolApplication"));
            protApp3.setName(outputNameTemplateParam.getStringValue());
            protApp3 = Table.insert(user, getTinfoProtocolApplication(), protApp3);

            addDataInputs(outputDatas, runContainer, protApp3, user);
            addMaterialInputs(outputMaterials, runContainer, protApp3, user);

            if (transactionOwner)
                getSchema().getScope().commitTransaction();

            for (ExpData insertedData : insertedDatas)
            {
                insertedData.findDataHandler().importFile(getExpData(insertedData.getRowId()), insertedData.getFile(), info, log, context);
            }

            return run;
        }
        finally
        {
            if (transactionOwner)
                getSchema().getScope().closeConnection();
        }
    }

    public ExpRun deriveSamples(Map<ExpMaterial, String> inputMaterials, Map<ExpMaterial, String> outputMaterials, ViewBackgroundInfo info, Logger log) throws ExperimentException
    {
        try
        {
            PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());
            if (pipeRoot == null || !NetworkDrive.exists(pipeRoot.getRootPath()))
            {
                throw new IllegalArgumentException("The target container must have a valid pipeline root");
            }

            StringBuilder name = new StringBuilder("Derive ");
            name.append(outputMaterials.size());
            name.append(" sample");
            if (outputMaterials.size() > 1)
            {
                name.append("s");
            }
            name.append(" from ");
            String nameSeparator = " ";
            for (ExpMaterial material : inputMaterials.keySet())
            {
                name.append(nameSeparator);
                name.append(material.getName());
                nameSeparator = ", ";
            }

            ExpProtocol protocol = ensureSampleDerivationProtocol(info.getUser());
            ExpRunImpl run = createExperimentRun(info.getContainer(), name.toString());
            run.setProtocol(protocol);
            run.setFilePathRoot(pipeRoot.getRootPath());

            return insertSimpleExperimentRun(run, inputMaterials, Collections.<ExpData, String>emptyMap(), outputMaterials, Collections.<ExpData, String>emptyMap(), info, log);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private ExpProtocol ensureSampleDerivationProtocol(User user)
    {
        Protocol protocol = getProtocol(SAMPLE_DERIVATION_PROTOCOL_LSID);
        if (protocol == null)
        {
            ExpProtocolImpl baseProtocol = createExpProtocol(ContainerManager.getSharedContainer(), "Sample Derivation Protocol", ExpProtocol.ApplicationType.ExperimentRun);
            baseProtocol.setLSID(SAMPLE_DERIVATION_PROTOCOL_LSID);
            baseProtocol.setMaxInputDataPerInstance(0);
            baseProtocol.setProtocolDescription("Simple protocol for creating derived samples that may have different properties from the original sample.");
            try
            {
                return insertSimpleProtocol(baseProtocol, user);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        return new ExpProtocolImpl(protocol);
    }

    public void registerExperimentDataHandler(ExperimentDataHandler handler)
    {
        _dataHandlers.add(handler);
    }

    public void registerRunExpansionHandler(RunExpansionHandler handler)
    {
        _expansionHanders.add(handler);
    }

    public void registerExperimentRunFilter(ExperimentRunFilter filter)
    {
        _runFilters.add(filter);
    }

    public void registerDataType(DataType type)
    {
        _dataTypes.put(type.getNamespacePrefix(), type);
    }

    public Set<ExperimentRunFilter> getExperimentRunFilters()
    {
        return Collections.unmodifiableSet(_runFilters);
    }

    public Set<ExperimentDataHandler> getExperimentDataHandlers()
    {
        return Collections.unmodifiableSet(_dataHandlers);
    }

    public Set<RunExpansionHandler> getRunExpansionHandlers()
    {
        return Collections.unmodifiableSet(_expansionHanders);
    }

    public DataType getDataType(String namespacePrefix)
    {
        return _dataTypes.get(namespacePrefix);
    }

    public void registerProtocolImplementation(ProtocolImplementation impl)
    {
        _protocolImplementations.put(impl.getName(), impl);
    }

    public ProtocolImplementation getProtocolImplementation(String name)
    {
        return _protocolImplementations.get(name);
    }

    public ExpProtocolApplicationImpl getExpProtocolApplication(int rowId)
    {
        ProtocolApplication app = Table.selectObject(getTinfoProtocolApplication(), rowId, ProtocolApplication.class);
        if (app == null)
            return null;
        return new ExpProtocolApplicationImpl(app);
    }

    public ExpProtocolApplication[] getExpProtocolApplicationsForRun(int runId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RunId", runId);
        Sort sort = new Sort("ActionSequence, RowId");
        return ExpProtocolApplicationImpl.fromProtocolApplications(Table.select(getTinfoProtocolApplication(), Table.ALL_COLUMNS, filter, sort, ProtocolApplication.class));
    }

    public ExpSampleSet createSampleSet()
    {
        return new ExpSampleSetImpl(new MaterialSource());
    }

    public ExpProtocol[] getExpProtocols(Container container)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter("Container", container.getId());
            return ExpProtocolImpl.fromProtocols(Table.select(getTinfoProtocol(), Table.ALL_COLUMNS, filter, null, Protocol.class));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    private void addMaterialInputs(Map<ExpMaterial, String> inputMaterials, Container c, ProtocolApplication protApp1, User user)
            throws SQLException
    {
        for (Map.Entry<ExpMaterial, String> entry : inputMaterials.entrySet())
        {
            MaterialInput input = new MaterialInput();
            PropertyDescriptor pd = ensureMaterialInputRole(c, entry.getValue(), getExpMaterial(entry.getKey().getRowId()));
            input.setPropertyId(pd != null ? pd.getPropertyId() : null);
            input.setMaterialId(entry.getKey().getRowId());
            input.setTargetApplicationId(protApp1.getRowId());
            Table.insert(user, getTinfoMaterialInput(), input);
        }
    }

    private void addDataInputs(Map<ExpData, String> inputDatas, Container c, ProtocolApplication protApp1, User user)
            throws SQLException
    {
        for (Map.Entry<ExpData, String> entry : inputDatas.entrySet())
        {
            DataInput input = new DataInput();
            PropertyDescriptor pd = ensureDataInputRole(user, c, entry.getValue(), getExpData(entry.getKey().getRowId()));
            input.setPropertyId(pd.getPropertyId());
            input.setDataId(entry.getKey().getRowId());
            input.setTargetApplicationId(protApp1.getRowId());
            Table.insert(user, getTinfoDataInput(), input);
        }
    }

    public ExpProtocol insertSimpleProtocol(ExpProtocol wrappedProtocol, User user) throws SQLException
    {
        boolean transactionOwner = !getSchema().getScope().isTransactionActive();
        if (transactionOwner)
            getSchema().getScope().beginTransaction();
        Protocol baseProtocol = ((ExpProtocolImpl)wrappedProtocol).getDataObject();
        try
        {
            baseProtocol.setApplicationType("ExperimentRun");
            baseProtocol.setOutputDataType("Data");
            baseProtocol.setOutputMaterialType("Material");
            baseProtocol.setContainer(baseProtocol.getContainer());

            List<ProtocolParameter> baseParams = new ArrayList<ProtocolParameter>();
            ProtocolParameter baseLSIDTemplate = new ProtocolParameter();
            baseLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            baseLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            baseLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.InputStep");
            baseParams.add(baseLSIDTemplate);
            ProtocolParameter baseNameTemplate = new ProtocolParameter();
            baseNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            baseNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            baseNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName() + " Protocol");
            baseParams.add(baseNameTemplate);
            baseProtocol.storeProtocolParameters(baseParams);

            baseProtocol = saveProtocol(user, baseProtocol);

            Protocol coreProtocol = new Protocol();
            coreProtocol.setOutputDataType("Data");
            coreProtocol.setOutputMaterialType("Material");
            coreProtocol.setContainer(baseProtocol.getContainer());
            coreProtocol.setApplicationType("ProtocolApplication");
            coreProtocol.setName(baseProtocol.getName() + " - Core");
            coreProtocol.setLSID(baseProtocol.getLSID() + ".Core");

            List<ProtocolParameter> coreParams = new ArrayList<ProtocolParameter>();
            ProtocolParameter coreLSIDTemplate = new ProtocolParameter();
            coreLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            coreLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            coreLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.CoreStep");
            coreParams.add(coreLSIDTemplate);
            ProtocolParameter coreNameTemplate = new ProtocolParameter();
            coreNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            coreNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            coreNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName());
            coreParams.add(coreNameTemplate);
            coreProtocol.storeProtocolParameters(coreParams);

            coreProtocol = saveProtocol(user, coreProtocol);

            Protocol outputProtocol = new Protocol();
            outputProtocol.setOutputDataType("Data");
            outputProtocol.setOutputMaterialType("Material");
            outputProtocol.setName(baseProtocol.getName() + " - Output");
            outputProtocol.setLSID(baseProtocol.getLSID() + ".Output");
            outputProtocol.setApplicationType("ExperimentRunOutput");
            outputProtocol.setContainer(baseProtocol.getContainer());

            List<ProtocolParameter> outputParams = new ArrayList<ProtocolParameter>();
            ProtocolParameter outputLSIDTemplate = new ProtocolParameter();
            outputLSIDTemplate.setName(XarConstants.APPLICATION_LSID_TEMPLATE_NAME);
            outputLSIDTemplate.setOntologyEntryURI(XarConstants.APPLICATION_LSID_TEMPLATE_URI);
            outputLSIDTemplate.setValue(SimpleTypeNames.STRING, "${RunLSIDBase}:SimpleProtocol.OutputStep");
            outputParams.add(outputLSIDTemplate);
            ProtocolParameter outputNameTemplate = new ProtocolParameter();
            outputNameTemplate.setName(XarConstants.APPLICATION_NAME_TEMPLATE_NAME);
            outputNameTemplate.setOntologyEntryURI(XarConstants.APPLICATION_NAME_TEMPLATE_URI);
            outputNameTemplate.setValue(SimpleTypeNames.STRING, baseProtocol.getName() + " output");
            outputParams.add(outputNameTemplate);
            outputProtocol.storeProtocolParameters(outputParams);

            outputProtocol = saveProtocol(user, outputProtocol);

            ProtocolAction action1 = new ProtocolAction();
            action1.setParentProtocolId(baseProtocol.getRowId());
            action1.setChildProtocolId(baseProtocol.getRowId());
            action1.setSequence(1);
            action1 = Table.insert(user, getTinfoProtocolAction(), action1);

            insertProtocolPredecessor(user, action1.getRowId(), action1.getRowId());

            ProtocolAction action2 = new ProtocolAction();
            action2.setParentProtocolId(baseProtocol.getRowId());
            action2.setChildProtocolId(coreProtocol.getRowId());
            action2.setSequence(10);
            action2 = Table.insert(user, getTinfoProtocolAction(), action2);

            insertProtocolPredecessor(user, action2.getRowId(), action1.getRowId());

            ProtocolAction action3 = new ProtocolAction();
            action3.setParentProtocolId(baseProtocol.getRowId());
            action3.setChildProtocolId(outputProtocol.getRowId());
            action3.setSequence(20);
            action3 = Table.insert(user, getTinfoProtocolAction(), action3);

            insertProtocolPredecessor(user, action3.getRowId(), action2.getRowId());

            if (transactionOwner)
                getSchema().getScope().commitTransaction();
            return wrappedProtocol;
        }
        finally
        {
            if (transactionOwner)
                getSchema().getScope().closeConnection();
        }
    }
}
