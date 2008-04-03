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
        setAlias(name);
        setSqlTypeName("VARCHAR");
        setIsUnselectable(true);
        _participantColumn = participantColumn;
        _visitColumn = visitColumn;
    }

    public SQLFragment getValueSql(String alias)
    {
        return getValueSql(_participantColumn.getValueSql(alias), _visitColumn.getValueSql(alias));
    }

    public SQLFragment getValueSql()
    {
        return getValueSql(_participantColumn.getValueSql(), _visitColumn.getValueSql());
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
