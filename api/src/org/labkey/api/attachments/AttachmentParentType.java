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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;

/**
 * Tags {@link Attachment} objects based on what they're attached to. Does not indicate that they are a file of a
 * particular type/format.
 */
public interface AttachmentParentType
{
    SQLFragment NO_ENTITY_IDS = new SQLFragment("SELECT NULL AS EntityId WHERE 1 = 0");

    AttachmentParentType UNKNOWN = new AttachmentParentType()
    {
        @NotNull
        @Override
        public String getUniqueName()
        {
            return "Unknown";
        }

        @Override
        public void addWhereSql(SQLFragment sql, String parentColumn, String documentNameColumn)
        {
            sql.append("0 = 1");
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
        if (selectSql == null)
            throw new IllegalStateException("Must override either addWhereSql() or getSelectParentEntityIdsSql()");
        sql.append(parentColumn).append(" IN (").append(selectSql).append(")");
    }

    /**
     * Return a SQLFragment that selects all the EntityIds that might be attachment parents from the table(s) that
     * provide attachments of this type, without involving the core.Documents table. For example,
     * {@code SELECT EntityId FROM comm.Announcements}. Return null if this is not-yet-implemented or inappropriate.
     * For example, some attachments' parents are container IDs. If the method determines that no parents exist, then
     * return a valid query that selects no rows, for example, {@code NO_ENTITY_IDS}.
     */
    default @Nullable SQLFragment getSelectParentEntityIdsSql()
    {
        return null;
    }
}
