package org.labkey.api.settings;

import jakarta.servlet.ServletContext;
import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.settings.AdminConsole.OptionalFeatureFlag;
import org.labkey.api.util.DOM;
import org.labkey.api.util.StartupListener;

import java.util.Comparator;
import java.util.Map;

import static org.labkey.api.settings.AppProps.SCOPE_OPTIONAL_FEATURE;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.STRONG;

public class OptionalFeatureStartupListener implements StartupListener
{
    @Override
    public String getName()
    {
        return "Optional feature startup property handler";
    }

    @Override
    public void moduleStartupComplete(ServletContext servletContext)
    {
        ModuleLoader.getInstance().handleStartupProperties(new OptionalFeatureStartupPropertyHandler());
    }

    private static class OptionalFeatureStartupPropertyHandler extends MapBasedStartupPropertyHandler<OptionalFeatureFlag>
    {
        public OptionalFeatureStartupPropertyHandler()
        {
            super(
                SCOPE_OPTIONAL_FEATURE,
                OptionalFeatureFlag.class.getName(),
                AdminConsole.getOptionalFeatureFlags().stream()
                    .filter(flag -> flag.getPropertyName() != null)
                    .sorted(Comparator.comparing(OptionalFeatureFlag::getPropertyName, String.CASE_INSENSITIVE_ORDER))
            );
        }

        @Override
        public DOM.Renderable getScopeDescription()
        {
            return SPAN(STRONG(getScope()), " - set these properties to true/false to enable/disable the corresponding feature flag");
        }

        @Override
        public void handle(Map<OptionalFeatureFlag, StartupPropertyEntry> properties)
        {
            OptionalFeatureService svc = OptionalFeatureService.get();
            properties.forEach((sp, entry) -> svc.setFeatureEnabled(sp.getFlag(), BooleanUtils.toBoolean(entry.getValue()), null));
        }
    }
}
