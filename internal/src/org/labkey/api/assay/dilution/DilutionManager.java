/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.assay.dilution;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.nab.NabSpecimen;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableResultSet;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: 5/8/13
 */
public class DilutionManager
{
    public static final String CELL_CONTROL_SAMPLE = "CELL_CONTROL_SAMPLE";
    public static final String VIRUS_CONTROL_SAMPLE = "VIRUS_CONTROL_SAMPLE";

    /**
     * Since the nab assay existed long before a dilution superclass existed, we retain the underlying storage of
     * the data in the original nab schema rather than migrate to a new dilution schema and tables (although this
     * could change, if there is too much confusion)
     */
    public static final String CUTOFF_VALUE_TABLE_NAME = "CutoffValue";
    public static final String NAB_SPECIMEN_TABLE_NAME = "NAbSpecimen";
    public static final String WELL_DATA_TABLE_NAME = "WellData";
    public static final String DILUTION_DATA_TABLE_NAME = "DilutionData";
    public static final String VIRUS_TABLE_NAME = "Virus";

    public static final String NAB_DBSCHEMA_NAME = "nab";
    public static final String OOR_INDICATOR_SUFFIX = "OORIndicator";

    public static DbSchema getSchema()
    {
        return DbSchema.get(NAB_DBSCHEMA_NAME);
    }

    public static TableInfo getTableInfoNAbSpecimen()
    {
        return getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
    }

    public static TableInfo getTableInfoCutoffValue()
    {
        return getSchema().getTable(CUTOFF_VALUE_TABLE_NAME);
    }

    public static TableInfo getTableInfoWellData()
    {
        return getSchema().getTable(WELL_DATA_TABLE_NAME);
    }

    public static TableInfo getTableInfoDilutionData()
    {
        return getSchema().getTable(DILUTION_DATA_TABLE_NAME);
    }

    public void deleteContainerData(Container container) throws SQLException
    {
        // Remove rows from DilutionData and WellData
        SimpleFilter wellDataFilter = SimpleFilter.createContainerFilter(container);
        Table.delete(getSchema().getTable(WELL_DATA_TABLE_NAME), wellDataFilter);
        Table.delete(getSchema().getTable(DILUTION_DATA_TABLE_NAME), wellDataFilter);

        // Remove rows from NAbSpecimen and CutoffValue tables
        SimpleFilter runIdFilter = makeNabSpecimenContainerClause(container);

        // Delete all rows in CutoffValue table that match those nabSpecimenIds
        Filter specimenIdFilter = makeCuttoffValueSpecimenClause(runIdFilter);
        Table.delete(getSchema().getTable(CUTOFF_VALUE_TABLE_NAME), specimenIdFilter);

        // Delete the rows in NASpecimen hat match those runIdFilter
        TableInfo nabTableInfo = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        Table.delete(nabTableInfo, runIdFilter);

        PlateService.get().deleteAllPlateData(container);
    }

    public int insertNabSpecimenRow(User user, Map<String, Object> fields) throws SQLException
    {
        TableInfo tableInfo = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        Map<String, Object> newFields = Table.insert(user, tableInfo, fields);
        return (Integer)newFields.get("RowId");
    }

    public void insertCutoffValueRow(User user, Map<String, Object> fields) throws SQLException
    {
        TableInfo tableInfo = getSchema().getTable(CUTOFF_VALUE_TABLE_NAME);
        Map<String, Object> newFields = Table.insert(user, tableInfo, fields);
    }

    public static int insertDilutionDataRow(User user, Map<String, Object> fields) throws SQLException
    {
        TableInfo tableInfo = getSchema().getTable(DILUTION_DATA_TABLE_NAME);
        Map<String, Object> newFields = Table.insert(user, tableInfo, fields);
        return (Integer)newFields.get("RowId");
    }

    public static List<DilutionDataRow> getDilutionDataRows(int runId, int plateNumber, String name, Container container, boolean filterReplicate)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromString("runId"), runId);
        filter.addCondition(FieldKey.fromString("plateNumber"), plateNumber);
        if (filterReplicate)
            filter.addCondition(FieldKey.fromString("replicateName"), name);
        else
            filter.addCondition(FieldKey.fromString("wellgroupName"), name);
        return new TableSelector(getSchema().getTable(DILUTION_DATA_TABLE_NAME), filter, null).getArrayList(DilutionDataRow.class);
    }

    public static int insertWellDataRow(User user, Map<String, Object> fields) throws SQLException
    {
        TableInfo tableInfo = getSchema().getTable(WELL_DATA_TABLE_NAME);
        Map<String, Object> newFields = Table.insert(user, tableInfo, fields);
        return (Integer)newFields.get("RowId");
    }

    public static List<WellDataRow> getWellDataRows(ExpRun run)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(run.getContainer());
        filter.addCondition(FieldKey.fromString("runId"), run.getRowId());
        return new TableSelector(getSchema().getTable(WELL_DATA_TABLE_NAME), filter, null).getArrayList(WellDataRow.class);
    }

    public static List<WellDataRow> getExcludedWellDataRows(ExpRun run)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(run.getContainer());
        filter.addCondition(FieldKey.fromParts("runId"), run.getRowId());
        filter.addCondition(FieldKey.fromParts("excluded"), true);

        return new TableSelector(getSchema().getTable(WELL_DATA_TABLE_NAME), filter, null).getArrayList(WellDataRow.class);
    }

    /**
     * clear all well exclusions for this run
     */
    public static void clearWellExclusions(SqlDialect dialect, int runId)
    {
        SQLFragment sql = new SQLFragment("UPDATE ").append(DilutionManager.getTableInfoWellData(), "").
                append(" SET excluded = ").append(dialect.getBooleanFALSE()).append(" WHERE runid = ?");
        sql.addAll(runId);
        new SqlExecutor(getSchema()).execute(sql);
    }

    /**
     * Set the excluded flag for the collection of well row ids
     */
    public static int setWellExclusions(SqlDialect dialect, Collection<Integer> wellRowIds)
    {
        SQLFragment update = new SQLFragment("UPDATE ").append(DilutionManager.getTableInfoWellData(), "").
                append(" SET excluded = ").append(dialect.getBooleanTRUE()).append(" WHERE rowid ");
        getSchema().getSqlDialect().appendInClauseSql(update, wellRowIds);
        return new SqlExecutor(getSchema()).execute(update);
    }

    @Nullable
    public NabSpecimen getNabSpecimen(int rowId)
    {
        Filter filter = new SimpleFilter(FieldKey.fromString("RowId"), rowId);
        return getNabSpecimen(filter);
    }

    @Nullable
    public NabSpecimen getNabSpecimen(String dataRowLsid, Container container)
    {
        // dataRowLsid is the objectUri column
        SimpleFilter filter = makeNabSpecimenContainerClause(container);
        filter.addCondition(FieldKey.fromString("ObjectUri"), dataRowLsid);
        return getNabSpecimen(filter);
    }

    @Nullable
    private NabSpecimen getNabSpecimen(Filter filter)
    {
        TableInfo tableInfo = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        List<NabSpecimen> nabSpecimens = new TableSelector(tableInfo, filter, null).getArrayList(NabSpecimen.class);
        if (!nabSpecimens.isEmpty())
            return nabSpecimens.get(0);
        return null;
    }

    public List<NabSpecimen> getNabSpecimens(List<Integer> rowIds)
    {
        TableInfo tableInfo = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        Filter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromString("RowId"), rowIds));
        List<NabSpecimen> nabSpecimens = new TableSelector(tableInfo, filter, null).getArrayList(NabSpecimen.class);
        return nabSpecimens;
    }

    protected SimpleFilter makeNabSpecimenContainerClause(Container container)
    {
        String str = "RunId IN (SELECT RowId FROM " + ExperimentService.get().getTinfoExperimentRun().getSelectName() + " WHERE Container = '" + container.getEntityId() + "')";
        return new SimpleFilter(new SimpleFilter.SQLClause(str, new Object[]{}));
    }

    protected SimpleFilter makeCuttoffValueSpecimenClause(SimpleFilter nabSpecimenFilter)
    {
        TableInfo table = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        String str = "NAbSpecimenId IN (SELECT RowId FROM " + table.getSelectName() + " " +
                nabSpecimenFilter.getWhereSQL(table) + ")";

        List<Object> paramVals = nabSpecimenFilter.getWhereParams(table);
        Object[] params = new Object[paramVals.size()];
        for (int i = 0; i < paramVals.size(); i += 1)
            params[i] = paramVals.get(i);

        return new SimpleFilter(new SimpleFilter.SQLClause(str, params));
    }

    public static Set<Double> getCutoffValues(final ExpProtocol protocol)
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT Cutoff FROM ");
        sql.append(DilutionManager.getTableInfoCutoffValue(), "cv");
        sql.append(", ");
        sql.append(DilutionManager.getTableInfoNAbSpecimen(), "ns");
        sql.append(" WHERE ns.RowId = cv.NAbSpecimenID AND ns.ProtocolId = ?");
        sql.add(protocol.getRowId());

        return Collections.synchronizedSet(new HashSet<>(new SqlSelector(getSchema(), sql).getCollection(Double.class)));
    }

    /**
     * Clean up the records associated with the specified run data
     */
    public void deleteRunData(List<ExpData> datas) throws SQLException
    {
        DbScope scope = getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            // Get dataIds that match the ObjectUri and make filter on NabSpecimen
            List<Integer> dataIDs = new ArrayList<>(datas.size());
            Set<Integer> runIds = new HashSet<>();
            for (ExpData data : datas)
            {
                dataIDs.add(data.getRowId());
                if (null != data.getRunId())
                    runIds.add(data.getRunId());

            }
            if (!runIds.isEmpty())
            {
                SimpleFilter wellDataFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromString("RunId"), runIds));
                Table.delete(getSchema().getTable(WELL_DATA_TABLE_NAME), wellDataFilter);
                Table.delete(getSchema().getTable(DILUTION_DATA_TABLE_NAME), wellDataFilter);
            }

            SimpleFilter dataIdFilter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromString("DataId"), dataIDs));

            // Now delete all rows in CutoffValue table that match those nabSpecimenIds
            Filter specimenIdFilter = makeCuttoffValueSpecimenClause(dataIdFilter);
            Table.delete(getTableInfoCutoffValue(), specimenIdFilter);

            // Finally, delete the rows in NASpecimen
            Table.delete(getTableInfoNAbSpecimen(), dataIdFilter);

            transaction.commit();
        }
    }

    // Class for parsing a Data Property Descriptor name and categorizing it
    public static class PropDescCategory
    {
        private String _origName = null;
        private String _type = null;         // ic_4pl, ic_5pl, ic_poly, point, null
        private boolean _oor = false;
        private String _rangeOrNum = null;   // inrange, number, null
        private Integer _cutoffValue = null; // value, null

        public PropDescCategory(String name)
        {
            _origName = name;
        }

        public String getOrigName()
        {
            return _origName;
        }

        public void setOrigName(String origName)
        {
            _origName = origName;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public boolean isOor()
        {
            return _oor;
        }

        public void setOor(boolean oor)
        {
            _oor = oor;
        }

        public String getRangeOrNum()
        {
            return _rangeOrNum;
        }

        public void setRangeOrNum(String rangeOrNum)
        {
            _rangeOrNum = rangeOrNum;
        }

        public Integer getCutoffValue()
        {
            return _cutoffValue;
        }

        public void setCutoffValue(Integer cutoffValue)
        {
            _cutoffValue = cutoffValue;
        }

        public String getColumnName()
        {
            String colName = _type;
            if (_oor) colName += OOR_INDICATOR_SUFFIX;
            return colName;
        }

        public String getCalculatedColumnName()
        {
            String columnName = null;
            if (null == getRangeOrNum())
            {
                columnName = getColumnName();
            }
            else if (getRangeOrNum().equalsIgnoreCase("inrange"))
            {
                columnName = getColumnName() + "InRange";
            }
            else if (getRangeOrNum().equalsIgnoreCase("number"))
            {
                columnName = getColumnName() + "Number";
            }
            return columnName;
        }

        @Nullable
        public String getCutoffValueColumnName()
        {
            if (null != _cutoffValue)
                return "Cutoff" + _cutoffValue;
            return null;
        }

        public FieldKey getFieldKey()
        {
            String cutoffColumnName = getCutoffValueColumnName();
            String columnName = getCalculatedColumnName();
            if (null != cutoffColumnName)
                return FieldKey.fromParts(cutoffColumnName, columnName);
            return FieldKey.fromString(columnName);
        }
    }

    public static PropDescCategory getPropDescCategory(String name)
    {
        PropDescCategory pdCat = new PropDescCategory(name);
        String lowerCaseName = name.toLowerCase();
        if (lowerCaseName.contains("inrange"))
            pdCat.setRangeOrNum("inrange");
        else if (lowerCaseName.contains("number"))
            pdCat.setRangeOrNum("number");

        if (lowerCaseName.startsWith("point") && lowerCaseName.contains("ic"))
        {
            pdCat.setType("Point");
        }
        else if (lowerCaseName.startsWith("curve") && lowerCaseName.contains("ic"))
        {
            if (lowerCaseName.contains("4pl"))
                pdCat.setType("IC_4pl");
            else if (lowerCaseName.contains("5pl"))
                pdCat.setType("IC_5pl");
            else if (lowerCaseName.contains("poly"))
                pdCat.setType("IC_Poly");
            else
                pdCat.setType("IC");
        }
        else if (lowerCaseName.contains("auc"))
        {
            String typePrefix = lowerCaseName.contains("positive") ?  "PositiveAuc" : "Auc";

            if (lowerCaseName.contains("4pl"))
                pdCat.setType(typePrefix + DilutionDataHandler.PL4_SUFFIX);
            else if (lowerCaseName.contains("5pl"))
                pdCat.setType(typePrefix + DilutionDataHandler.PL5_SUFFIX);
            else if (lowerCaseName.contains("poly"))
                pdCat.setType(typePrefix + DilutionDataHandler.POLY_SUFFIX);
            else
                pdCat.setType(typePrefix);
        }
        else if (lowerCaseName.equals("specimen lsid"))
            pdCat.setType("SpecimenLsid");
        else if (lowerCaseName.equals("wellgroup name"))
            pdCat.setType("WellgroupName");
        else if (lowerCaseName.equals("fit error"))
            pdCat.setType("FitRrror");

        pdCat.setOor(lowerCaseName.contains(OOR_INDICATOR_SUFFIX.toLowerCase()));
        pdCat.setCutoffValue(cutoffValueFromName(name));
        return pdCat;
    }

    public static FieldKey getCalculatedColumn(DilutionManager.PropDescCategory pdCat)
    {
        FieldKey fieldKey = null;
        if (null != pdCat.getCutoffValue())
        {
            String cutoffColumnName = pdCat.getCutoffValueColumnName();
            String columnName = pdCat.getCalculatedColumnName();
            if (null != columnName)         // cutoffColumn could be null when we're deleting folders
            {
                fieldKey = FieldKey.fromParts(cutoffColumnName, columnName);
            }
        }

        return fieldKey;
    }

    @Nullable
    private static Integer cutoffValueFromName(String name)
    {
        int icIndex = name.toLowerCase().indexOf("ic");
        if (icIndex != -1)
        {
            StringBuilder sb = new StringBuilder();
            for (int i=(icIndex + 2); i < name.length(); i++)
            {
                char c = name.charAt(i);
                if (Character.isDigit(c))
                    sb.append(c);
                else
                    break;
            }

            if (NumberUtils.isDigits(sb.toString()))
                return NumberUtils.toInt(sb.toString());
        }
        return null;
    }

    public void getDataPropertiesFromRunData(TableInfo dataTable, String dataRowLsid, Container container,
                        List<PropertyDescriptor> propertyDescriptors, Map<PropertyDescriptor, Object> dataProperties)
    {
        // dataRowLsid is the objectUri column
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("ObjectUri"), dataRowLsid);
        Map<PropertyDescriptor, FieldKey> fieldKeys = new HashMap<>();
        for (PropertyDescriptor pd : propertyDescriptors)
        {
            PropDescCategory pdCat = getPropDescCategory(pd.getName());
            FieldKey fieldKey = pdCat.getFieldKey();
            if (null != fieldKey)
                fieldKeys.put(pd, fieldKey);
        }

        Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(dataTable, fieldKeys.values());

        // TODO: use getMap() instead of getResultSet()
        try (TableResultSet resultSet = new TableSelector(dataTable, columns.values(), filter, null).getResultSet())
        {
            // We're expecting only 1 row, but there could be 0 in some cases
            if (resultSet.getSize() > 0)
            {
                resultSet.next();
                Map<String, Object> rowMap = resultSet.getRowMap();
                for (PropertyDescriptor pd : propertyDescriptors)
                {
                    ColumnInfo column = columns.get(fieldKeys.get(pd));
                    if (null != column)
                    {
                        String columnAlias = column.getAlias();
                        if (null != columnAlias)
                            dataProperties.put(pd, rowMap.get(columnAlias));
                    }
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
