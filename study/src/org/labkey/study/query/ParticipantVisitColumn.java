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

package org.labkey.study.query;

import org.labkey.api.data.*;

/**
 * Column which has the data for both the participant and the visit in a single value.
 */
public class ParticipantVisitColumn extends ColumnInfo
{
    static private final int LEN_VISITTEXT = 20;
    ColumnInfo _participantColumn;
    ColumnInfo _visitColumn;

    public ParticipantVisitColumn(String name, ColumnInfo participantColumn, ColumnInfo visitColumn)
    {
        this(name, participantColumn.getParentTable(), participantColumn, visitColumn);
    }

    public ParticipantVisitColumn(String name, TableInfo parentTable, ColumnInfo participantColumn, ColumnInfo visitColumn)
    {
        super(name, parentTable);
        setSqlTypeName("VARCHAR");
        setIsUnselectable(true);
        _participantColumn = participantColumn;
        _visitColumn = visitColumn;
    }

    public SQLFragment getValueSql(String alias)
    {
        return getValueSql(_participantColumn.getValueSql(alias), _visitColumn.getValueSql(alias));
    }

    public SQLFragment getValueSql(SQLFragment participant, SQLFragment visit)
    {
        SqlDialect dialect = getSqlDialect();
        SQLFragment ret = new SQLFragment("((");
        ret.append(participant);
        ret.append(")");
        ret.append(dialect.getConcatenationOperator());
        ret.append("'|'");
        ret.append(dialect.getConcatenationOperator());
        ret.append("CAST(");
        ret.append(visit);
        ret.append(" AS VARCHAR))");
        return ret;
    }
}
