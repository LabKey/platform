package org.labkey.api.util;

/**
 * Same as {@link SafeToRender}, but intended to mark enums that don't override {@code toString()}. Default Enum.toString()
 * returns the constant's name, which is a Java identifier, which is safe-to-render.
 */
public interface SafeToRenderEnum extends SafeToRender
{
    /**
     * The only override of this should be Enum.toString(); any enum that overrides toString() should not implement SafeToRenderEnum.
     */
    @Override
    String toString();
}
