/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.experiment.list;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Set;

public class ListSchema extends UserSchema
{
    static public final String NAME = "lists";
    static public void register()
    {
        DefaultSchema.registerProvider(NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                if (ListService.get().getLists(schema.getContainer()).isEmpty())
                    return null;
                return new ListSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public ListSchema(User user, Container container)
    {
        super(NAME, user, container, ExperimentService.get().getSchema());
    }

    public Set<String> getTableNames()
    {
        return ListService.get().getLists(getContainer()).keySet();
    }

    public TableInfo getTable(String name, String alias)
    {
        ListDefinition def = ListService.get().getLists(getContainer()).get(name);
        if (def != null)
            return def.getTable(getUser(), alias);
        return super.getTable(name, alias);
    }
}
