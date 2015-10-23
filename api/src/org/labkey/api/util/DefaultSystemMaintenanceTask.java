package org.labkey.api.util;

/**
 * Created by klum on 9/29/2015.
 */
public abstract class DefaultSystemMaintenanceTask implements SystemMaintenance.MaintenanceTask
{
    @Override
    public boolean isEnabledByDefault()
    {
        return true;
    }

    @Override
    public boolean hideFromAdminPage()
    {
        return false;
    }

    @Override
    public boolean canDisable()
    {
        return true;
    }
}
