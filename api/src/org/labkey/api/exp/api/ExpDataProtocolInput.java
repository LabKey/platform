package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Part of an ExpProtocol design that describes the expected input data.
 * CONSIDER: Add matching based on <code>DataType</code> or file name suffix.
 */
public interface ExpDataProtocolInput extends ExpProtocolInput
{
    /**
     * Returns null if the ExpData matches the requirements of this ExpProtocolInput or a String error message.
     */
    String matches(@NotNull User user, @NotNull Container c, @NotNull ExpData data);

    /**
     * If non-null, the ExpDataClass that the input ExpData must come from.
     */
    @Nullable ExpDataClass getType();
}
