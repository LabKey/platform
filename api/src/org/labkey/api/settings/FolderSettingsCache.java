/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;

/**
 * User: adam
 * Date: 12/15/13
 * Time: 10:31 AM
 */

// Folder settings inherit all the way up the folder tree. All the property sets involved should be cached, but the walk up the tree
// is a potentially expensive operation to perform just to format a date or number. So, we cache the set of resolved properties
// on a per-container basis and clear the entire cache on every change of look and feel settings.
public class FolderSettingsCache
{
    private static final BlockingCache<Container, FolderSettings> CACHE = CacheManager.getBlockingCache(10000, CacheManager.DAY, "Folder Settings", new CacheLoader<Container, FolderSettings>()
    {
        @Override
        public FolderSettings load(Container c, @Nullable Object argument)
        {
            return new FolderSettings(c);
        }
    });

    public static String getDefaultDateFormat(Container c)
    {
        return CACHE.get(c).getDefaultDateFormat();
    }

    public static String getDefaultDateTimeFormat(Container c)
    {
        return CACHE.get(c).getDefaultDateTimeFormat();
    }

    public static String getDefaultNumberFormat(Container c)
    {
        return CACHE.get(c).getDefaultNumberFormat();
    }

    public static boolean areRestrictedColumnsEnabled(Container c)
    {
        return CACHE.get(c).areRestrictedColumnsEnabled();
    }

    public static void clear()
    {
        CACHE.clear();
    }

    public static void remove(Container c)
    {
        CACHE.remove(c);
    }

    private static class FolderSettings
    {
        private final String _defaultDateFormat;
        private final String _defaultDateTimeFormat;
        private final String _defaultNumberFormat;
        private final boolean _restrictedColumnsEnabled;

        FolderSettings(Container c)
        {
            LookAndFeelProperties props = LookAndFeelProperties.getInstance(c);
            _defaultDateFormat = props.getDefaultDateFormat();
            _defaultDateTimeFormat = props.getDefaultDateTimeFormat();
            _defaultNumberFormat = props.getDefaultNumberFormat();
            _restrictedColumnsEnabled = props.areRestrictedColumnsEnabled();
        }

        public String getDefaultNumberFormat()
        {
            return _defaultNumberFormat;
        }

        public String getDefaultDateFormat()
        {
            return _defaultDateFormat;
        }

        public String getDefaultDateTimeFormat()
        {
            return _defaultDateTimeFormat;
        }

        public boolean areRestrictedColumnsEnabled()
        {
            return _restrictedColumnsEnabled;
        }
    }

    public static class FolderSettingsCacheListener implements ContainerManager.ContainerListener
    {
        @Override
        public void containerCreated(Container c, User user)
        {
            // Don't care... nothing should be cached for a brand new container, and it must be a leaf node (doesn't
            // affect other folders' settings.
        }

        @Override
        public void containerDeleted(Container c, User user)
        {
            // Should be sufficient to remove settings for this container only; in a recursive delete, this method
            // is called on each container
            remove(c);
        }

        @Override
        public void containerMoved(Container c, Container oldParent, User user)
        {
            // When moving a tree, this is called only for the top node, so we need to clear the entire cache.
            clear();
        }

        @NotNull
        @Override
        public Collection<String> canMove(Container c, Container newParent, User user)
        {
            return Collections.emptyList();
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt)
        {
            // Don't care
        }
    }
}