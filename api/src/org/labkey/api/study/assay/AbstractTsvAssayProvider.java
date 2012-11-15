/*
 * Copyright (c) 2009-2012 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;


/**
 * User: jeckels
 * Date: Jan 26, 2009
 */
public abstract class AbstractTsvAssayProvider extends AbstractAssayProvider
{
    public static final String OBJECT_ID_UPGRADE = "ObjectId__Upgrade";
    private static final String DATASET_SCHEMA_NAME = "studydataset";
    public static final String ASSAY_SCHEMA_NAME = "assayresult";
    public static final String ROW_ID_COLUMN_NAME = "RowId";
    public static final String DATA_ID_COLUMN_NAME = "DataId";

    public AbstractTsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType);
    }

    public ExpData getDataForDataRow(Object dataRowId, ExpProtocol protocol)
    {
        if (dataRowId == null)
            return null;

        Integer id;
        if (dataRowId instanceof Integer)
        {
            id = (Integer)dataRowId;
        }
        else
        {
            try
            {
                id = Integer.parseInt(dataRowId.toString());
            }
            catch (NumberFormatException nfe)
            {
                return null;
            }
        }

        TableInfo table = StorageProvisioner.createTableInfo(getResultsDomain(protocol), DbSchema.get(AbstractTsvAssayProvider.ASSAY_SCHEMA_NAME));

        try
        {
            Map<String, Object>[] rows = Table.select(table, table.getColumns(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME), new SimpleFilter(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME, id), null, Map.class);
            for (Map<String, Object> row : rows)
            {
                Number dataId = (Number)row.get(AbstractTsvAssayProvider.DATA_ID_COLUMN_NAME);
                if (dataId != null)
                {
                    return ExperimentService.get().getExpData(dataId.intValue());
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        return null;
    }

    public void upgradeAssayDefinitions(User user, ExpProtocol protocol, double targetVersion) throws SQLException
    {
        // Due to a bug in the original implementation, this upgrade is handled in two separate pieces.
        if (targetVersion == 11.1)
        {
            // The first step is to create the hard table and migrate data into it
            migrateToHardTable(user, protocol);
        }
        if (targetVersion == 11.101)
        {
            // The second step is to make the dataset match the new expectations for the key property name
            renameObjectIdDatasetColumn(user, protocol);
        }
    }

    /**
     * The original migration code incorrectly left the single property named "ObjectId", instead of
     * "RowId" which is what future copy-to-study operations expect. We need to loop through all datasets created
     * from this assay definition and fix them up.
     */
    public void renameObjectIdDatasetColumn(User user, ExpProtocol protocol) throws SQLException
    {
        List<DataSet> dataSets = StudyService.get().getDatasetsForAssayProtocol(protocol);
        // Iterate through all of the studies that contain a dataset created by copying from this assay
        for (DataSet<DataSet> dataSet : dataSets)
        {
            Domain domain = dataSet.getDomain();
            DomainProperty property = domain.getPropertyByName("ObjectId");
            // Check if we have the old property name - datasets created with 11.1 won't
            if (property != null)
            {
                try
                {
                    // Rename it to be "RowId"
                    property.setName("RowId");
                    property.setLabel("RowId");
                    domain.save(user);
                    // Update the key property name too
                    dataSet = dataSet.createMutable();
                    dataSet.setKeyPropertyName("RowId");
                    dataSet.save(user);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
    }

    public void migrateToHardTable(User user, ExpProtocol protocol) throws SQLException
    {
        // First create the hard table
        Domain resultsDomain = getResultsDomain(protocol);
        TableInfo toTable = StorageProvisioner.createTableInfo(resultsDomain, DbSchema.get(ASSAY_SCHEMA_NAME));

        // Add a column to temporarily hold the objectId
        TableChange addColumnChange = new TableChange(toTable.getSchema().getName(), toTable.getName(), TableChange.ChangeType.AddColumns);
        PropertyStorageSpec objectIdSpec = new PropertyStorageSpec(OBJECT_ID_UPGRADE, Types.INTEGER);
        addColumnChange.addColumn(objectIdSpec);
        for (String sql : toTable.getSqlDialect().getChangeStatements(addColumnChange))
        {
            Table.execute(toTable.getSchema(), sql);
        }

        Container container = protocol.getContainer();
        AssayProtocolSchema schema = createProtocolSchema(user, container, protocol, null);

        @SuppressWarnings({"deprecation"})
        RunDataTable fromTable = new RunDataTable(schema, true);
        fromTable.setContainerFilter(ContainerFilter.EVERYTHING);

        // Build up a list of all the columns we need from the source table
        List<FieldKey> selectFKs = new ArrayList<FieldKey>();
        for (ColumnInfo columnInfo : fromTable.getColumns())
        {
            // Include all the base columns
            selectFKs.add(columnInfo.getFieldKey());
        }
        for (DomainProperty property : resultsDomain.getProperties())
        {
            // Plus the custom properties
            selectFKs.add(FieldKey.fromParts("Properties", property.getName()));
        }

        Map<FieldKey, ColumnInfo> fromColumns = QueryService.get().getColumns(fromTable, selectFKs);

        Map<String, ColumnInfo> colMap = new CaseInsensitiveHashMap<ColumnInfo>();
        for (ColumnInfo c : fromColumns.values())
        {
            if (null != c.getPropertyURI())
                colMap.put(c.getPropertyURI(), c);
            colMap.put(c.getName(), c);
        }

        SQLFragment fromSQL = QueryService.get().getSelectSQL(fromTable, fromColumns.values(), null, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

        // 
        SQLFragment insertInto = new SQLFragment("INSERT INTO " + toTable.getSelectName() + " (" + objectIdSpec.getName());
        SQLFragment select = new SQLFragment("SELECT ObjectId" );
        for (ColumnInfo to : toTable.getColumns())
        {
            ColumnInfo from = colMap.get(to.getPropertyURI());
            if (null == from)
                from = colMap.get(to.getName());
            if (null == from)
            {
                String name = to.getName().toLowerCase();
                if (name.endsWith("_" + MvColumn.MV_INDICATOR_SUFFIX.toLowerCase()))
                {
                    from = colMap.get(name.substring(0,name.length()-(MvColumn.MV_INDICATOR_SUFFIX.length()+1)) + MvColumn.MV_INDICATOR_SUFFIX);
                    if (null == from)
                        continue;
                }
                else
                {
                    ModuleUpgrader.getLogger().error("Could not copy column: " + container.getId() + "-" + container.getPath() + " " + protocol.getRowId() + "-" + protocol.getName() + " " + to.getName());
                    continue;
                }
            }
            insertInto.append(", ").append(schema.getDbSchema().getSqlDialect().makeLegalIdentifier(to.getSelectName()));
            select.append(", ").append(from.getAlias());
        }
        insertInto.append(")\n");
        insertInto.append(select);
        insertInto.append("\n FROM (").append(fromSQL).append(") x ORDER BY ObjectId ");

        ModuleUpgrader.getLogger().info("Migrating data for [" + container.getPath() + "]  '" + protocol.getName() + "'");
        ModuleUpgrader.getLogger().info(insertInto.toString());
        Table.execute(toTable.getSchema(), insertInto);

        List<DataSet> dataSets = StudyService.get().getDatasetsForAssayProtocol(protocol);
        for (DataSet dataSet : dataSets)
        {
            Domain dataSetDomain = dataSet.getDomain();
            SQLFragment updateKeysSQL = new SQLFragment("UPDATE " + DATASET_SCHEMA_NAME + "." + dataSetDomain.getStorageTableName());
            updateKeysSQL.append(" SET _key = (SELECT RowId FROM ");
            updateKeysSQL.append(ASSAY_SCHEMA_NAME + "." + toTable.getName());
            updateKeysSQL.append(" WHERE CAST(_key AS INT) = " + OBJECT_ID_UPGRADE + ")");

            int copyFixupCount = Table.execute(toTable.getSchema(), updateKeysSQL);

            SQLFragment updateObjectIdSQL = new SQLFragment("UPDATE " + DATASET_SCHEMA_NAME + "." + dataSetDomain.getStorageTableName());
            updateObjectIdSQL.append(" SET ObjectId = CAST(_key AS INT)");
            Table.execute(toTable.getSchema(), updateObjectIdSQL);

            ModuleUpgrader.getLogger().info("Migrated ObjectId to RowId for " + copyFixupCount + " in " + dataSet.getContainer().getPath() + "." + dataSet.getName());
        }

        // Remove the temporary objectId column from the new assay results table
        TableChange removeColumnChange = new TableChange(toTable.getSchema().getName(), toTable.getName(), TableChange.ChangeType.DropColumns);
        removeColumnChange.addColumn(objectIdSpec);
        for (String sql : toTable.getSqlDialect().getChangeStatements(removeColumnChange))
        {
            Table.execute(toTable.getSchema(), sql);
        }

        // Delete the data from OntologyManager
        SQLFragment objectIdsSQL = new SQLFragment("(SELECT child.objectid FROM exp.object child, exp.object parent, " +
                "exp.data d, exp.experimentrun r\n" +
                "\tWHERE child.ownerobjectid = parent.objectid AND parent.objecturi = d.lsid AND d.runid = r.rowid AND\n" +
                "\tr.protocollsid = ?)");
        objectIdsSQL.add(protocol.getLSID());

        SQLFragment deleteObjectPropertiesSQL = new SQLFragment("DELETE FROM exp.objectproperty WHERE objectid IN ");
        deleteObjectPropertiesSQL.append(objectIdsSQL);
        Table.execute(toTable.getSchema(), deleteObjectPropertiesSQL);
        ModuleUpgrader.getLogger().info("Deleted property values from OntologyManager for protocol " + protocol.getName());

        SQLFragment deleteObjectsSQL = new SQLFragment("DELETE FROM exp.object WHERE objectid IN ");
        deleteObjectsSQL.append(objectIdsSQL);
        Table.execute(toTable.getSchema(), deleteObjectsSQL);
        ModuleUpgrader.getLogger().info("Deleted data rows from OntologyManager for protocol " + protocol.getName());
    }
}
