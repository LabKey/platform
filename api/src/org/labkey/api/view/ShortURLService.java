/*
 * Copyright (c) 2014-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.util.List;

/**
 * Manages short URLs, TinyURL-style redirects handled within the server.
 * User: jeckels
 * Date: 1/23/14
 */
public interface ShortURLService
{
    /** Checks for a registered short URL that matches the string. The string itself should not contain slashes */
    @Nullable
    ShortURLRecord resolveShortURL(@NotNull String shortURL);

    /** Gets all of the short URLs that are configured on the current server */
    @NotNull
    List<ShortURLRecord> getAllShortURLs();

    /** Save the URL and its redirect target. If it is a new short URL, it will be owned by the user who's creating it.
     * Guests may not save or update URLs. If it is already registered, permissions will be checked. To grant or remove
     * permissions, get the SecurityPolicy for the record, manipulate it, and save it.
     * @throws ValidationException if the URL is invalid (contains slashes, etc)
     * @throws org.labkey.api.view.UnauthorizedException if the user does not have permission to save the URL
     */
    @NotNull
    ShortURLRecord saveShortURL(@NotNull String shortURL, @NotNull URLHelper fullURL, @NotNull User user) throws ValidationException;

    /**
     * Delete the previously saved short URL.
     * @throws org.labkey.api.view.UnauthorizedException if the user does not have permission to delete the URL
     */
    void deleteShortURL(@NotNull ShortURLRecord record, @NotNull User user) throws ValidationException;

    String validateShortURL(String shortURL) throws ValidationException;

    ShortURLRecord getForEntityId(@NotNull String entityId);

    void addListener(ShortURLListener listener);

    void removeListener(ShortURLListener listener);

    interface ShortURLListener
    {
        /**
         * Called prior to deleting a shortURL, to determine if a shortURL can be deleted.
         * @return a list of errors that should prevent the shortURL from being deleted (e.g. if this shortURL is still
         * referenced as a FK in another table)
         */
        @NotNull
        List<String> canDelete(ShortURLRecord shortUrl);
    }
}
