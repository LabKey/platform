package org.labkey.api.premium;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;

import java.util.Collections;
import java.util.Map;

public interface AntiVirusProvider
{
    @NotNull String getId();             // something unique e.g. className

    @NotNull String getDescription();    // e.g. ClamAV Daemon

    @Nullable ActionURL getConfigurationURL();

    @NotNull AntiVirusService getService();

    default boolean claims(String id)
    {
        return getId().equals(id);
    }

    default Map<String, Object> getUsageMetrics() { return Collections.emptyMap(); }
}
