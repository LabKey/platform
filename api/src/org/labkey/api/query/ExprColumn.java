/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

package org.labkey.api.query;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;

import java.util.Map;

public class ExprColumn extends ColumnInfo
{
    static public final String STR_TABLE_ALIAS = "'''~~TABLE~~'''";
    SQLFragment _sql;
    private ColumnInfo[] _dependentColumns;

    
    public ExprColumn(TableInfo parent, FieldKey key, SQLFragment sql, int sqltype, ColumnInfo ... dependentColumns)
    {
        super(key, parent);
        setAlias(AliasManager.makeLegalName(key.toString(), parent.getSqlDialect()));
        setSqlTypeName(getSqlDialect().sqlTypeNameFromSqlType(sqltype));
        _sql = sql;
        for (ColumnInfo dependentColumn : dependentColumns)
        {
            if (dependentColumn == null)
            {
                // Enable after https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=8439 is fixed
//                throw new NullPointerException("Dependent columns may not be null");
            }
        }
        _dependentColumns = dependentColumns;
    }

    
    public ExprColumn(TableInfo parent, String name, SQLFragment sql, int sqltype, ColumnInfo ... dependentColumns)
    {
        this(parent, FieldKey.fromString(name), sql, sqltype, dependentColumns);
//        assert -1 == name.indexOf('/');
    }


    public SQLFragment getValueSql(String tableAlias)
    {
        if (tableAlias.equals(STR_TABLE_ALIAS))
            return _sql;
        String sql = StringUtils.replace(_sql.getSQL(), STR_TABLE_ALIAS, tableAlias);
        SQLFragment ret = new SQLFragment(sql);
        ret.addAll(_sql.getParams());
        return ret;
    }


    public void setValueSQL(SQLFragment sql)
    {
        _sql = sql;
    }

    
    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        for (ColumnInfo col : _dependentColumns)
        {
            col.declareJoins(parentAlias, map);
        }
    }
}
