/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.list;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.list.model.ListDef;
import org.labkey.list.model.ListManager;
import org.labkey.list.model.ListQuerySchema;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PicklistMaterialListener implements ExperimentListener
{
    @Override
    public void beforeMaterialDelete(List<? extends ExpMaterial> materials, Container container, User user)
    {
        Collection<ListDef> picklists = ListManager.get().getPicklists(container);
        List<Long> materialIds = materials.stream().map(ExpMaterial::getRowId).collect(Collectors.toList());
        picklists.forEach(picklist -> {
            ListQuerySchema listQuerySchema = new ListQuerySchema(user, container);
            TableInfo table = listQuerySchema.getTable(picklist.getName());

            if (table != null)
            {
                SimpleFilter filter = new SimpleFilter();
                filter.addInClause(FieldKey.fromParts("SampleID"), materialIds);

                Table.delete(((FilteredTable) table).getRealTable(), filter);
            }
        });
    }

}
