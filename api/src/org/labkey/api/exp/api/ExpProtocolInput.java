package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;

/**
 * Base interface for the different object types that can be the input or output of an {@link ExpProtocol}.
 */
public interface ExpProtocolInput extends ExpObject
{
    /**
     * The protocol this expected material is an input or output of.
     */
    @NotNull ExpProtocol getProtocol();

    /**
     * True if this represents an input to the associated ExpProtocol, otherwise an output of the ExpProtocol.
     */
    boolean isInput();

    /** A criteria that the {@link ExpRunItem} must match to be considered a allowable input. */
    @Nullable ExpProtocolInputCriteria getCriteria();

    /** The minimum number of input items required. */
    int getMinOccurs();

    /** Get the maximum number of allowed input items or null for unlimited. */
    @Nullable Integer getMaxOccurs();

    @Override
    void save(User user);

    /**
     * Returns true if the ExpProtocolInput are compatible.  Only compatible inputs may
     * be used to create an output -> input link between two ExpProtocol.
     */
    boolean isCompatible(ExpProtocolInput other);
}
