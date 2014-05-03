/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.survey.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UnionContainerFilter;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;
import org.labkey.survey.SurveyController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: klum
 * Date: 12/7/12
 */
public class SurveyDesignTable extends FilteredTable<SurveyQuerySchema>
{
    public SurveyDesignTable(TableInfo table, SurveyQuerySchema schema)
    {
        super(table, schema, new ContainerFilter.CurrentPlusProjectAndShared(schema.getUser()));

        wrapAllColumns(true);

        List<FieldKey> defaultColumns = new ArrayList<>(Arrays.asList(
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("CreatedBy"),
                FieldKey.fromParts("Created"),
                FieldKey.fromParts("ModifiedBy"),
                FieldKey.fromParts("Modified")
        ));
        setDefaultVisibleColumns(defaultColumns);

        ActionURL updateUrl = new ActionURL(SurveyController.SurveyDesignAction.class, schema.getContainer());
        setUpdateURL(new DetailsURL(updateUrl, Collections.singletonMap("rowId", FieldKey.fromString("RowId"))));
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        TableInfo table = getRealTable();
        if (table != null && table.getTableType() == DatabaseTableType.TABLE)
            return new DefaultQueryUpdateService(this, table);
        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm);
    }

    @Override
    public boolean hasDefaultContainerFilter()
    {
        return getContainerFilter() instanceof ContainerFilter.CurrentPlusProjectAndShared;
    }

    @Override
    public void setContainerFilter(@NotNull ContainerFilter filter)
    {
        if (hasDefaultContainerFilter())
        {
            filter = new UnionContainerFilter(filter, getContainerFilter());
        }
        super.setContainerFilter(filter);
    }
}
