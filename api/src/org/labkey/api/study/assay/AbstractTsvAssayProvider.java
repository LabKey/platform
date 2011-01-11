/*
 * Copyright (c) 2009-2010 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.ModuleUpgrader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.sql.ResultSet;
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

    public AbstractTsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, AssayDataType dataType, AssayTableMetadata tableMetadata)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, dataType, tableMetadata);
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

    @Override
    public abstract FilteredTable createDataTable(AssaySchema schema, ExpProtocol protocol);

    public ActionURL copyToStudy(ViewContext viewContext, ExpProtocol protocol, @Nullable Container study, Map<Integer, AssayPublishKey> dataKeys, List<String> errors)
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getTableMetadata().getResultRowIdFieldKey().toString(), dataKeys.keySet());

            AssaySchema schema = AssayService.get().createSchema(viewContext.getUser(), viewContext.getContainer());
            FilteredTable dataTable = createDataTable(schema, protocol);
            dataTable.setContainerFilter(new ContainerFilter.CurrentAndSubfolders(viewContext.getUser()));
            List<ColumnInfo> columns = dataTable.getColumns();
            SQLFragment sql = QueryService.get().getSelectSQL(dataTable, columns, filter,
                    new Sort(getTableMetadata().getResultRowIdFieldKey().toString()), Table.ALL_ROWS, 0);

            List<Map<String, Object>> dataMaps = new ArrayList<Map<String, Object>>();

            CopyToStudyContext context = new CopyToStudyContext(protocol, viewContext.getUser());

            Container sourceContainer = null;

            // little hack here: since the property descriptors created by the 'addProperty' calls below are not in the database,
            // they have no RowId, and such are never equal to each other.  Since the loop below is run once for each row of data,
            // this will produce a types set that contains rowCount*columnCount property descriptors unless we prevent additions
            // to the map after the first row.  This is done by nulling out the 'tempTypes' object after the first iteration:
            Set<PropertyDescriptor> typeList = new LinkedHashSet<PropertyDescriptor>();
            Set<PropertyDescriptor> tempTypes = typeList;

            Map<PropertyDescriptor, ColumnInfo> pdsToColumns = new HashMap<PropertyDescriptor, ColumnInfo>();
            for (DomainProperty prop : getResultsDomain(protocol).getProperties())
            {
                for (ColumnInfo column : columns)
                {
                    if (column.getName().equalsIgnoreCase(prop.getName()))
                    {
                        pdsToColumns.put(prop.getPropertyDescriptor(), column);
                        break;
                    }
                }
            }

            ResultSet rs = null;

            try
            {
                rs = Table.executeQuery(dataTable.getSchema(), sql);
                while (rs.next())
                {
                    AssayPublishKey publishKey = dataKeys.get(((Integer)dataTable.getColumn("RowId").getValue(rs)).intValue());

                    Container targetStudyContainer = study;
                    if (publishKey.getTargetStudy() != null)
                        targetStudyContainer = publishKey.getTargetStudy();
                    assert targetStudyContainer != null;

                    TimepointType studyType = AssayPublishService.get().getTimepointType(targetStudyContainer);
                    if (tempTypes != null)
                    {
                        tempTypes.add(createPublishPropertyDescriptor(targetStudyContainer, "ObjectId", PropertyType.INTEGER));
                        tempTypes.add(createPublishPropertyDescriptor(targetStudyContainer, "SourceLSID", PropertyType.INTEGER));
                    }

                    Map<String, Object> dataMap = new HashMap<String, Object>();
                    for (Map.Entry<PropertyDescriptor, ColumnInfo> entry : pdsToColumns.entrySet())
                    {

                        PropertyDescriptor pd = entry.getKey();
                        // We should skip properties that are set by the resolver: participantID,
                        // and either date or visit, depending on the type of study
                        boolean skipProperty = PARTICIPANTID_PROPERTY_NAME.equals(pd.getName());

                        if (TimepointType.DATE == studyType)
                            skipProperty = skipProperty || DATE_PROPERTY_NAME.equals(pd.getName());
                        else // it's visit-based
                            skipProperty = skipProperty || VISITID_PROPERTY_NAME.equals(pd.getName());

                        if (!skipProperty)
                        {
                            ColumnInfo column = entry.getValue();
                            Object value = column.getValue(rs);
                            if (pd.isMvEnabled())
                            {
                                for (ColumnInfo c : columns)
                                {
                                    if (c.getName().equalsIgnoreCase(column.getMvColumnName()))
                                    {
                                        value = new MvFieldWrapper(value, (String)c.getValue(rs));
                                        break;
                                    }
                                }
                            }
                            addProperty(pd, value, dataMap, tempTypes);
                        }
                    }

                    ExpRun run = context.getRun(((Integer)dataTable.getColumn("Run").getValue(rs)).intValue());
                    sourceContainer = run.getContainer();

                    dataMap.put("ParticipantID", publishKey.getParticipantId());
                    dataMap.put("SequenceNum", publishKey.getVisitId());
                    if (TimepointType.DATE == studyType)
                    {
                        dataMap.put("Date", publishKey.getDate());
                    }
                    dataMap.put("SourceLSID", run.getLSID());
                    dataMap.put("ObjectId", publishKey.getDataId());
                    dataMap.put("TargetStudy", targetStudyContainer);

                    // CONSIDER: only add run publish properties to target study dataset (avoiding extra columns)
                    addStandardRunPublishProperties(targetStudyContainer, tempTypes, dataMap, run, context);

                    dataMaps.add(dataMap);
                    tempTypes = null;
                }
                
                return AssayPublishService.get().publishAssayData(viewContext.getUser(), sourceContainer, study, protocol.getName(), protocol,
                        dataMaps, new ArrayList<PropertyDescriptor>(typeList), "ObjectId", errors);
            }
            finally
            {
                if (rs != null) { try { rs.close(); } catch (SQLException e) {} }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public void materializeAssayResults(User user, ExpProtocol protocol) throws SQLException
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
            Table.execute(toTable.getSchema(), sql, new Object[0]);
        }

        Container container = protocol.getContainer();
        AssaySchema schema = AssayService.get().createSchema(user, container);

        TableInfo fromTable = new RunDataTable(schema, protocol, true);

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

        SQLFragment fromSQL = QueryService.get().getSelectSQL(fromTable, fromColumns.values(), null, new Sort("ObjectId"), Table.ALL_ROWS, 0);

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
            insertInto.append(", ").append(to.getSelectName());
            select.append(", ").append(from.getAlias());
        }
        insertInto.append(")\n");
        insertInto.append(select);
        insertInto.append("\n FROM (").append(fromSQL).append(") x");

        ModuleUpgrader.getLogger().info("Migrating data for [" + container.getPath() + "]  '" + protocol.getName() + "'");
        ModuleUpgrader.getLogger().info(insertInto.toString());
        Table.execute(toTable.getSchema(), insertInto);

        Set<Container> studyContainers = StudyService.get().getStudyContainersForAssayProtocol(protocol.getRowId());
        for (Container studyContainer : studyContainers)
        {
            Study study = StudyService.get().getStudy(studyContainer);
            for (DataSet dataSet : study.getDataSets())
            {
                if (dataSet.getProtocolId() != null && dataSet.getProtocolId().intValue() == protocol.getRowId())
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
            }
        }

        // Remove the temporary objectId column from the new assay results table
        TableChange removeColumnChange = new TableChange(toTable.getSchema().getName(), toTable.getName(), TableChange.ChangeType.DropColumns);
        removeColumnChange.addColumn(objectIdSpec);
        for (String sql : toTable.getSqlDialect().getChangeStatements(removeColumnChange))
        {
            Table.execute(toTable.getSchema(), sql, new Object[0]);
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
