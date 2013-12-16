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

import org.apache.commons.collections15.MultiMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableChange;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.CommandLineSplitter;
import org.labkey.study.reports.DefaultCommandLineSplitter;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ParticipantReportDescriptor;
import org.labkey.study.reports.WindowsCommandLineSplitter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(StudyUpgradeCode.class);

    // invoked by study-11.32-11.33.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateReportProperties(ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            try
            {
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
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            renameDataSetParticipantSequenceKey(ContainerManager.getRoot());
            transaction.commit();
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

    private Map<Container, Boolean> _propertyMap = new HashMap<>();

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
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Report report : ReportService.get().getReports(new SimpleFilter()))
                {
                    // for existing participant reports, upgrade the descriptor type to enable
                    // alternate participant ID handling during export.
                    if (ParticipantReport.TYPE.equals(report.getType()))
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
                transaction.commit();
            }
            catch (Exception e)
            {
                _log.error("An error occurred upgrading participant reports: ", e);
            }
        }
    }

    // invoked for module version < 13.11 in StudyModule.afterUpdate
    @DeferredUpgrade
    public static void upgradeDatasetLabelsToNames(ModuleContext context)
    {
        // one time upgrade to migrate all queryName usages of dataset labels in reports, custom views, and query snapshots
        // to instead be references to the dataset name (which is what will be used going forward)
        MultiMap<Container, Container> containerTree = ContainerManager.getContainerTree();
        for (Map.Entry<Container, Collection<Container>> treeEntry : containerTree.entrySet())
        {
            for (Container container : treeEntry.getValue())
            {
                Study study = StudyManager.getInstance().getStudy(container);
                if (study != null)
                {
                    List<QueryChangeListener.QueryPropertyChange> queryPropertyChanges = new ArrayList<>();
                    for (DataSetDefinition dsd : StudyManager.getInstance().getDataSetDefinitions(study))
                    {
                        if (!dsd.getName().equals(dsd.getLabel()))
                        {
                            queryPropertyChanges.add(new QueryChangeListener.QueryPropertyChange<>(
                                    QueryService.get().getUserSchema(context.getUpgradeUser(), container, StudyQuerySchema.SCHEMA_NAME).getQueryDefForTable(dsd.getName()),
                                    QueryChangeListener.QueryProperty.Name,
                                    dsd.getLabel(),
                                    dsd.getName()
                            ));
                        }
                    }

                    if (queryPropertyChanges.size() > 0)
                    {
                        QueryService.get().fireQueryChanged(User.getSearchUser(), container, null, new SchemaKey(null, StudyQuerySchema.SCHEMA_NAME), QueryChangeListener.QueryProperty.Name, queryPropertyChanges);
                    }
                }
            }
        }
    }


    public static final CommandLineSplitter COMMAND_LINE_SPLITTER = SystemUtils.IS_OS_WINDOWS ? new WindowsCommandLineSplitter() : new DefaultCommandLineSplitter();

    // invoked by study-13.23-13.24.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void upgradeExternalReports(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (Report report : ReportService.get().getReports(new SimpleFilter()))
                {
                    // Find external reports and split single "commandLine" parameter into "program" and "arguments", see #18077
                    if (report.getType().equals(ExternalReport.TYPE))
                    {
                        ExternalReport externalReport = (ExternalReport)report;
                        String commandLine = externalReport.getCommandLine();

                        // This report is messed up
                        if (StringUtils.isBlank(commandLine))
                            continue;

                        String[] strings = COMMAND_LINE_SPLITTER.getCommandStrings(commandLine);

                        externalReport.setProgram(strings[0]);

                        if (strings.length > 1)
                        {
                            String arguments = StringUtils.join(ArrayUtils.subarray(strings, 1, strings.length), " ");
                            externalReport.setArguments(arguments);
                        }

                        externalReport.setCommandLine(null);

                        ReportDescriptor descriptor = externalReport.getDescriptor();

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
                        ReportService.get().saveReport(rptContext, descriptor.getReportKey(), externalReport);
                    }
                }
                transaction.commit();
            }
            catch (Exception e)
            {
                _log.error("An error occurred upgrading participant reports: ", e);
            }
        }
    }

    private static final String STUDY_FORMAT_STRINGS = "DefaultStudyFormatStrings";
    private static final String DATE_FORMAT_STRING = "DateFormatString";
    private static final String NUMBER_FORMAT_STRING = "NumberFormatString";

    // TODO: This should work but is not yet invoked... need to add a 13.3x study script that calls it once specimen provisioning branch is merged into trunk
    // Move the "default study format strings" to project/folder-level look & feel settings
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void moveDefaultFormatProperties(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        for (Container c : ContainerManager.getAllChildren(ContainerManager.getRoot()))
        {
            try
            {
                Map<String, String> props = PropertyManager.getProperties(c, STUDY_FORMAT_STRINGS);

                if (!props.isEmpty())
                {
                    String dateFormat = props.get(DATE_FORMAT_STRING);

                    if (null != dateFormat)
                        WriteableLookAndFeelProperties.saveDefaultDateFormat(c, dateFormat);

                    String numberFormat = props.get(NUMBER_FORMAT_STRING);

                    if (null != numberFormat)
                        WriteableLookAndFeelProperties.saveDefaultNumberFormat(c, numberFormat);

                    // Now delete the old property set
                    PropertyManager.getNormalStore().deletePropertySet(c, STUDY_FORMAT_STRINGS);
                }
            }
            catch (Throwable e)
            {
                _log.error("Error migrating study format properties in " + c.toString(), e);
            }
        }
    }
}
