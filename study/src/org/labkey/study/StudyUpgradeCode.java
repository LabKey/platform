/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.User;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.SpecimenDomainKind;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenTablesProvider;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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

    private static final String STUDY_FORMAT_STRINGS = "DefaultStudyFormatStrings";
    private static final String DATE_FORMAT_STRING = "DateFormatString";
    private static final String NUMBER_FORMAT_STRING = "NumberFormatString";

    // Invoked from study-13.30-14.10.sql
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


    // Invoked from study-13.30-14.10.sql
    @SuppressWarnings("unused")
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


    private void migrateSpecimenTables(Study study, boolean createTablesOnly)
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

        // 15.1 changes the constraints that were to study.site, one from Vial, one from SpecimentEvent
        // We need to drop them here. They'll get added in migrateSpecimenTypeAndLocationTables
        TableInfo locationNew = StudySchema.getInstance().getTableInfoSite(study.getContainer());
        String vialConstraintName = "FK_CurrentLocation_" + vialNew.getMetaDataName() + "_" + locationNew.getMetaDataName();
        vialNew.getSqlDialect().dropIfExists(vialNew.getSchema(), vialNew.getMetaDataName(), "CONSTRAINT", vialConstraintName);
        String eventConstraintName = "FK_LabId_" + specimeneventNew.getMetaDataName() + "_" + locationNew.getMetaDataName();
        vialNew.getSqlDialect().dropIfExists(specimeneventNew.getSchema(), specimeneventNew.getMetaDataName(), "CONSTRAINT", eventConstraintName);

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


    /*
        upgrade provisioned dataset domains to always include data and container

        Invoked from study-13.30-14.10.sql
    */
    @SuppressWarnings("unused")
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
            for (DatasetDefinition def : study.getDatasets())
            {
                migrateDatasetStorage(def);
                uncacheDef(def);
            }
        }

        DbScope.getLabKeyScope().invalidateSchema(StudySchema.getInstance().getDatasetSchema());
    }

    private void uncacheDef(DatasetDefinition def)
    {
        TableInfo t = def.getStorageTableInfo();
        t.getSchema().getScope().invalidateTable(StudySchema.getInstance().getDatasetSchema(), t.getName());
        StudyManager.getInstance().uncache(def);
    }

    private void migrateDatasetStorage(DatasetDefinition def) throws ChangePropertyDescriptorException
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


    private void renameColumnWithTheNameOfWhichIDoNotApprove(DatasetDefinition def, ColumnInfo columnInfo) throws ChangePropertyDescriptorException
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

    // Invoked from study-13.30-14.10.sql
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

    // Invoked by study-14.30-15.10.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public void migrateSpecimenTypeAndLocationTables(final ModuleContext context)
    {
        if (!context.isNewInstall())
        {
            Set<Container> allContainers = ContainerManager.getAllChildren(ContainerManager.getRoot());
            for (Container c : allContainers)
            {
                if (null != c)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                        migrateSpecimenTypeAndLocationTables(study, true, context);
                }
            }

            for (Container c : allContainers)
            {
                if (null != c)
                {
                    Study study = StudyManager.getInstance().getStudy(c);
                    if (null != study)
                        migrateSpecimenTypeAndLocationTables(study, false, context);
                }
            }
        }

        // Drop any constraints on Site table left by orphaned Vial and SpecimenEvent tables
        dropConstraintsFromOrphanedSpecimenTables();

        // Now drop the tables
        SqlDialect dialect = StudySchema.getInstance().getSchema().getScope().getSqlDialect();
        dialect.dropIfExists(StudySchema.getInstance().getSchema(), "Site", "TABLE", null);
        dialect.dropIfExists(StudySchema.getInstance().getSchema(), "SpecimenPrimaryType", "TABLE", null);
        dialect.dropIfExists(StudySchema.getInstance().getSchema(), "SpecimenDerivative", "TABLE", null);
        dialect.dropIfExists(StudySchema.getInstance().getSchema(), "SpecimenAdditive", "TABLE", null);
    }

    private void migrateSpecimenTypeAndLocationTables(Study study, boolean createTablesOnly, final ModuleContext context)
    {
        TableInfo location = StudySchema.getInstance().getTableInfoSite(study.getContainer());
        TableInfo primaryType = StudySchema.getInstance().getTableInfoSpecimenPrimaryType(study.getContainer());
        TableInfo derivative = StudySchema.getInstance().getTableInfoSpecimenDerivative(study.getContainer());
        TableInfo additive = StudySchema.getInstance().getTableInfoSpecimenAdditive(study.getContainer());

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
            _log.info("Getting bare study schema failed: " + study.getEntityId());
        }
        TableInfo locationOld = db.getTable("site");
        TableInfo primaryTypeOld = db.getTable("specimenprimarytype");
        TableInfo derivativeOld = db.getTable("specimenderivative");
        TableInfo additiveOld = db.getTable("specimenadditive");
        _copy(study, locationOld, location, true);
        _copy(study, primaryTypeOld, primaryType, true);
        _copy(study, derivativeOld, derivative, true);
        _copy(study, additiveOld, additive, true);

        // It's possible a study's specimen tables don't exist, in which case we don't have to migrate the constraints;
        // We'll force them to get created as well, if they don't exist
        StudySchema.getInstance().getTableInfoSpecimen(study.getContainer(), context.getUpgradeUser());

        boolean vialExists = null != StudySchema.getInstance().getTableInfoVialIfExists(study.getContainer());
        boolean specimenEventExists = null != StudySchema.getInstance().getTableInfoSpecimenEventIfExists(study.getContainer());

        // Vial and SpecimenEvent each have an FK to Site. We need to drop that and add one to the provisioned Location/Site table
        if (vialExists)
            migrateForeignKeyConstraint(StudySchema.getInstance().getTableInfoVial(study.getContainer()), locationOld, location, "CurrentLocation");
        else
            StudySchema.getInstance().getTableInfoVial(study.getContainer(), context.getUpgradeUser());

        if (specimenEventExists)
            migrateForeignKeyConstraint(StudySchema.getInstance().getTableInfoSpecimenEvent(study.getContainer()), locationOld, location, "LabId");
        else
            StudySchema.getInstance().getTableInfoSpecimenEvent(study.getContainer(), context.getUpgradeUser());
    }

    private void migrateForeignKeyConstraint(TableInfo tableInfoWithFK, TableInfo oldDestTableInfo, TableInfo newDestTableInfo, String fieldName)
    {
        String constraintName = "FK_" + fieldName + "_" + tableInfoWithFK.getMetaDataName() + "_" + oldDestTableInfo.getMetaDataName();
        tableInfoWithFK.getSqlDialect().dropIfExists(tableInfoWithFK.getSchema(), tableInfoWithFK.getMetaDataName(), "CONSTRAINT", constraintName);

        SQLFragment sql = new SQLFragment("ALTER TABLE ");
        sql.append(tableInfoWithFK.getSelectName()).append(" ADD CONSTRAINT FK_")
           .append(fieldName).append("_").append(tableInfoWithFK.getMetaDataName()).append("_").append(newDestTableInfo.getMetaDataName())
           .append(" FOREIGN KEY (").append(fieldName).append(") REFERENCES ").append(newDestTableInfo.getSelectName()).append(" (RowId)");

        try
        {
            new SqlExecutor(tableInfoWithFK.getSchema()).execute(sql);
        }
        catch (Exception e)
        {
            _log.error("Exception message: " + e.getMessage() + "\n executing sql: " + sql.toDebugString());
        }
    }

    private void dropConstraintsFromOrphanedSpecimenTables()
    {
        SQLFragment sql = new SQLFragment("SELECT constraint_name As ConstraintName, table_name AS ForeignTableName FROM information_schema.table_constraints\n");
        sql.append("WHERE constraint_type = 'FOREIGN KEY' AND table_schema = 'specimentables' AND constraint_name LIKE '%_site'");

        final DbSchema schema = DbSchema.get("specimentables", DbSchemaType.Provisioned);
        new SqlSelector(schema.getScope(), sql).forEachMap(new Selector.ForEachBlock<Map<String, Object>>()
        {
            @Override
            public void exec(Map<String, Object> object) throws SQLException
            {
                TableInfo tableInfo = schema.getTable((String)object.get("ForeignTableName"));
                String constraintName = (String)object.get("ConstraintName");
                if (null != tableInfo && null != constraintName)
                {
                    tableInfo.getSqlDialect().dropIfExists(tableInfo.getSchema(), tableInfo.getMetaDataName(), "CONSTRAINT", constraintName);
                    _log.info("Table '" + tableInfo.getMetaDataName() + "' has constraint '" + constraintName + "' and appears to be orphaned.");
                }
            }
        });
    }

    // Invoked by study-14.30-15.10.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void upgradeStudySnapshotsType(final ModuleContext context)
    {
        StudySchema studySchema = StudySchema.getInstance();

        TableInfo tableInfo = studySchema.getTableInfoStudySnapshot();

        List<List<?>> paramsList = new ArrayList<>();

        // NOTE: avoiding getArrayList(StudySnapshot.class) because bean could change (and did change with this upgrade).
        for ( Map<String, Object> snapshot : new TableSelector(tableInfo).getMapArray())
        {
            HashMap<String, Object> settings;
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                settings = mapper.readValue((String)snapshot.get("settings"), HashMap.class);
            }
            catch (IOException e)
            {
                // In previous usage of GSON, there was potential for unhandled exception from gson.fromJson().
                // Jackson explicitly throws exceptions, but there's still nothing we can do about it if one happens here.
                _log.error("Error passing settings json into map.", e);
                continue;
            }

            String type;
            int rowId = (int)snapshot.get("rowid");
            if (settings != null)
            {
                if (settings.get("type") != null)
                    type = (String)settings.get("type");
                else
                {
                    if(settings.get("visits") == null)
                        type = StudySnapshotType.ancillary.toString();
                    else
                        type = StudySnapshotType.publish.toString();
                }
                settings.remove("type");
            }
            else
                type = StudySnapshotType.publish.toString();

            String settingsStr;
            try
            {
                settingsStr = mapper.writeValueAsString(settings);
            }
            catch (IOException e)
            {
                // In previous usage of GSON, there was potential for unhandled exception from gson.fromJson().
                // Jackson explicitly throws exceptions, but there's still nothing we can do about it if one happens here.
                _log.error("Error parsing settings map into json.", e);
                continue;
            }

            paramsList.add(Arrays.asList(type, settingsStr, rowId));
        }
        String sql = "UPDATE " + tableInfo + " SET type=?,settings=? WHERE rowid=?";

        try (DbScope.Transaction transaction = studySchema.getSchema().getScope().ensureTransaction())
        {
            try
            {
                Table.batchExecute(studySchema.getSchema(), sql, paramsList);
            }
            catch (SQLException e)
            {
                _log.error("Error upgrading study type.", e);
            }
            transaction.commit();
        }
    }
}