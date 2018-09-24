package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * Part of an ExpProtocol design that describes the expected input materials.
 */
public interface ExpMaterialProtocolInput extends ExpProtocolInput
{
    /**
     * Returns null if the ExpMaterial matches the requirements of this ExpProtocolInput or a String error message.
     */
    String matches(@NotNull User user, @NotNull Container c, @NotNull ExpMaterial material);

    /** If non-null, the ExpSampleSet that the input ExpMaterial must come from.  If null, any ExpMaterial is allowed. */
    @Nullable ExpSampleSet getType();

}
