/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.query.sql;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.FieldKey;

public class QTable implements QJoinOrTable
{
    QExpr _table;
    QIdentifier _alias;
    QueryRelation _queryRelation;

    public QTable(QExpr table)
    {
        _table = table;
    }


    public QTable(QueryRelation t, String alias)
    {
        _queryRelation = t;
        _alias = new QIdentifier(alias);
        _table = _alias;
    }

    public void setAlias(QIdentifier alias)
    {
        _alias = alias;
    }

    public FieldKey getTableKey()
    {
        return _table.getFieldKey();
    }


    // should be a one part field key (but strings are case-sensitive)
    public FieldKey getAlias()
    {
        if (_alias == null)
        {
            FieldKey fk = getTableKey();
            if (null == fk)
                return null;
            return null == fk.getParent() ? fk : new FieldKey(null, fk.getName());
        }
        return new FieldKey(null, _alias.getIdentifier());
    }


    public void appendSource(SourceBuilder builder)
    {
        _table.appendSource(builder);
        if (_alias != null)
        {
            builder.append(" AS ");
            _alias.appendSource(builder);
        }
    }


    public void appendSql(SqlBuilder sql, QuerySelect select)
    {
        SQLFragment sqlRelation = getQueryRelation().getFromSql();
        assert sqlRelation != null || select.getParseErrors().size() > 0;
        if (null == sqlRelation)
            return;
        sql.append(sqlRelation);
    }

    
    public QExpr getTable()
    {
        return _table;
    }

    public QueryRelation getQueryRelation()
    {
        return _queryRelation;
    }

    public void setQueryRelation(QueryRelation queryRelation)
    {
        _queryRelation = queryRelation;
    }
}
