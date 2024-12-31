package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.study.StudyUtils;
import org.labkey.study.StudySchema;

import java.util.List;

public class QueryDataset extends VirtualTable<UserSchema>
{
    private final QuerySchemaTableInfo _inner;

    public QueryDataset(@NotNull String name, @NotNull Container sourceContainer, @NotNull String sourceSchema, @NotNull String sourceQuery)
    {
        super(StudySchema.getInstance().getSchema(), name, null);
        UserSchema us = QueryService.get().getUserSchema(new LimitedUser(User.guest, ProjectAdminRole.class), sourceContainer, sourceSchema);
        if (us == null)
        {
            throw new IllegalArgumentException("QueryDataset requires a valid schema");
        }

        TableInfo ti = us.getTable(sourceQuery);
        if (ti == null)
        {
            throw new IllegalArgumentException("QueryDataset requires a valid query");
        }

        _inner = new QuerySchemaTableInfo(ti);
    }


    @Override
    public ColumnInfo getColumn(@NotNull String name, boolean resolveIfNeeded)
    {
        return _inner.getColumn(name);
    }

    @Override
    public DatabaseTableType getTableType()
    {
        return _inner.getTableType();
    }

    @Override
    public String getSelectName()
    {
        return _inner.getSelectName();
    }

    @Override
    public @Nullable SQLFragment getSQLName()
    {
        return _inner.getSQLName();
    }

    @Nullable
    @Override
    public String getMetaDataName()
    {
        return _inner.getMetaDataName();
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        return _inner.getFromSQL(alias);
    }


    private static class QuerySchemaTableInfo extends SchemaTableInfo
    {
        private static final String queryAlias = "QD";
        SQLFragment _sql;

        public QuerySchemaTableInfo(TableInfo ti)
        {
            super(ti.getSchema(), DatabaseTableType.NOT_IN_DB, ti.getName());
            _sql = ti.getFromSQL(queryAlias);

            wrapAllColumns(ti.getColumns());

            // Key
            if (getColumn("Key") != null)
            {
                addColumn(new ExprColumn(this, "_Key", getColumn("Key").getValueSql(ExprColumn.STR_TABLE_ALIAS), JdbcType.VARCHAR));
            }
            else
            {
                throw new IllegalArgumentException("QueryDataset requires a query with a unique Key column");
            }

            // ParticipantId
            if (!ensureParticipantIdCol(ti))
            {
                throw new IllegalArgumentException("QueryDataset requires a query with a ParticipantId, SubjectId, or Id column");
            }

            // Date
            if (getColumn("Date") == null)
            {
                throw new IllegalArgumentException("QueryDataset requires a query with a Date column");
            }

            // QCState
            // could consider looking up the "Completed" state and filling that in automatically if found
            if (getColumn("QCState") == null)
            {
                throw new IllegalArgumentException("QueryDataset requires a query with a QCState column");
            }

            // LSID
            if (getColumn("lsid") == null)
            {
                throw new IllegalArgumentException("QueryDataset requires a query with a LSID column");
            }

            // SequenceNum
            if (getColumn("SequenceNum") == null)
            {
                ExprColumn sequenceNumCol = new ExprColumn(this, "SequenceNum", new SQLFragment(StudyUtils.sequenceNumFromDateSQL("Date")), JdbcType.DECIMAL);
                addColumn(sequenceNumCol);
            }

            // SourceLSID
            if (getColumn("SourceLSID") == null)
            {
                addColumn(new ExprColumn(this, "SourceLSID", new SQLFragment("NULL"), JdbcType.VARCHAR));
            }

            // SourceLSID
            if (getColumn("SourceLSID") == null)
            {
                addColumn(new ExprColumn(this, "SourceLSID", new SQLFragment("NULL"), JdbcType.VARCHAR));
            }
        }

        public void wrapAllColumns(List<ColumnInfo> columns)
        {
            for (ColumnInfo col : columns)
            {
                ExprColumn to = new ExprColumn(this, col.getName(), col.getValueSql(ExprColumn.STR_TABLE_ALIAS), col.getJdbcType());
                addColumn(to);
            }
        }

        private boolean ensureParticipantIdCol(TableInfo ti)
        {
            if (getColumn("ParticipantId") != null)
            {
                return true;
            }

            if (ti.getColumn("SubjectId") != null)
            {
                ColumnInfo subjectIdCol = ti.getColumn("SubjectId");
                addColumn(new ExprColumn(this, "ParticipantId", subjectIdCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), subjectIdCol.getJdbcType()));
                return true;
            }
            else if (ti.getColumn("Id") != null)
            {
                ColumnInfo idCol = ti.getColumn("Id");
                addColumn(new ExprColumn(this, "ParticipantId", idCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), idCol.getJdbcType()));
                return true;
            }

            return false;
        }

        @Override
        public @NotNull SQLFragment getFromSQL()
        {
            SQLFragment sql = new SQLFragment("(SELECT ").append(queryAlias).append(".* FROM ").append(_sql).append(")");
            return sql;
        }

        @Override
        public String getSelectName()
        {
            return null;
        }

        @Override
        public @Nullable SQLFragment getSQLName()
        {
            return null;
        }
    }
}
