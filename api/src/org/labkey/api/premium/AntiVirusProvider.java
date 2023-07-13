package org.labkey.api.premium;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ActionURL;

public interface AntiVirusProvider
{
    @NotNull String getId();             // something unique e.g. className

    @NotNull String getDescription();    // e.g. ClamAV Daemon

    @Nullable ActionURL getConfigurationURL();

    @NotNull AntiVirusService getService();
}
