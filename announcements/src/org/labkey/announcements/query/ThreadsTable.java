/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.announcements.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.query.FilteredTable;

import static org.labkey.announcements.query.AnnouncementSchema.THREADS_TABLE_NAME;

public class ThreadsTable extends FilteredTable<AnnouncementSchema>
{
    public ThreadsTable(@NotNull AnnouncementSchema userSchema, ContainerFilter cf)
    {
        super(CommSchema.getInstance().getTableInfoThreads(), userSchema, cf);

        wrapAllColumns(true);
        removeColumn(getColumn("Body"));
        removeColumn(getColumn("RendererType"));
        removeColumn(getColumn("DiscussionSrcIdentifier"));
        removeColumn(getColumn("DiscussionSrcUrl"));
        removeColumn(getColumn("Container"));
        var folderColumn = wrapColumn("Folder", getRealTable().getColumn("Container"));
        folderColumn.setFk(new ContainerForeignKey(userSchema));
        addColumn(folderColumn);

        setDescription("Contains one row per thread");
        setName(THREADS_TABLE_NAME);
        setPublicSchemaName(AnnouncementSchema.SCHEMA_NAME);
    }
}
