package org.labkey.api.data.bigiron;

import org.labkey.api.data.DbScope;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 1/16/2017
 *
 * Central registration of individual assembly installation managers. Mainly so BaseMicrosoftSQLServerDialect doesn't need to know
 * about all of them in order to gather any appropriate admin warning messages. It would be nice to also centralize the calls to
 * ensureInstalled(), but a) GroupConcat is a special case that has to be checked as part of the core upgrade (as other upgrades
 * depend on it), and b) there's only one other assembly at the moment.
 */
public class ClrAssemblyManager
{
    private static final Set<AbstractClrInstallationManager> _managers = new LinkedHashSet<>();

    private ClrAssemblyManager()
    {
    }

    public static void registerInstallationManager(AbstractClrInstallationManager manager)
    {
        _managers.add(manager);
    }

    public static void addAdminWarningMessages(Collection<String> messages)
    {
        for (AbstractClrInstallationManager manager : _managers)
        {
            if (!manager.isInstalled(DbScope.getLabKeyScope()))
            {
                manager.addAdminWarningMessages(messages);
            }
        }
    }
}
