package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.FieldKey;
import org.labkey.study.model.ParticipantGroup;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by matthew on 9/28/15.
 */
public class ParticipantGroupFilterClause extends SimpleFilter.FilterClause
{
    final FieldKey _ptid;
    final ParticipantGroup _group;
    final SimpleFilter.FilterClause _clause;

    public ParticipantGroupFilterClause(@NotNull FieldKey ptid, @NotNull ParticipantGroup group)
    {
        _ptid = ptid;
        _group = group;

        SimpleFilter.FilterClause clause;
        if (group.isSession() || group.isNew())
        {
            clause = new SimpleFilter.InClause(_ptid, group.getParticipantSet());
        }
        else
        {
            clause = new SimpleFilter.SQLClause(
                    "ParticipantId IN (SELECT ParticipantId FROM study.ParticipantGroupMap WHERE GroupId=?)",
                    new Object[] {group.getRowId()},
                    _ptid);
        }
        _clause = clause;
    }

    @Override
    public List<String> getColumnNames()
    {
        return Collections.singletonList(_ptid.getName());
    }

    @Override
    public List<FieldKey> getFieldKeys()
    {
        return Collections.singletonList(_ptid);
    }

    @Override
    public String getLabKeySQLWhereClause(Map<FieldKey, ? extends ColumnInfo> columnMap)
    {
        return _clause.getLabKeySQLWhereClause(columnMap);
    }

    @Override
    public SQLFragment toSQLFragment(Map<FieldKey, ? extends ColumnInfo> columnMap, SqlDialect dialect)
    {
        return _clause.toSQLFragment(columnMap, dialect);
    }
}

