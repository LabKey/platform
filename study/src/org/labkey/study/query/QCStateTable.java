/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.qc.QCStateHandler;
import org.labkey.api.qc.QCStateManager;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.util.Map;

/**
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class QCStateTable extends FilteredTable<StudyQuerySchema>
{
    public QCStateTable(StudyQuerySchema schema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoQCState(), schema, cf);
        wrapAllColumns(true);
    }

    @Override
    public @Nullable QueryUpdateService getUpdateService()
    {
        return new QCStateService(this);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {



        return true;
//        return getContainer().hasPermission(user, perm);
    }

    private class QCStateService extends DefaultQueryUpdateService
    {
        public QCStateService(FilteredTable table) { super(table, table.getRealTable()); }
    }
}