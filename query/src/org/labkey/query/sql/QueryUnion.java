package org.labkey.query.sql;

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
    Query _query;
    List<QuerySelect> _selectList = new ArrayList<QuerySelect>();
    List<QUnion> _unionList = new ArrayList<QUnion>();  // parent of corresponding select
    List<QueryTableInfo> _tinfos = new ArrayList<QueryTableInfo>();

    QueryUnion(Query query, QUnion union)
    {
        _query = query;
        _schema = query.getSchema();

        initSelectStatements(union);
    }

    // current grammar does not support real tree of union using()
    void initSelectStatements(QUnion union)
    {
        for (QNode n : union.children())
        {
            assert n instanceof QQuery || n instanceof QUnion;
            if (n instanceof QQuery)
            {
                QuerySelect select = new QuerySelect(_query, (QQuery)n);
                _selectList.add(select);
                _unionList.add(union);
            }
            else
                initSelectStatements((QUnion)n);
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
        if (_query.getParseErrors().size() > 0)
            return null;
        
        String union = "";
        SQLFragment unionSql = new SQLFragment();

        for (int i=0 ; i<_selectList.size() ; i++)
        {
            QuerySelect select = _selectList.get(i);
            SQLFragment sql = select.getSelectSql();
            if (i>0)
                unionSql.append(_unionList.get(i).getTokenType() == SqlTokenTypes.UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n");
            unionSql.append(sql);
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
        
        for (int i=0 ; i<_selectList.size() ; i++)
        {
            QuerySelect select = _selectList.get(i);
            String sql = select.getQueryText();
            if (i>0)
                sb.append(_unionList.get(i).getTokenType() == SqlTokenTypes.UNION_ALL ? "\nUNION ALL\n" : "\nUNION\n");
            sb.append(union);
            sb.append(sql);
        }
        return sb.toString();
    }
}
