package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

/**
 * User: jeckels
 * Date: 1/23/14
 */
public interface ShortURLService
{
    /** Checks for a registered short URL that matches the string. The string itself should not contain slashes */
    @Nullable
    public ShortURLRecord resolveShortURL(@NotNull String shortURL);

    /** Save the URL and its redirect target. If it is a new short URL, it will be owned by the user who's creating it.
     * Guests may not save or update URLs. If it is already registered, permissions will be checked. To grant or remove
     * permissions, get the SecurityPolicy for the record, manipulate it, and save it.
     * @throws ValidationException if the URL is invalid (contains slashes, etc)
     * @throws org.labkey.api.view.UnauthorizedException if the user does not have permission to save the URL
     */
    @NotNull
    public ShortURLRecord saveShortURL(@NotNull String shortURL, @NotNull URLHelper fullURL, @NotNull User user) throws ValidationException;

    /**
     * Delete the previously saved short URL.
     * @throws org.labkey.api.view.UnauthorizedException if the user does not have permission to delete the URL
     */
    public void deleteShortURL(@NotNull ShortURLRecord record, @NotNull User user);

}
