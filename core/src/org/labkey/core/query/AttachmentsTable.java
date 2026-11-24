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
package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdQueryForeignKey;

import java.util.Objects;

/** One row per attachment pointer, with the size and type of the content it references */
public class AttachmentsTable extends FilteredTable<CoreQuerySchema>
{
    public AttachmentsTable(@NotNull CoreQuerySchema userSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoAttachmentPointers(), userSchema, cf);
        wrapAllColumns(true);
        getMutableColumnOrThrow("RowId").setHidden(true);
        getMutableColumnOrThrow("AttachmentId").setHidden(true);
        getMutableColumnOrThrow("CreatedBy").setFk(new UserIdQueryForeignKey(_userSchema));
        getMutableColumnOrThrow("Parent").setHidden(true);
        getMutableColumnOrThrow("LastIndexed").setHidden(true);
        BaseColumnInfo size = new BaseColumnInfo("Size", this, JdbcType.INTEGER);
        size.setFormat("#,##0");
        addColumn(size);
        addColumn(new BaseColumnInfo("Type", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("ParentDescription", this, JdbcType.VARCHAR));
        addColumn(new BaseColumnInfo("Orphaned", this, JdbcType.BOOLEAN));

        setDefaultVisibleColumns(
            new CsvSet("CreatedBy, Created, Container, Name, Size, Type, ParentType, ParentDescription")
                .stream()
                .map(FieldKey::fromParts)
                .toList()
        );
    }

    @Override
    public @NotNull SQLFragment getFromSQL(String alias)
    {
        SqlDialect dialect = getSqlDialect();
        AliasManager am = new AliasManager(dialect);
        SQLFragment parents = AttachmentService.get().getAttachmentParentTypes().stream()
            .map(type -> SQLFragment.unsafe("SELECT ? AS ParentType, EntityId, Description FROM (")
                .add(type.getUniqueName())
                .append(type.getSelectEntityIdAndDescriptionSql())
                .unsafeAppend(") ")
                .appendIdentifier(am.decideAlias("x")))
            .filter(Objects::nonNull)
            .collect(LabKeyCollectors.joining(SQLFragment.unsafe("\nUNION\n")));

        return SQLFragment.unsafe("(SELECT ap.*, a.Size, a.Type, p.Description AS ParentDescription, ")
            .append(dialect.wrapBooleanExpression(SQLFragment.unsafe("EntityId IS NULL")))
            .unsafeAppend(" AS Orphaned FROM ")
            .append(super.getFromSQL("ap")) // core.AttachmentPointers with container filter applied
            .unsafeAppend(" INNER JOIN ")
            .append(CoreSchema.getInstance().getTableInfoAttachments(), "a")
            .unsafeAppend(" ON a.RowId = ap.AttachmentId LEFT JOIN (\n")
            .append(parents)
            .unsafeAppend("\n) p ON ap.Parent = p.EntityId AND ap.ParentType = p.ParentType) ")
            .appendIdentifier(alias);
    }
}
