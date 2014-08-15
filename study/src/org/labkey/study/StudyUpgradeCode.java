/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenTablesProvider;
import org.labkey.study.query.StudyQuerySchema;
import org.labkey.study.reports.CommandLineSplitter;
import org.labkey.study.reports.DefaultCommandLineSplitter;
import org.labkey.study.reports.ExternalReport;
import org.labkey.study.reports.ParticipantReport;
import org.labkey.study.reports.ParticipantReportDescriptor;
import org.labkey.study.reports.WindowsCommandLineSplitter;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 4:54:36 PM
 */
public class StudyUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(StudyUpgradeCode.class);

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
                    for (DataSetDefinition dsd : StudyManager.getInstance().getDatasetDefinitions(study))
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
                        try
                        {
                            WriteableLookAndFeelProperties.saveDefaultDateFormat(c, dateFormat);
                        }
                        catch (IllegalArgumentException e)
                        {
                            _log.warn("Study date format " + dateFormat + " in " + c.getPath() + " will not be migrated to new folder date format: " + e.getMessage());
                        }

                    String numberFormat = props.get(NUMBER_FORMAT_STRING);

                    if (null != numberFormat)
                        try
                        {
                            WriteableLookAndFeelProperties.saveDefaultNumberFormat(c, numberFormat);
                        }
                        catch (IllegalArgumentException e)
                        {
                            _log.warn("Study number format " + numberFormat + " in " + c.getPath() + " will not be migrated to new folder number format: " + e.getMessage());
                        }

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


    public void migrateSpecimenTables(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        List<String> containerIds = new SqlSelector(scope, "SELECT Container FROM study.specimen UNION SELECT Container FROM study.vial UNION SELECT Container FROM study.specimenevent").getArrayList(String.class);

//        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            for (String containerId : containerIds)
            {
                Container c = ContainerManager.getForId(containerId);
                if (null == c)
                    continue;
                Study study = StudyManager.getInstance().getStudy(c);
                migrateSpecimenTables(study, true);
            }
//            tx.commit();
        }

//        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            for (String containerId : containerIds)
            {
                Container c = ContainerManager.getForId(containerId);
                if (null == c)
                    continue;
                Study study = StudyManager.getInstance().getStudy(c);
                migrateSpecimenTables(study, false);
            }
//            tx.commit();
        }
    }


    public void migrateSpecimenTables(Study study, boolean createTablesOnly)
    {
        TableInfo specimenNew = StudySchema.getInstance().getTableInfoSpecimen(study.getContainer(), null);
        TableInfo vialNew = StudySchema.getInstance().getTableInfoVial(study.getContainer(), null);
        TableInfo specimeneventNew = StudySchema.getInstance().getTableInfoSpecimenEvent(study.getContainer(), null);

        if (createTablesOnly)
            return;

        DbSchema db = DbSchema.get("study");
        try
        {
            DbSchema bareStudySchema = DbSchema.createFromMetaData(db.getScope(), "study", DbSchemaType.Bare);
            db = bareStudySchema;
        }
        catch (SQLException e)
        {

        }
        TableInfo specimenOld = db.getTable("specimen");
        TableInfo vialOld = db.getTable("vial");
        TableInfo specimeneventOld = db.getTable("specimenevent");
        _copy(study, specimenOld, specimenNew, true);
        _copy(study, vialOld, vialNew, false);
        _copy(study, specimeneventOld, specimeneventNew, true);
    }


    private void _copy(Study study, TableInfo from, TableInfo to, boolean hasIdentity)
    {
        SQLFragment sqlfCols = new SQLFragment();
        String comma = "";

        // Only include columns in BOTH tables in the sql
        Set<String> toColumnNames = new HashSet<>();
        for (ColumnInfo col : to.getColumns())
        {
            toColumnNames.add(col.getName().toLowerCase());
        }

        for (ColumnInfo col : from.getColumns())
        {
            if (toColumnNames.contains(col.getName().toLowerCase()))
            {
                sqlfCols.append(comma);
                sqlfCols.append(col.getSelectName());
                comma = ",";
            }
        }

        SQLFragment f = new SQLFragment();
        if (to.getSqlDialect().isSqlServer() && hasIdentity)
        {
            f.append("SET IDENTITY_INSERT ").append(to.getSelectName()).append(" ON;");
        }
        f.append("INSERT INTO ").append(to.getSelectName()).append(" (").append(sqlfCols).append(")\n");
        f.append("SELECT ").append(sqlfCols).append(" FROM ").append(from.getFromSQL("x")).append("\n");
        f.append("WHERE Container=?;");
        f.add(study.getContainer());
        if (to.getSqlDialect().isSqlServer() && hasIdentity)
        {
            f.append("SET IDENTITY_INSERT ").append(to.getSelectName()).append(" OFF;");
        }
        else if (to.getSqlDialect().isPostgreSQL() && hasIdentity)
        {
            SQLFragment resetSeq = new SQLFragment();
            resetSeq.append("SELECT setval(\n");
            resetSeq.append("  pg_get_serial_sequence('").append(to.getSelectName()).append("', 'rowid'),\n");
            resetSeq.append("  (SELECT MAX(rowid) FROM ").append(to.getSelectName()).append(") + 1");
            resetSeq.append(");\n");
            f.append(resetSeq);
        }

        new SqlExecutor(StudySchema.getInstance().getScope()).execute(f);
    }


    /* upgrade provisioned dataset domains to always include data and container */

    public void migrateProvisionedDatasetTables141(final ModuleContext context) throws ChangePropertyDescriptorException
    {
        if (context.isNewInstall())
            return;

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        List<String> containerIds = new SqlSelector(scope, "SELECT DISTINCT Container FROM study.dataset").getArrayList(String.class);

        for (String containerId : containerIds)
        {
            Container c = ContainerManager.getForId(containerId);
            if (null == c)
                continue;
            StudyImpl study = StudyManager.getInstance().getStudy(c);
            if (null == study)
                continue;
            for (DataSetDefinition def : study.getDatasets())
            {
                migrateDatasetStorage(def);
                uncacheDef(def);
            }
        }

        DbScope.getLabkeyScope().invalidateSchema(StudySchema.getInstance().getDatasetSchema());
    }

    void uncacheDef(DataSetDefinition def)
    {
        TableInfo t = def.getStorageTableInfo();
        t.getSchema().getScope().invalidateTable(StudySchema.getInstance().getDatasetSchema(), t.getName());
        StudyManager.getInstance().uncache(def);
    }

    private void migrateDatasetStorage(DataSetDefinition def) throws ChangePropertyDescriptorException
    {
        TableInfo t = def.getStorageTableInfo();
        ColumnInfo dt = t.getColumn("date");

        if (null == dt || dt.getJdbcType() != JdbcType.TIMESTAMP)
        {
            if (null != dt)
                renameColumnWithTheNameOfWhichIDoNotApprove(def, dt);
            new SqlExecutor(t.getSchema()).execute("ALTER TABLE " + t.getSelectName() + " ADD Date " + t.getSqlDialect().getDefaultDateTimeDataType());
        }

        ColumnInfo ct = t.getColumn("container");
        if (null == ct || (ct.getJdbcType() != JdbcType.GUID && ct.getJdbcType() != JdbcType.VARCHAR))
        {
            // I don't really expect this to happen, creating a container column in a dataset in 13.3 causes all kinds of bad
            if (null != ct)
                renameColumnWithTheNameOfWhichIDoNotApprove(def,ct);
            new SqlExecutor(t.getSchema()).execute("ALTER TABLE " + t.getSelectName() + " ADD Container " + t.getSqlDialect().getGuidType());
        }
        new SqlExecutor(t.getSchema()).execute("UPDATE " + t.getSelectName() + " SET Container = ?", def.getContainer());
        if (t.getSqlDialect().isSqlServer())
            new SqlExecutor(t.getSchema()).execute("ALTER TABLE " + t.getSelectName() + " ALTER COLUMN Container " + t.getSqlDialect().getGuidType() + " NOT NULL");
        else
            new SqlExecutor(t.getSchema()).execute("ALTER TABLE " + t.getSelectName() + " ALTER COLUMN Container SET NOT NULL");
    }


    void renameColumnWithTheNameOfWhichIDoNotApprove(DataSetDefinition def, ColumnInfo columnInfo) throws ChangePropertyDescriptorException
    {
        try
        {
            StorageProvisioner.setAllowRenameOfColumnsDuringUpgrade(true);
            Domain d = def.getDomain();
            DomainProperty dp = d.getPropertyByName(columnInfo.getName());
            dp.setName(columnInfo.getName() + "_" + columnInfo.getJdbcType().getJavaClass().getSimpleName());
            d.save(null);
        }
        finally
        {
            StorageProvisioner.setAllowRenameOfColumnsDuringUpgrade(false);
        }
    }


    // Splits DrawTimeStamp into DrawDate and DrawTime in SpecimenEvent and Specimen tables
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateSpecimenDrawTimeStamp(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        User user = context.getUpgradeUser();
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        List<String> containerIds = new SqlSelector(scope, "SELECT EntityId FROM core.containers").getArrayList(String.class);
        for (String containerId : containerIds)
        {
            Container c = ContainerManager.getForId(containerId);
            if (null == c)
                continue;
            Study study = StudyManager.getInstance().getStudy(c);
            if (null == study)
                continue;
            try
            {
                SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(c, user, null);
                Domain specimenDomain = specimenTablesProvider.getDomain("Specimen", false);
                if (null == specimenDomain)
                    continue;

                // Add DrawDate and DrawTime if not already there
                PropertyStorageSpec drawDateStorageSpec = SpecimenDomainKind.getDrawDateStorageSpec();
                PropertyStorageSpec drawTimeStorageSpec = SpecimenDomainKind.getDrawTimeStorageSpec();
                boolean foundDrawDate = false;
                for (DomainProperty property : specimenDomain.getProperties())
                {
                    if (drawDateStorageSpec.getName().equalsIgnoreCase(property.getName()))
                    {
                        foundDrawDate = true;
                        break;
                    }
                }

                if (!foundDrawDate)
                {
                    specimenDomain.addProperty(drawDateStorageSpec);
                    specimenDomain.addProperty(drawTimeStorageSpec);
                    specimenDomain.save(user, true);
                }

                TableInfo specimenTable = StudySchema.getInstance().getTableInfoSpecimen(c, user);
                SQLFragment specimenSql = new SQLFragment("UPDATE ");
                specimenSql.append(specimenTable.getSelectName())
                           .append(" SET ").append(drawDateStorageSpec.getName())
                           .append(" = CAST(DrawTimeStamp As Date), ")
                           .append(drawTimeStorageSpec.getName()).append(" = CAST(DrawTimeStamp As Time)");
                new SqlExecutor(scope).execute(specimenSql);
            }
            catch (Throwable e)
            {
                _log.error("Error migrating provisioned specimen tables in " + c.toString(), e);
            }
        }
    }
}