/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.compliance;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TableRulesManager
{
    private static final TableRulesManager INSTANCE = new TableRulesManager();
    private static final List<TableRulesProvider> TABLE_RULES_PROVIDERS = new CopyOnWriteArrayList<>();

    public static TableRulesManager get()
    {
        return INSTANCE;
    }

    private TableRulesManager()
    {
    }

    public void addTableRulesProvider(TableRulesProvider provider)
    {
        // Always add to the beginning... we want to iterate in reverse dependency order
        TABLE_RULES_PROVIDERS.add(0, provider);
    }

    public @NotNull TableRules getTableRules(@NotNull Container c, @NotNull User user, SecurableResource permissionsResource)
    {
        for (TableRulesProvider provider : TABLE_RULES_PROVIDERS)
        {
            TableRules rules = provider.get(c, user, permissionsResource);

            if (null != rules)
            {
                return rules;
            }
        }

        return TableRules.NOOP_TABLE_RULES;
    }
}
