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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudySnapshotType;
import org.labkey.study.model.StudyManager;

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