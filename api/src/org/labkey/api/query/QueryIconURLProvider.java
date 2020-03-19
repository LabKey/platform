package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

public interface QueryIconURLProvider
{
    @Nullable String getIconURL(String schemaName, String queryName, Container container, User user);
}
