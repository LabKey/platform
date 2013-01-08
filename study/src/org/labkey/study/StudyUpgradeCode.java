/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.UpgradeUtils;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.StartupListener;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ParticipantReportDescriptor;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(StudyUpgradeCode.class);

    // called at 10.30->10.31
    @SuppressWarnings({"UnusedDeclaration"})
    public void materializeAssayResults(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        // This needs to happen later, after all of the AssayProviders have been registered
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                AssayService.get().upgradeAssayDefinitions(moduleContext.getUpgradeUser(), 11.1);
            }
        });
    }


    /**
     * Called at 10.31->10.32
     * Get rid of the duplicate assay data in datasets and rely on a join to the original data on the assay side
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public void deleteDuplicateAssayDatasetFields(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        deleteDuplicateAssayDatasetFields(context.getUpgradeUser(), ContainerManager.getRoot());
    }

    private void deleteDuplicateAssayDatasetFields(User user, Container c)
    {
        try
        {
            Study study = StudyService.get().getStudy(c);
            if (study != null)
            {
                for (DataSet dataSet : study.getDataSets())
                {
                    if (dataSet.isAssayData())
                    {
                        Domain domain = dataSet.getDomain();
                        for (DomainProperty prop : domain.getProperties())
                        {
                            String keyName = dataSet.getKeyPropertyName();
                            if (keyName == null || !keyName.equalsIgnoreCase(prop.getName()))
                            {
                                prop.delete();
                            }
                        }
                        domain.save(user);
                    }
                }
            }

            // Recurse through the children
            for (Container child : c.getChildren())
            {
                deleteDuplicateAssayDatasetFields(user, child);
            }
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new UnexpectedException(e);
        }
    }

    // called at 11.10->11.101
    @SuppressWarnings({"UnusedDeclaration"})
    public void renameObjectIdToRowId(final ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        // This needs to happen later, after all of the AssayProviders have been registered
        ContextListener.addStartupListener(new StartupListener()
        {
            @Override
            public void moduleStartupComplete(ServletContext servletContext)
            {
                AssayService.get().upgradeAssayDefinitions(moduleContext.getUpgradeUser(), 11.101);
            }
        });
    }

    /* called at 11.11->11.12, PostgreSQL only, to move to case-insensitive UNIQUE INDEXes */
    @SuppressWarnings({"UnusedDeclaration"})
    public void uniquifyDatasetNamesAndLabels(ModuleContext moduleContext)
    {
        if (moduleContext.isNewInstall())
            return;

        try
        {
            TableInfo datasets = DbSchema.get("study").getTable("Dataset");
            UpgradeUtils.uniquifyValues(datasets.getColumn("Name"), new Sort("DatasetId"), false, true);
            UpgradeUtils.uniquifyValues(datasets.getColumn("Label"), new Sort("DatasetId"), false, true);
        }
        catch (SQLException se)
        {
            throw UnexpectedException.wrap(se);
        }
    }

    /* called at 11.21-11.22, adds studyProtocolEntityIds to each study. */
    @SuppressWarnings({"UnusedDeclaration"})
    public void assignProtocolDocumentEntityId(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            //Do upgrade actions here
            try
            {
                doAssignProtocolDocumentEntityId();
            }
            catch (Exception e)
            {
                String msg = "Error running doAssignProtocolDocumentEntityId on StudyModule, upgrade from version " + String.valueOf(moduleContext.getInstalledVersion());
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                //following sends an exception report to mothership if site is configured to do so, but doesn't abort schema upgrade
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, null, false, false);
            }
        }
    }

    private void doAssignProtocolDocumentEntityId() throws SQLException
    {
        TableInfo tinfo = StudySchema.getInstance().getTableInfoStudy();
        String sql = "UPDATE " + tinfo + " SET ProtocolDocumentEntityId = ? WHERE Container = ?";
        Map<String, Object>[] rows = Table.selectMaps(tinfo, Collections.singleton("Container"), null, null);
        for (Map<String, Object> row : rows)
        {
            String container = (String)row.get("Container");
            String protocolDocumentEntityId = GUID.makeGUID();
            Table.execute(tinfo.getSchema(), sql, protocolDocumentEntityId, container);
        }
    }

    // invoked by study-11.32-11.33.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateReportProperties(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            try {
                for (Report report : ReportService.get().getReports(new SimpleFilter()))
                {
                    ReportDescriptor descriptor = report.getDescriptor();

                    String author = descriptor.getProperty("author");
                    if (author != null && NumberUtils.isDigits(author))
                    {
                        Container c = ContainerManager.getForId(report.getContainerId());
                        ensureProperties(c, context.getUpgradeUser());

                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), c, "author", NumberUtils.createInteger(author));
                    }

                    if (descriptor.getProperty("status") != null)
                    {
                        Container c = ContainerManager.getForId(report.getContainerId());
                        ensureProperties(c, context.getUpgradeUser());

                        ReportPropsManager.get().setPropertyValue(report.getEntityId(), c, "status", descriptor.getProperty("status"));
                    }
                }
            }
            catch (Exception e)
            {
                _log.error("An error occurred upgrading report properties: ", e);
            }
        }
    }

    // invoked by study-12.10-12.11.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void renameDataSetParticipantSequenceKey(ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try
        {
            scope.ensureTransaction();
            renameDataSetParticipantSequenceKey(ContainerManager.getRoot());
            scope.commitTransaction();
        }
        catch (SQLException e)
        {
            ModuleUpgrader.getLogger().error("An error occurred renaming ParticipantSequenceKey on DataSets: ", e);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private void renameDataSetParticipantSequenceKey(Container c)
    {
        try
        {
            Study study = StudyService.get().getStudy(c);
            if (study != null)
            {
                for (DataSet dataSet : study.getDataSets())
                {
                    renameDataSetParticipantSequenceKey(c, dataSet);
                }
            }

            // Recurse through the children
            for (Container child : c.getChildren())
            {
                renameDataSetParticipantSequenceKey(child);
            }
        }
        catch (SQLException e)
        {
            throw new UnexpectedException(e);
        }
    }

    private void renameDataSetParticipantSequenceKey(Container c, DataSet dataSet) throws SQLException
    {
        Domain domain = dataSet.getDomain();
        if (domain == null)
        {
            ModuleUpgrader.getLogger().info("No domain for DatSet '" + dataSet.getName() + "', in container '" + c.getPath() + "'.  Skipping.");
            return;
        }

        ModuleUpgrader.getLogger().info("Renaming ParticipantSequenceKey to ParticipantSequenceNum on DatSet '" + dataSet.getName() + "', in container '" + c.getPath() + "'");
        DomainKind kind = domain.getDomainKind();
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        // We should already be running in a transaction with the top-level upgrade script
        assert scope.isTransactionActive();
        Connection con = null;
        try
        {
            con = scope.getConnection();
            
            String tableName = domain.getStorageTableName();
            TableChange change = new TableChange(kind.getStorageSchemaName(), tableName, TableChange.ChangeType.RenameColumns);

            // Casing of the columns must match the names used in DatastDomainKind
            // When the name is too long it is hashed to truncate the length and won't match the existing name otherwise.
            change.addColumnRename("participantsequencekey", "participantsequencenum");
            change.addIndexRename(
                    new PropertyStorageSpec.Index(false, "participantsequencekey"),
                    new PropertyStorageSpec.Index(false, "participantsequencenum"));

            ModuleUpgrader.getLogger().info("Will issue:");
            for (String sql : scope.getSqlDialect().getChangeStatements(change))
            {
                ModuleUpgrader.getLogger().info("\t" + sql);
                con.prepareStatement(sql).execute();
            }
        }
        finally
        {
            scope.releaseConnection(con);
        }
    }

    private Map<Container, Boolean> _propertyMap = new HashMap<Container, Boolean>();

    private void ensureProperties(Container container, User user) throws Exception
    {
        if (!_propertyMap.containsKey(container))
        {
            ReportPropsManager.get().ensureProperty(container, user, "status", "Status", PropertyType.STRING);
            ReportPropsManager.get().ensureProperty(container, user, "author", "Author", PropertyType.INTEGER);

            _propertyMap.put(container, true);
        }
    }

    // invoked by study-12.24-12.25.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void upgradeParticipantReport(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try {
                scope.ensureTransaction();
                for (Report report : ReportService.get().getReports(new SimpleFilter()))
                {
                    // for existing participant reports, upgrade the descriptor type to enable
                    // alternate participant ID handling during export.
                    if (report.getType() == ParticipantReport.TYPE)
                    {
                        ReportDescriptor descriptor = report.getDescriptor();

                        if (!ParticipantReportDescriptor.TYPE.equals(descriptor.getDescriptorType()))
                        {
                            descriptor.setDescriptorType(ParticipantReportDescriptor.TYPE);
                            final Container descriptorContainer = ContainerManager.getForId(descriptor.getContainerId());

                            ContainerUser rptContext = new ContainerUser()
                            {
                                public User getUser()
                                {
                                    return context.getUpgradeUser();
                                }

                                public Container getContainer()
                                {
                                    return descriptorContainer;
                                }
                            };
                            ReportService.get().saveReport(rptContext, descriptor.getReportKey(), report);
                        }
                    }
                }
                scope.commitTransaction();
            }
            catch (Exception e)
            {
                _log.error("An error occurred upgrading participant reports: ", e);
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }
}
