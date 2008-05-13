/*
 * Copyright (c) 2005-2008 LabKey Corporation
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

package org.labkey.api.attachments;

import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.ContainerManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: Nov 18, 2005
 */
public class AttachmentCache
{
    public static final String LOGO_FILE_NAME_PREFIX = "cpas-site-logo";
    public static final String FAVICON_FILE_NAME = "cpas-site-favicon.ico";

    private static CacheableWriter _cachedLogo;
    private static CacheableWriter _cachedFavIcon;
    private static CacheableWriter _cachedGradient;
    private static Map<String, CacheableWriter> _authLogoMap = new HashMap<String, CacheableWriter>();

    public static void clearLogoCache()
    {
        _cachedLogo = null;
    }

    public static void clearFavIconCache()
    {
        _cachedFavIcon = null;
    }

    public static CacheableWriter getCachedLogo()
    {
        return _cachedLogo;
    }

    public static CacheableWriter getCachedFavIcon()
    {
        return _cachedFavIcon;
    }

    public static void cacheLogo(CacheableWriter logo)
    {
        _cachedLogo = logo;
    }

    public static void cacheFavIcon(CacheableWriter favIcon)
    {
        _cachedFavIcon = favIcon;
    }

    public static void cacheGradient(CacheableWriter gradient)
    {
        _cachedGradient = gradient;
    }

    public static CacheableWriter getCachedGradient()
    {
        return _cachedGradient;
    }

    public static void clearGradientCache()
    {
        _cachedGradient = null;
    }

    public static Attachment lookupFavIconAttachment() throws SQLException
    {
        return lookupAttachment(FAVICON_FILE_NAME);
    }

    public static Attachment lookupLogoAttachment() throws SQLException
    {
        Attachment[] attachments = AttachmentService.get().getAttachments(ContainerManager.RootContainer.get());
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().startsWith(LOGO_FILE_NAME_PREFIX))
            {
                return attachment;
            }
        }
        return null;
    }

    public static Attachment lookupAttachment(String name) throws SQLException
    {
        return AttachmentService.get().getAttachment(ContainerManager.RootContainer.get(), name);
    }


    public static void cacheAuthLogo(String name, CacheableWriter writer)
    {
        _authLogoMap.put(name, writer);
    }


    public static CacheableWriter getAuthLogo(String name)
    {
        return _authLogoMap.get(name);
    }


    public static void clearAuthLogoCache()
    {
        _authLogoMap.clear();
    }
}
