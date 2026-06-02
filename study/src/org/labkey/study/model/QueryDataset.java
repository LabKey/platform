/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.study.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.MetadataParseException;
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

    public QueryDataset(@NotNull String name, @NotNull DatasetDefinition def)
    {
        super(StudySchema.getInstance().getSchema(), name, null);
        UserSchema us = QueryService.get().getUserSchema(new LimitedUser(User.guest, ProjectAdminRole.class), def.getSourceQueryContainer(), def.getSourceQuerySchema());
        if (us == null)
        {
            throw new MetadataParseException("QueryDataset requires a valid schema");
        }

        TableInfo ti = us.getTable(def.getSourceQueryName());
        if (ti == null)
        {
            throw new MetadataParseException("QueryDataset requires a valid query");
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

    @Override
    public @Nullable DatabaseIdentifier getMetaDataIdentifier()
    {
        return _inner.getMetaDataIdentifier();
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
            if (getColumn(DatasetDomainKind._KEY) == null)
            {
                throw new MetadataParseException("A query dataset requires a source query with a unique _Key column");
            }

            // ParticipantId
            if (!ensureParticipantIdCol(ti))
            {
                throw new MetadataParseException("A query dataset requires a source query with a ParticipantId, SubjectId, or Id column");
            }

            // Date
            if (getColumn(DatasetDomainKind.DATE) == null)
            {
                throw new MetadataParseException("A query dataset requires a source query with a Date column");
            }

            // QCState
            // could consider looking up the "Completed" state and filling that in automatically if found
            if (getColumn(DatasetDomainKind.QCSTATE) == null)
            {
                throw new MetadataParseException("A query dataset requires a source query with a QCState column");
            }

            // LSID
            if (getColumn(DatasetDomainKind.LSID) == null)
            {
                throw new MetadataParseException("A query dataset requires a source query with a LSID column");
            }

            // SequenceNum
            if (getColumn(DatasetDomainKind.SEQUENCENUM) == null)
            {
                ExprColumn sequenceNumCol = new ExprColumn(this, DatasetDomainKind.SEQUENCENUM, new SQLFragment(StudyUtils.sequenceNumFromDateSQL(DatasetDomainKind.DATE)), JdbcType.DECIMAL);
                addColumn(sequenceNumCol);
            }

            // SourceLSID
            if (getColumn(DatasetDomainKind.SOURCELSID) == null)
            {
                addColumn(new ExprColumn(this, DatasetDomainKind.SOURCELSID, new SQLFragment("NULL"), JdbcType.VARCHAR));
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
            if (getColumn(DatasetDomainKind.PARTICIPANTID) != null)
            {
                return true;
            }

            if (ti.getColumn("SubjectId") != null)
            {
                ColumnInfo subjectIdCol = ti.getColumn("SubjectId");
                addColumn(new ExprColumn(this, DatasetDomainKind.PARTICIPANTID, subjectIdCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), subjectIdCol.getJdbcType()));
                return true;
            }
            else if (ti.getColumn("Id") != null)
            {
                ColumnInfo idCol = ti.getColumn("Id");
                addColumn(new ExprColumn(this, DatasetDomainKind.PARTICIPANTID, idCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), idCol.getJdbcType()));
                return true;
            }

            return false;
        }

        @Override
        public @NotNull SQLFragment getFromSQL()
        {
            SQLFragment sql;
            sql = new SQLFragment("(SELECT ").append(queryAlias).append(".* FROM ").append(_sql).append(")");
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
