package org.labkey.api.compliance;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
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

    public @NotNull TableRules getTableRules(@NotNull Container c, @NotNull User user)
    {
        for (TableRulesProvider provider : TABLE_RULES_PROVIDERS)
        {
            TableRules rules = provider.get(c, user);

            if (null != rules)
            {
                return rules;
            }
        }

        return TableRules.NOOP_TABLE_RULES;
    }
}
