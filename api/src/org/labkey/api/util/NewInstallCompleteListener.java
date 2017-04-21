package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;

/**
 * Callback for when the server is a new install and the install has completed.
 */
public interface NewInstallCompleteListener
{
    void onNewInstallComplete(@NotNull User user);
}
