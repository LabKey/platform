package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface ExpProtocolInputCriteria
{
    interface Factory
    {
        @NotNull
        String getName();

        @NotNull
        ExpProtocolInputCriteria create(@Nullable String config);
    }

    String getTypeName();

    List<? extends ExpRunItem> findMatching(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c);

    /**
     * Returns null if the item matches the criteria.  If invalid, returns a string error message.
     */
    @Nullable
    String matches(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c, @NotNull ExpRunItem item);

    String serializeConfig();

    boolean isCompatible(ExpProtocolInputCriteria other);
}
