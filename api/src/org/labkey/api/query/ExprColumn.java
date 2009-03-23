/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

    public ExprColumn(TableInfo parent, String name, SQLFragment sql, int sqltype, ColumnInfo ... dependentColumns)
    {
        super(name, parent);
        setAlias(AliasManager.makeLegalName(name, parent.getSqlDialect()));
        setSqlTypeName(getSqlDialect().sqlTypeNameFromSqlType(sqltype));
        _sql = sql;
        _dependentColumns = dependentColumns;
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
