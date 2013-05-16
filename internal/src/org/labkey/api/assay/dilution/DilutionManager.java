/*
 * Copyright (c) 2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.nab.NabSpecimen;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
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
    public static final String NAB_DBSCHEMA_NAME = "nab";

    public static DbSchema getSchema()
    {
        return DbSchema.get(NAB_DBSCHEMA_NAME);
    }

    public void deleteContainerData(Container container) throws SQLException
    {
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
        List<NabSpecimen> nabSpecimens = new TableSelector(tableInfo, Table.ALL_COLUMNS, filter, null).getArrayList(NabSpecimen.class);
        if (!nabSpecimens.isEmpty())
            return nabSpecimens.get(0);
        return null;
    }

    public List<NabSpecimen> getNabSpecimens(List<Integer> rowIds)
    {
        TableInfo tableInfo = getSchema().getTable(NAB_SPECIMEN_TABLE_NAME);
        Filter filter = new SimpleFilter(new SimpleFilter.InClause(FieldKey.fromString("RowId"), rowIds));
        List<NabSpecimen> nabSpecimens = new TableSelector(tableInfo, Table.ALL_COLUMNS, filter, null).getArrayList(NabSpecimen.class);
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
                nabSpecimenFilter.getWhereSQL(getSchema().getSqlDialect()) + ")";

        List<Object> paramVals = nabSpecimenFilter.getWhereParams(table);
        Object[] params = new Object[paramVals.size()];
        for (int i = 0; i < paramVals.size(); i += 1)
            params[i] = paramVals.get(i);

        return new SimpleFilter(new SimpleFilter.SQLClause(str, params));
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
            if (_oor) colName += "OORIndicator";
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
        if (name.contains("InRange"))
            pdCat.setRangeOrNum("inrange");
        else if (name.contains("Number"))
            pdCat.setRangeOrNum("number");

        if (name.startsWith("Point") && name.contains("IC"))
        {
            pdCat.setType("Point");
        }
        else if (name.startsWith("Curve") && name.contains("IC"))
        {
            if (name.contains("4pl"))
                pdCat.setType("IC_4pl");
            else if (name.contains("5pl"))
                pdCat.setType("IC_5pl");
            else if (name.contains("poly"))
                pdCat.setType("IC_Poly");
            else
                pdCat.setType("IC");
        }
        else if (name.contains("AUC"))
        {
            String typePrefix = name.toLowerCase().contains("positive") ?  "PositiveAuc" : "Auc";

            if (name.contains("4pl"))
                pdCat.setType(typePrefix + DilutionDataHandler.PL4_SUFFIX);
            else if (name.contains("5pl"))
                pdCat.setType(typePrefix + DilutionDataHandler.PL5_SUFFIX);
            else if (name.contains("poly"))
                pdCat.setType(typePrefix + DilutionDataHandler.POLY_SUFFIX);
            else
                pdCat.setType(typePrefix);
        }
        else if (name.equalsIgnoreCase("specimen lsid"))
            pdCat.setType("SpecimenLsid");
        else if (name.equalsIgnoreCase("wellgroup name"))
            pdCat.setType("WellgroupName");
        else if (name.equalsIgnoreCase("fit error"))
            pdCat.setType("FitRrror");

        pdCat.setOor(name.contains("OORIndicator"));
        pdCat.setCutoffValue(cutoffValueFromName(name));
        return pdCat;
    }

    @Nullable
    private static Integer cutoffValueFromName(String name)
    {
        int icIndex = name.indexOf("IC");
        if (icIndex >= 0 && icIndex + 4 <= name.length())
            return Integer.valueOf(name.substring(icIndex + 2, icIndex + 4));
        return null;
    }
}
