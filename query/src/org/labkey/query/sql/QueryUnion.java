package org.labkey.query.sql;

import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.AliasedColumn;

import java.util.List;
import java.util.ArrayList;

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

public class QueryUnion
{
    QuerySchema _schema;
    QUnion _union;
    List<QuerySelect> _selectList = new ArrayList<QuerySelect>();
    List<QueryTableInfo> _tinfos = new ArrayList<QueryTableInfo>();

    QueryUnion(Query query, QUnion union)
    {
        _schema = query.getSchema();
        _union = union;
        
        for (QNode n : union.children())
        {
            QQuery qquery = (QQuery)n;
            QuerySelect select = new QuerySelect(query, qquery);
            _selectList.add(select);
        }
    }

    void computeTableInfos()
    {
        if (_tinfos.size() == 0)
        {
            for (QuerySelect s : _selectList)
                _tinfos.add(s.getTableInfo("U"));
        }
    }


    public QueryTableInfo getTableInfo(String alias)
    {
        computeTableInfos();
        
        String union = "";
        SQLFragment unionSql = new SQLFragment();

        for (QuerySelect select : _selectList)
        {
            SQLFragment sql = select.getSelectSql();
            unionSql.append(union);
            unionSql.append(sql);
            union = _union.getTokenType() == SqlTokenTypes.UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n";
        }

        SQLTableInfo sti = new SQLTableInfo(_schema.getDbSchema());
        sti.setName("UNION");
        sti.setAlias(alias);
        sti.setFromSQL(unionSql);
        QueryTableInfo ret = new QueryTableInfo(sti, "UNION", alias);
        for (ColumnInfo col : _tinfos.get(0).getColumns())
        {
            ColumnInfo ucol = new AliasedColumn(ret, col.getName(), col);
            ret.addColumn(ucol);
        }
        return ret;
    }


    public String getQueryText()
    {
        String union = "";
        StringBuilder sb = new StringBuilder();
        
        for (QuerySelect select : _selectList)
        {
            String sql = select.getQueryText();
            sb.append(union);
            sb.append(sql);
            union = _union.getTokenType() == SqlTokenTypes.UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n";
        }
        return sb.toString();
    }
}
