/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.snapshot.AbstractTableMethodInfo;

/**
 * User: kevink
 * Date: 2/23/16
 *
 * Adds a table method to exp.Data or exp.Material tables.  For example:
 *   exp.Data.Inputs(type, depth)
 *   exp.Data.Outputs(type, depth)
 *
 * Where:
 * - (optional) type is either one of 'ExperimentRun', 'Data', or 'Material' or is the cpastype lsid of the SampleType or DataClass.
 *   TODO: also support DataClass and SampleType names
 *   CONSIDER: we could use use run.protocollsid as its cpastype.
 * - (optional) depth is an integer >= 0.
 */
/*package*/ class LineageMethod extends AbstractTableMethodInfo
{
    private ColumnInfo _lsidColumn;
    private boolean _parents;

    LineageMethod(ColumnInfo lsidColumn, boolean parents)
    {
        super(JdbcType.VARCHAR);

        _lsidColumn = lsidColumn;
        _parents = parents;
    }

    @Override
    public MutableColumnInfo createColumnInfo(TableInfo parentTable, ColumnInfo[] arguments, String alias)
    {
        var col = super.createColumnInfo(parentTable, arguments, alias);
        ForeignKey fk = new LookupForeignKey("self_lsid", "Name")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return createLineageJunctionTable(arguments);
            }
        };
        String junctionLookup = "lsid";
        col.setFk(new MultiValuedForeignKey(fk, junctionLookup));
        return col;
    }

    protected TableInfo createLineageJunctionTable(ColumnInfo[] arguments)
    {
        if (arguments.length > 2)
            throw new QueryException("Lineage method supports 0, 1, or 2 arguments");

        TableInfo parentTable = _lsidColumn.getParentTable();
        UserSchema schema = parentTable.getUserSchema();

        SQLFragment objectids = new SQLFragment();
        objectids.append("(SELECT qq.objectid FROM ");
        objectids.append(parentTable.getFromSQL("qq"));
        objectids.append(")");

        SQLFragment[] fragments = getSQLFragments(arguments);
        String expType = null;
        String cpasType = null;
        if (fragments.length > 0 && isSimpleString(fragments[0]))
        {
            String type = toSimpleString(fragments[0]);
            if (type.equals("Data") || type.equals("Material") || type.equals("ExperimentRun"))
                expType = type;
            else
                cpasType = type;
        }

        Integer depth = null;
        if (fragments.length > 1)
        {
            String s = fragments[1].getSQL();
            try
            {
                depth = Integer.parseInt(s);
            }
            catch (NumberFormatException ex) { /* ok */ }
        }

        return new LineageTableInfo("LineageTableInfo (" + (_parents?"parents)":"children)"), schema, objectids, _parents, depth, expType, cpasType, null);
    }

    @Override
    public SQLFragment getSQL(String tableAlias, DbSchema schema, SQLFragment[] arguments)
    {
        String alias = _lsidColumn.getAlias();
        return new SQLFragment(tableAlias + "." + alias);
    }

    public static boolean isSimpleString(SQLFragment f)
    {
        if (f.getParams().size() > 0)
            return false;
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return false;
        return s.length()-1 == s.indexOf('\'',1);
    }


    public static String toSimpleString(SQLFragment f)
    {
        assert isSimpleString(f);
        String s = f.getSQL();
        if (s.length() < 2 || !s.startsWith("'"))
            return s;
        s = s.substring(1,s.length()-1);
        s = StringUtils.replace(s,"''","'");
        return s;
    }

}
