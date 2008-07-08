/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.experiment;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.SpringModule;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.api.query.SchemaUpdateServiceRegistry;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.LogDataType;
import org.labkey.experiment.api.Material;
import org.labkey.experiment.api.property.PropertyServiceImpl;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.controllers.list.ListController;
import org.labkey.experiment.controllers.list.ListWebPart;
import org.labkey.experiment.controllers.list.SingleListWebPartFactory;
import org.labkey.experiment.controllers.property.PropertyController;
import org.labkey.experiment.list.*;
import org.labkey.experiment.types.TypesController;
import org.labkey.experiment.xar.DefaultRunExpansionHandler;

import javax.naming.NamingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: phussey (Peter Hussey)
 * Date: Jul 18, 2005
 * Time: 3:25:52 PM
 */
public class ExperimentModule extends SpringModule
{
    private static final String SAMPLE_SET_WEB_PART_NAME = "Sample Sets";
    private static final String PROTOCOL_WEB_PART_NAME = "Protocols";
    public static final String EXPERIMENT_RUN_WEB_PART_NAME = "Experiment Runs";

    private static final Logger _log = Logger.getLogger(ExperimentModule.class);

    public ExperimentModule()
    {
        super(ExperimentService.MODULE_NAME, 8.20, "/org/labkey/experiment", true, createWebPartList());
        addController("experiment", ExperimentController.class);
        addController("experiment-types", TypesController.class);
        addController("property", PropertyController.class);
        addController("list", ListController.class);
        ExperimentService.setInstance(new ExperimentServiceImpl());
        PropertyService.setInstance(new PropertyServiceImpl());
        ListService.setInstance(new ListServiceImpl());
        

        ExperimentProperty.register();
        SamplesSchema.register();
        ExpSchema.register();
        ListSchema.register();
        PropertyService.get().registerDomainKind(new SampleSetDomainType());
        PropertyService.get().registerDomainKind(new ListDomainType());
    }

    @Override
    protected ContextType getContextType()
    {
        return ContextType.config;
    }

    private static WebPartFactory[] createWebPartList()
    {
        List<WebPartFactory> result = new ArrayList<WebPartFactory>();
        
        WebPartFactory runGroupsFactory = new WebPartFactory(RunGroupWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        runGroupsFactory.addLegacyNames("Experiments", "Experiment", "Experiment Navigator");
        result.add(runGroupsFactory);
        WebPartFactory narrowRunGroupsFactory = new WebPartFactory(RunGroupWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunGroupWebPart(portalCtx, WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), webPart);
            }
        };
        narrowRunGroupsFactory.addLegacyNames("Experiments", "Narrow Experiments");
        result.add(narrowRunGroupsFactory);

        WebPartFactory runTypesFactory = new WebPartFactory(RunTypeWebPart.WEB_PART_NAME)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesFactory);

        WebPartFactory runTypesNarrowFactory = new WebPartFactory(RunTypeWebPart.WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new RunTypeWebPart();
            }
        };
        result.add(runTypesNarrowFactory);

        result.add(new WebPartFactory(ExperimentModule.EXPERIMENT_RUN_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart)
            {
                return ExperimentService.get().createExperimentRunWebPart(new ViewContext(portalCtx), ExperimentRunFilter.ALL_RUNS_FILTER, true, true);
            }
        });
        result.add(new WebPartFactory(SAMPLE_SET_WEB_PART_NAME){
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        });
        WebPartFactory narrowSampleSetFactory = new WebPartFactory(SAMPLE_SET_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new SampleSetWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowSampleSetFactory.addLegacyNames("Narrow Sample Sets");
        result.add(narrowSampleSetFactory);
        WebPartFactory narrowProtocolFactory = new WebPartFactory(PROTOCOL_WEB_PART_NAME, WebPartFactory.LOCATION_RIGHT)
        {
            public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
            {
                return new ProtocolWebPart(WebPartFactory.LOCATION_RIGHT.equalsIgnoreCase(webPart.getLocation()), portalCtx);
            }
        };
        narrowProtocolFactory.addLegacyNames("Narrow Protocols");
        result.add(narrowProtocolFactory);
        result.add(ListWebPart.FACTORY);
        result.add(new SingleListWebPartFactory());

        return result.toArray(new WebPartFactory[result.size()]);
    }


    @Override
    public void startup(ModuleContext context)
    {
        PipelineService.get().registerPipelineProvider(new ExperimentPipelineProvider());
        ExperimentService.get().registerExperimentRunFilter(ExperimentRunFilter.ALL_RUNS_FILTER);
        ExperimentService.get().registerRunExpansionHandler(new DefaultRunExpansionHandler());
        ExperimentService.get().registerExperimentDataHandler(new DefaultExperimentDataHandler());
        ExperimentService.get().registerDataType(new LogDataType());
        AuditLogService.get().addAuditViewFactory(ListAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(DomainAuditViewFactory.getInstance());

        ContainerManager.addContainerListener(new ContainerManager.ContainerListener()
        {
            public void containerCreated(Container c)
            {
            }

            public void containerDeleted(Container c, User user)
            {
                try
                {
                    ExperimentService.get().deleteAllExpObjInContainer(c, user);
                }
                catch (SQLException e)
                {
                    throw new RuntimeException("Delete failed", e);
                }
                catch (Exception ee)
                {
                    throw new RuntimeException(ee);
                }
            }

            public void propertyChange(PropertyChangeEvent evt)
            {
                if (evt.getPropertyName().equals("Parent"))
                {
                    Container c = (Container) evt.getSource();
                    Container cOldParent = (Container) evt.getOldValue();
                    Container cNewParent = (Container) evt.getNewValue();
                    try
                    {
                        ExperimentService.get().moveContainer(c, cOldParent, cNewParent);
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeException(e);
                    }
                    catch (ExperimentException e)
                    {
                        throw new RuntimeException(e);
                    }

                }
            }
        });
        SystemProperty.registerProperties();
        OntologyManager.initCaches();
        super.startup(context);
    }

    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        int count = ExperimentService.get().getExperiments(c).length;
        if (count > 0)
            list.add("" + count + " Run Group" + (count > 1 ? "s" : ""));
        return list;
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            LSIDRelativizer.TestCase.class,
            OntologyManager.TestCase.class,
            LsidUtils.TestCase.class));
    }


    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(ExpSchema.SCHEMA_NAME);
    }


    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(ExperimentService.get().getSchema());
    }


    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double version = moduleContext.getInstalledVersion();
        if (version > 0 && version < 1.32)
        {
            try {
                doVersion_132Update();
            } catch (Exception e) {
                String msg = "Error running afterSchemaUpdate doVersion_132Update on ExperimentModule, upgrade from version " + String.valueOf(version);
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                //following sends an exception report to mothership if site is configured to do so, but doesn't abort schema upgrade
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, viewContext.getRequest(), false, false);
            }
        }

        if (version > 0 && version < 1.7)
        {
            try {
                doInputRoleUpdate(viewContext);
            } catch (Exception e) {
                String msg = "Error running afterSchemaUpdate doInputRoleUpdate on ExperimentModule, upgrade from version " + String.valueOf(version);
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, viewContext.getRequest(), false, false);
            }
        }

        if (version > 0 && version < 2.23)
        {
            try
            {
                doPopulateListEntityIds();
            }
            catch (Exception e)
            {
                String msg = "Error running afterSchemaUpdate doPopulateListEntityIds on ExperimentModule, upgrade from version " + String.valueOf(version);
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                //following sends an exception report to mothership if site is configured to do so, but doesn't abort schema upgrade
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, viewContext.getRequest(), false, false);
            }
        }

        super.afterSchemaUpdate(moduleContext, viewContext);
    }

    public static void doVersion_132Update() throws SQLException, NamingException, ServletException
    {
        DbSchema tmpSchema = DbSchema.createFromMetaData("exp");
        doProjectColumnUpdate(tmpSchema, "exp.PropertyDescriptor");
        doProjectColumnUpdate(tmpSchema, "exp.DomainDescriptor");
        alterDescriptorTables(tmpSchema);
    }

    protected static void doProjectColumnUpdate(DbSchema tmpSchema, String descriptorTable) throws SQLException
    {
        String sql = "SELECT DISTINCT(Container) FROM " + descriptorTable + " WHERE Project IS NULL ";
        String[] cids = Table.executeArray(tmpSchema, sql, new Object[]{}, String.class);
        String projectId;
        String newContainerId;
        for (String cid : cids)
        {
            newContainerId = cid;
            if (cid.equals(ContainerManager.getRoot().getId()) || cid.equals("00000000-0000-0000-0000-000000000000"))
                newContainerId = ContainerManager.getSharedContainer().getId();
            projectId = ContainerManager.getForId(newContainerId).getProject().getId();
            setDescriptorProject(tmpSchema, cid, projectId, newContainerId, descriptorTable);
        }
    }

    private static void doPopulateListEntityIds() throws SQLException
    {
        ListDef[] listDefs = ListManager.get().getAllLists();

        for (ListDef listDef : listDefs)
        {
            int listId = listDef.getRowId();
            ListDefinitionImpl impl = new ListDefinitionImpl(listDef);
            TableInfo tinfo = impl.getIndexTable();

            SimpleFilter lstItemFilter = new SimpleFilter("ListId", listId);
            ListItm[] itms = Table.select(tinfo, Table.ALL_COLUMNS, lstItemFilter, null, ListItm.class);

            String sql = "UPDATE " + tinfo + " SET EntityId = ? WHERE ListId = ? AND " + tinfo.getSqlDialect().getColumnSelectName("Key") + " = ?";

            for (ListItm itm : itms)
            {
                Table.execute(tinfo.getSchema(), sql, new Object[]{GUID.makeGUID(), listId, itm.getKey()});
            }
        }
    }

    protected static void setDescriptorProject (DbSchema tmpSchema, String containerId, String projectId, String newContainerId, String descriptorTable) throws SQLException
    {
        String sql = " UPDATE " + descriptorTable + " SET Project = ?, Container = ? WHERE Container = ? ";
        Table.execute(tmpSchema, sql, new Object[]{projectId, newContainerId, containerId});
    }

    protected static void alterDescriptorTables(DbSchema tmpSchema) throws SQLException
    {
        String indexOption = " ";
        String keywordNotNull = " ENTITYID ";

        if (tmpSchema.getSqlDialect().isSqlServer())
            indexOption = " CLUSTERED ";

        if (tmpSchema.getSqlDialect().isPostgreSQL())
            keywordNotNull = " SET ";

        String sql = " ALTER TABLE exp.PropertyDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        sql = " ALTER TABLE exp.PropertyDescriptor ADD CONSTRAINT UQ_PropertyDescriptor UNIQUE " + indexOption + " (Project, PropertyURI);" ;
        Table.execute(tmpSchema, sql, new Object[]{});

        sql = " ALTER TABLE exp.DomainDescriptor ALTER COLUMN Project " + keywordNotNull + " NOT NULL ;";
        Table.execute(tmpSchema, sql, new Object[]{});

        sql = " ALTER TABLE exp.DomainDescriptor ADD CONSTRAINT UQ_DomainDescriptor UNIQUE " + indexOption + " (Project, DomainURI);"  ;
        Table.execute(tmpSchema, sql, new Object[]{});
    }

    protected static void doInputRoleUpdate(ViewContext viewContext) throws Exception
    {
        DbSchema exp = ExperimentService.get().getSchema();

        String sqlSelect = "SELECT DISTINCT(D.Container) FROM "+ ExperimentServiceImpl.get().getTinfoDataInput() + ", ";
        String sqlUpdate = "UPDATE  " + ExperimentServiceImpl.get().getTinfoDataInput() + " SET PropertyId = ?  FROM ";

        String sqlFrom = ExperimentServiceImpl.get().getTinfoData() + " D, " +
                ExperimentServiceImpl.get().getTinfoProtocolApplication() + " PA " +
                " WHERE D.RowID = " + ExperimentServiceImpl.get().getTinfoDataInput() + ".DataId " +
                " AND PA.RowId = " + ExperimentServiceImpl.get().getTinfoDataInput() + ".TargetApplicationId " +
                " AND UPPER(PA.CpasType)='EXPERIMENTRUN' " +
                " AND " + ExperimentServiceImpl.get().getTinfoDataInput() + ".PropertyId IS NULL ";

        Map<String, String> roleFilters = new HashMap<String, String>();
        roleFilters.put("mzXML", " AND UPPER(D.DataFileUrl) LIKE '%.MZXML' ");
        roleFilters.put("FASTA", " AND (UPPER(D.DataFileUrl) LIKE '%.FSA' OR  UPPER(D.DataFileUrl) LIKE '%.FASTA' ) ");
        roleFilters.put("SearchConfig", " AND UPPER(D.DataFileUrl) LIKE '%TANDEM.XML' ");

        String containerFilt = " AND D.Container = ? ";

        User user = viewContext.getUser();
        Container c ;
        String[] cids;
        PropertyDescriptor pd;

        for (String roleName : roleFilters.keySet())
        {
            cids = Table.executeArray(exp, sqlSelect + sqlFrom + roleFilters.get(roleName), new Object[]{}, String.class);
            for (String cid : cids)
            {
                c = ContainerManager.getForId(cid);
                pd = ExperimentService.get().ensureDataInputRole(user, c, roleName, null);
                Table.execute(exp, sqlUpdate + sqlFrom + roleFilters.get(roleName) + containerFilt,
                        new Object[]{pd.getPropertyId(),  cid});
            }
        }

        sqlSelect = "SELECT M.* FROM "+ ExperimentServiceImpl.get().getTinfoMaterialInput() + ", ";
        sqlUpdate = "UPDATE  " + ExperimentServiceImpl.get().getTinfoMaterialInput() + " SET PropertyId = ? FROM ";

        sqlFrom = ExperimentServiceImpl.get().getTinfoMaterial() + " M, " +
                ExperimentServiceImpl.get().getTinfoProtocolApplication() + " PA " +
                " WHERE M.RowID = " + ExperimentServiceImpl.get().getTinfoMaterialInput() + ".MaterialId " +
                " AND PA.RowId = " + ExperimentServiceImpl.get().getTinfoMaterialInput() + ".TargetApplicationId " +
                " AND UPPER(PA.CpasType)='EXPERIMENTRUN' " +

                " AND " + ExperimentServiceImpl.get().getTinfoMaterialInput() + ".MaterialId = " +
                " (SELECT MIN(MI2.MaterialId) FROM " + ExperimentServiceImpl.get().getTinfoMaterialInput() + " MI2 " +
                " WHERE MI2.TargetApplicationId = " + ExperimentServiceImpl.get().getTinfoMaterialInput() + ".TargetApplicationId )" +
                " AND "  + ExperimentServiceImpl.get().getTinfoMaterialInput() + ".PropertyId IS NULL ";

        String sqlMaterialToUpdate = " AND M.RowId = ? ";
        String roleName= "Material";
        ExpMaterial sample;
        Material[] aMats = Table.executeQuery(exp, sqlSelect + sqlFrom, new Object[]{}, Material.class);

        for (Material m : aMats)
        {
            sample = ExperimentService.get().getExpMaterial(m.getRowId());
            c = ContainerManager.getForId(m.getContainer());
            pd = ExperimentService.get().ensureMaterialInputRole(c, roleName, sample);

            Table.execute(exp, sqlUpdate + sqlFrom + sqlMaterialToUpdate, new Object[]{pd.getPropertyId(), m.getRowId()});
        }
    }


    @Override
    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Pipeline");
        return result;
    }
}
