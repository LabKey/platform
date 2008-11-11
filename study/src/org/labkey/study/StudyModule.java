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

package org.labkey.study;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.*;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Search;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.*;
import org.labkey.api.wiki.WikiService;
import org.labkey.study.assay.*;
import org.labkey.study.assay.query.AssayAuditViewFactory;
import org.labkey.study.assay.query.AssaySchema;
import org.labkey.study.controllers.*;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.controllers.designer.DesignerController;
import org.labkey.study.controllers.plate.PlateController;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.controllers.security.SecurityController;
import org.labkey.study.dataset.DatasetAuditViewFactory;
import org.labkey.study.dataset.DatasetSnapshotProvider;
import org.labkey.study.designer.view.StudyDesignsWebPart;
import org.labkey.study.importer.SpecimenImporter;
import org.labkey.study.model.*;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.plate.PlateManager;
import org.labkey.study.plate.query.PlateSchema;
import org.labkey.study.query.StudySchemaProvider;
import org.labkey.study.reports.*;
import org.labkey.study.samples.SamplesWebPart;
import org.labkey.study.view.*;

import java.lang.reflect.InvocationTargetException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class StudyModule extends DefaultModule
{
    private static final Logger _log = Logger.getLogger(DefaultModule.class);

    public static final BaseWebPartFactory reportsPartFactory = new ReportsWebPartFactory();
    public static final WebPartFactory reportsWidePartFactory = new ReportsWideWebPartFactory();
    public static final WebPartFactory samplesPartFactory = new SamplesWebPartFactory("right");
    public static final WebPartFactory samplesWidePartFactory = new SamplesWebPartFactory(HttpView.BODY);
    public static final WebPartFactory datasetsPartFactory = new DatasetsWebPartFactory();
    public static final WebPartFactory manageStudyPartFactory = new StudySummaryWebPartFactory();
    public static final WebPartFactory enrollmentChartPartFactory = new EnrollmentChartWebPartFactory();
    public static final WebPartFactory studyDesignsWebPartFactory = new StudyDesignsWebPartFactory();
    public static final WebPartFactory studyDesignSummaryWebPartFactory = new StudyDesignSummaryWebPartFactory();
    public static final WebPartFactory assayListWebPartFactory = new AssayListWebPartFactory();
    public static final WebPartFactory assayDetailsWebPartFactory = new AssayDetailsWebPartFactory();
    public static final WebPartFactory participantWebPartFactory = new ParticipantWebPartFactory();

    public String getName()
    {
        return "Study";
    }

    public double getVersion()
    {
        return 8.30;
    }

    protected void init()
    {
        addController("study", StudyController.class);
        addController("study-reports", ReportsController.class);
        addController("study-samples", SpringSpecimenController.class);
        addController("study-security", SecurityController.class);
        addController("study-designer", DesignerController.class);
        addController("plate", PlateController.class);
        addController("assay", AssayController.class);
        addController("dataset", DatasetController.class);
        addController("study-definition", StudyDefinitionController.class);
        addController("cohort", CohortController.class);
        addController("study-properties", StudyPropertiesController.class);

        PlateService.register(new PlateManager());
        AssayService.setInstance(new AssayManager());
        StudyService.register(new StudyServiceImpl());
        DefaultSchema.registerProvider("study", new StudySchemaProvider());
        DefaultSchema.registerProvider("plate", new PlateSchema.Provider());
        DefaultSchema.registerProvider("assay", new AssaySchema.Provider());

        PropertyService.get().registerDomainKind(new DatasetDomainKind());
        PropertyService.get().registerDomainKind(new AssayDomainKind());
        PropertyService.get().registerDomainKind(new CohortDomainKind());
        PropertyService.get().registerDomainKind(new StudyDomainKind());

        Search.register(new StudySearch());

        EnumConverter.registerEnum(SecurityType.class);
        QuerySnapshotService.registerProvider(StudyManager.getSchemaName(), DatasetSnapshotProvider.getInstance());
    }

    public boolean hasScripts()
    {
        return true;
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(reportsPartFactory, reportsWidePartFactory, samplesPartFactory,
                samplesWidePartFactory, datasetsPartFactory, manageStudyPartFactory,
                enrollmentChartPartFactory, studyDesignsWebPartFactory, studyDesignSummaryWebPartFactory,
                assayListWebPartFactory, assayDetailsWebPartFactory, participantWebPartFactory);
    }

    public Collection<String> getSummary(Container c)
    {
        Study study = StudyManager.getInstance().getStudy(c);

        if (study != null)
        {
            Collection<String> list = new LinkedList<String>();
            list.add("Study: " + study.getLabel());
            long participants = StudyManager.getInstance().getParticipantCount(study);
            list.add("" + participants + " Study participants");
            return list;
        }
        else
            return Collections.emptyList();
    }


    public void startup(ModuleContext moduleContext)
    {
        PipelineService.get().registerPipelineProvider(new StudyPipeline());
        ContainerManager.addContainerListener(new StudyContainerListener());
        AssayPublishService.register(new AssayPublishManager());
        SpecimenService.register(new SpecimenServiceImpl());
        LsidManager.get().registerHandler("Study", StudyManager.getLsidHandler());
        WikiService.get().registerMacroProvider("study", new StudyMacroProvider());
        PlateManager.get().registerLsidHandlers();
        registerFolderType();
        SecurityManager.addViewFactory(new SecurityController.StudySecurityViewFactory());
        AssayService.get().registerAssayProvider(new TsvAssayProvider());
        ExperimentService.get().registerExperimentDataHandler(new TsvDataHandler());
        AuditLogService.get().addAuditViewFactory(AssayAuditViewFactory.getInstance());
        AuditLogService.get().addAuditViewFactory(DatasetAuditViewFactory.getInstance());

        ReportService.get().registerReport(new StudyController.StudyChartReport());
        ReportService.get().registerReport(new EnrollmentReport());
        ReportService.get().registerReport(new StudyQueryReport());
        ReportService.get().registerReport(new ChartReportView.DatasetChartReport());
        ReportService.get().registerReport(new ExternalReport());
        ReportService.get().registerReport(new AttachmentReport());
        ReportService.get().registerReport(new ExportExcelReport());
        ReportService.get().registerReport(new ChartReportView());
        ReportService.get().registerReport(new StudyChartQueryReport());
        ReportService.get().registerReport(new StudyCrosstabReport());
        ReportService.get().registerReport(new StudyRReport());

        ReportService.get().registerDescriptor(new ChartReportView.ChartReportViewDescriptor());
        ReportService.get().registerDescriptor(new CrosstabReportDescriptor());

        ReportService.get().addViewFactory(new ReportsController.StudyRReportViewFactory());
        ReportService.get().addUIProvider(new StudyReportUIProvider());
    }


    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(StudyManager.getSchemaName());
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(StudySchema.getInstance().getSchema());
    }

    private void registerFolderType()
    {
        ModuleLoader.getInstance().registerFolderType(new StudyFolderType(this));
    }

    public void beforeSchemaUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.getInstalledVersion() >= 2.1 && moduleContext.getInstalledVersion() < 8.29)
        {
            // We're going to add a unique constraint to dataset labels,
            // so we need to go through and unique-ify any that are not unique.
            ResultSet labelRS = null;
            ResultSet nameRS = null;
            try
            {
                DbSchema schema = StudySchema.getInstance().getSchema();
                
                String sql = "select num, container, label from (select count(*) as num, container, label\n" +
                    "from study.dataset \n" +
                    "group by container, label) as subselect\n" +
                    "where num > 1";

                labelRS = Table.executeQuery(schema, sql, new Object[0]);
                while(labelRS.next())
                {
                    String container = labelRS.getString("container");
                    String label = labelRS.getString("label");

                    // Now we need to get all the names for those labels
                    sql = "select name \n" +
                        "from study.dataset \n" +
                        "where container = ? AND\n" +
                        "label = ?";

                    nameRS = Table.executeQuery(schema, sql, new Object[]{container, label});

                    while (nameRS.next())
                    {
                        // Make the new label "name: oldLabel"

                        String name = nameRS.getString(1);

                        // We're guaranteed to get two or more of these, so ignore if there's one whose
                        // label matches its name
                        if (name.equals(label))
                            continue;

                        String newLabel = name + ": " + label;

                        sql = "update study.dataset\n" +
                            "set label = ?\n" +
                            "where name = ?\n" +
                            "and container = ?";
                        
                        Table.execute(schema, sql, new Object[]{newLabel, name, container});
                    }
                }
            }
            catch (SQLException se)
            {
                throw UnexpectedException.wrap(se);
            }
            finally
            {
                try {if (labelRS != null) labelRS.close();} catch (SQLException se) {}
                try {if (nameRS != null) nameRS.close();} catch (SQLException se) {}
            }
        }
    }

    public void afterSchemaUpdate(ModuleContext moduleContext)
    {
        if (moduleContext.getInstalledVersion() >= 1.3 && moduleContext.getInstalledVersion() < 2.11)
        {
            try
            {
                SampleManager.getInstance().upgradeRequirementsTables();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        if (moduleContext.getInstalledVersion() >= 1.74 && moduleContext.getInstalledVersion() < 2.21)
        {
            ReportManager.UpgradeReport_22_23 upgrade = new ReportManager.UpgradeReport_22_23();
            upgrade.upgradeStudyReports();
        }

        if (moduleContext.getInstalledVersion() >= 1.3 && moduleContext.getInstalledVersion() < 8.13)
        {
            try
            {
                SpecimenImporter.updateAllCalculatedSpecimenData(moduleContext.getUpgradeUser());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

        if (moduleContext.getInstalledVersion() >= 1.3 && moduleContext.getInstalledVersion() < 8.23)
        {
            try
            {
                StudyUpgrader.upgradeExtensibleTables_83(moduleContext.getUpgradeUser());
            }
            catch (SQLException se)
            {
                throw new RuntimeSQLException(se);
            }
        }

        if (moduleContext.getInstalledVersion() >= 1.3 && moduleContext.getInstalledVersion() < 8.291)
        {
            try
            {
                SpecimenImporter.updateAllCalculatedSpecimenData(moduleContext.getUpgradeUser());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }

    }

    private static class ReportsWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWebPartFactory()
        {
            super("Reports and Views", WebPartFactory.LOCATION_RIGHT);
            addLegacyNames("Reports");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ACL.PERM_READ))
                return new HtmlView("Reports and Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Reports and Views", "This folder does not contain a study");
            return new ReportsController.ReportsWebPart(false);
        }
    }

    private static class ReportsWideWebPartFactory extends BaseWebPartFactory
    {
        public ReportsWideWebPartFactory()
        {
            super("Reports and Views");
            addLegacyNames("Reports");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!portalCtx.hasPermission(ACL.PERM_READ))
                return new HtmlView("Reports and Views", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Reports and Views", "This folder does not contain a study");

            return new ReportsController.ReportsWebPart(!"right".equalsIgnoreCase(webPart.getLocation()));
        }
    }

    private static class SamplesWebPartFactory extends DefaultWebPartFactory
    {
        public SamplesWebPartFactory(String position)
        {
            super("Specimens", position, SamplesWebPart.class);
        }

        @Override
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ACL.PERM_READ))
                return new HtmlView("Specimens", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Specimens", "This folder does not contain a study.");
            return new SamplesWebPart(webPart.getLocation().equals(HttpView.BODY));
        }
    }

    private static class DatasetsWebPartFactory extends DefaultWebPartFactory
    {
        public DatasetsWebPartFactory()
        {
            super("Datasets", DatasetsWebPartView.class);
        }


        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException, InstantiationException
        {
            if (!portalCtx.hasPermission(ACL.PERM_READ))
                return new HtmlView("Datasets", portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data");

            if (null == StudyManager.getInstance().getStudy(portalCtx.getContainer()))
                return new HtmlView("Datasets", "This folder does not contain a study.");

            return new DatasetsWebPartView();
        }
    }


    private static class EnrollmentChartWebPartFactory extends BaseWebPartFactory
    {
        public EnrollmentChartWebPartFactory()
        {
            super("Enrollment Report");
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            Container c = portalCtx.getContainer();
            Report report = EnrollmentReport.getEnrollmentReport(portalCtx.getUser(), StudyManager.getInstance().getStudy(c), true);
            WebPartView view = new EnrollmentReport.EnrollmentView(report);
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    private static class StudyDesignsWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignsWebPartFactory()
        {
            super("Vaccine Study Protocols");
            addLegacyNames("Study Designs");
        }
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new StudyDesignsWebPart(portalCtx, true);
        }
    }
    
    private static class StudyDesignSummaryWebPartFactory extends BaseWebPartFactory
    {
        public StudyDesignSummaryWebPartFactory()
        {
            super("Study Protocol Summary");
        }
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            JspView view =  new JspView("/org/labkey/study/designer/view/studyDesignSummary.jsp");
            view.setTitle("Study Protocol Summary");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }
    }

    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        Set<Class<? extends TestCase>> set = new HashSet<Class<? extends TestCase>>();
        set.add(StudyManager.StudyTestCase.class);
        return set;
    }
}
