package org.labkey.api.settings;

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AdminConsole.ExperimentalFeatureFlag;
import org.labkey.api.util.StartupListener;

import javax.servlet.ServletContext;
import java.util.Comparator;
import java.util.Map;

import static org.labkey.api.settings.AppProps.SCOPE_EXPERIMENTAL_FEATURE;

public class ExperimentalFeatureStartupListener implements StartupListener
{
    @Override
    public String getName()
    {
        return "Experimental feature startup property handler";
    }

    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        ModuleLoader.getInstance().handleStartupProperties(new ExperimentalFeatureStartupPropertyHandler());
    }

    private static class ExperimentalFeatureStartupPropertyHandler extends MapBasedStartupPropertyHandler<ExperimentalFeatureFlag>
    {
        public ExperimentalFeatureStartupPropertyHandler()
        {
            super(
                SCOPE_EXPERIMENTAL_FEATURE,
                ExperimentalFeatureFlag.class.getName(),
                AdminConsole.getExperimentalFeatureFlags().stream().sorted(Comparator.comparing(ExperimentalFeatureFlag::getPropertyName, String.CASE_INSENSITIVE_ORDER))
            );
        }

        @Override
        public void handle(Map<ExperimentalFeatureFlag, StartupPropertyEntry> properties)
        {
            ExperimentalFeatureService svc = ExperimentalFeatureService.get();
            properties.forEach((sp, entry) -> svc.setFeatureEnabled(sp.getFlag(), BooleanUtils.toBoolean(entry.getValue()), null));
        }
    }
}
