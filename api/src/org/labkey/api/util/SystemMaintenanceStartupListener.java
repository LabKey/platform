package org.labkey.api.util;

import jakarta.servlet.ServletContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.MapBasedStartupPropertyHandler;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.STRONG;

public class SystemMaintenanceStartupListener implements StartupListener
{
    @Override
    public String getName()
    {
        return "System maintenance task startup property handler";
    }

    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        ModuleLoader.getInstance().handleStartupProperties(new SystemMaintenanceStartupPropertyHandler());
    }

    private static class SystemMaintenanceStartupPropertyHandler extends MapBasedStartupPropertyHandler<MaintenanceTask>
    {
        public SystemMaintenanceStartupPropertyHandler()
        {
            super(
                "SystemMaintenance",
                MaintenanceTask.class.getName(),
                SystemMaintenance.getTasks().stream()
                    .filter(MaintenanceTask::canDisable)
                    .filter(task -> task.getPropertyName() != null)
                    .sorted(Comparator.comparing(MaintenanceTask::getPropertyName, String.CASE_INSENSITIVE_ORDER))
            );
        }

        @Override
        public DOM.Renderable getScopeDescription()
        {
            return SPAN(STRONG(getScope()), " - set these properties to true/false to enable/disable the corresponding task");
        }

        @Override
        public void handle(Map<MaintenanceTask, StartupPropertyEntry> properties)
        {
            Map<Boolean, Set<String>> map = properties.values().stream()
                .collect(
                    Collectors.partitioningBy(prop -> Boolean.valueOf(prop.getValue()), Collectors.mapping(StartupPropertyEntry::getName, Collectors.toSet()))
                );

            if (!properties.isEmpty())
                SystemMaintenance.ensureTaskProperties(map.get(true), map.get(false));
        }
    }
}