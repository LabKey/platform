/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;

/**
 * Tags {@link Attachment} objects based on what they're attached to. Does not indicate that they are a file of a
 * particular type/format.
 */
public interface AttachmentParentType
{
    SQLFragment NO_ROWS = new SQLFragment("SELECT NULL AS EntityId, NULL AS Description WHERE 1 = 0");
    SQLFragment PARENT_CONTAINER_SQL = new SQLFragment("SELECT EntityId, COALESCE(Name, '<Root>') AS Description FROM ")
        .append(CoreSchema.getInstance().getTableInfoContainers());

    AttachmentParentType UNKNOWN = new AttachmentParentType()
    {
        @NotNull
        @Override
        public String getUniqueName()
        {
            return "Unknown";
        }

        @Override
        public @NotNull SQLFragment getSelectEntityIdAndDescriptionSql()
        {
            return NO_ROWS;
        }
    };

    // A short, human-friendly, unique name for this attachment parent type
    @NotNull String getUniqueName();

    /**
     * Append to the where clause of a query that wants to select attachments of the implementing type from the
     * core.Documents table
     * @param sql Implementers MUST append a valid where clause to this SQLFragment
     * @param parentColumn Column identifier for use in where clause. Usually represents 'core.Documents.Parent'
     * @param documentNameColumn Column identifier for use in where clause. Usually represents 'core.Documents.DocumentName'
     */
    default void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
    {
        SQLFragment selectSql = getSelectParentEntityIdsSql();
        sql.append(parentColumn).append(" IN (").append(selectSql).append(")");
    }

    /**
     * Return a SQLFragment that selects just the EntityId of rows that might be attachment parents from the table(s)
     * that provide attachments of this type, without involving the core.Documents table.
     */
    default @NotNull SQLFragment getSelectParentEntityIdsSql()
    {
        SQLFragment selectSql = getSelectEntityIdAndDescriptionSql();

        // The returned SQL is always used inside a subselect, so the alias doesn't have to be unique
        return new SQLFragment("SELECT EntityId FROM (").append(selectSql).append(") x");
    }

    /**
     * Return a SQLFragment that selects the EntityId and an appropriate Description of all rows that might be
     * attachment parents from the table(s) that provide attachment parents of this type, without involving the
     * core.Documents table. For example, {@code SELECT EntityId, Title AS Description FROM comm.Announcements}.
     * If the method determines that no parents exist, then return a valid query that selects no rows, for example,
     * {@code NO_ROWS}.
     */
    @NotNull SQLFragment getSelectEntityIdAndDescriptionSql();
}
