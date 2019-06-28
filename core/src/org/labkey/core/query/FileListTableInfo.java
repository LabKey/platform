/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.files.FileContentService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserSchema;

/**
 * User: kevink
 * Date: 2/24/13
 *
 */
class FileListTableInfo extends FilteredTable<CoreQuerySchema>
{
    public FileListTableInfo(@NotNull CoreQuerySchema schema)
    {
        super(createVirtualTable(schema), schema, schema.getContainer().isRoot() ? new ContainerFilter.AllFolders(schema.getUser()) : null);
        wrapAllColumns(true);
    }

    private static TableInfo createVirtualTable(@NotNull CoreQuerySchema schema)
    {
        return new FileUnionTable(schema);
    }

    private static class FileUnionTable extends VirtualTable
    {
        private SQLFragment _query;

        public FileUnionTable(@NotNull UserSchema schema)
        {
            super(CoreSchema.getInstance().getSchema(), CoreQuerySchema.FILES_TABLE_NAME, schema);

            FileContentService svc = FileContentService.get();
            _query = new SQLFragment();
            _query.appendComment("<FileListTableInfo>", getSchema().getSqlDialect());
            _query.append(svc.listFilesQuery(schema.getUser()));
            _query.appendComment("</FileListTableInfo>", getSchema().getSqlDialect());

            var containerCol = new BaseColumnInfo("Container", this, JdbcType.VARCHAR);
            ContainerForeignKey.initColumn(containerCol, schema);
            addColumn(containerCol);

            var createdCol = new BaseColumnInfo("Created", this, JdbcType.DATE);
            addColumn(createdCol);

            var createdByCol = new BaseColumnInfo("CreatedBy", this, JdbcType.INTEGER);
            UserIdForeignKey.initColumn(createdByCol);
            addColumn(createdByCol);

            var modifiedCol = new BaseColumnInfo("Modified", this, JdbcType.DATE);
            modifiedCol.setHidden(true);
            addColumn(modifiedCol);

            var modifiedByCol = new BaseColumnInfo("ModifiedBy", this, JdbcType.INTEGER);
            UserIdForeignKey.initColumn(modifiedByCol);
            modifiedByCol.setHidden(true);
            addColumn(modifiedByCol);

            var filePathCol = new BaseColumnInfo("FilePath", this, JdbcType.VARCHAR);
            addColumn(filePathCol);

            var sourceKeyCol = new BaseColumnInfo("SourceKey", this, JdbcType.INTEGER);
            sourceKeyCol.setHidden(true);
            addColumn(sourceKeyCol);

            var sourceNameCol = new BaseColumnInfo("SourceName", this, JdbcType.VARCHAR);
            sourceNameCol.setHidden(true);
            addColumn(sourceNameCol);
        }

        @Override
        public String getSelectName()
        {
            return null;
        }

        @NotNull
        @Override
        public SQLFragment getFromSQL()
        {
            return _query;
        }

    }
}


