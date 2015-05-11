/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.files.FileSystemDirectoryListener;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by adam on 5/8/2015.
 */
public class FolderTypeManager
{
    private static final Logger LOG = Logger.getLogger(FolderTypeManager.class);
    private static final FolderTypeManager INSTANCE = new FolderTypeManager();
    private static final String SIMPLE_TYPE_DIR_NAME = "folderTypes";
    private static final String SIMPLE_TYPE_FILE_EXTENSION = ".foldertype.xml";
    /** PropertyManager category name for folder type enabled state properties */
    private static final String FOLDER_TYPE_ENABLED_STATE = "FolderTypeEnabledState";

    private final ModuleResourceCache<SimpleFolderType> CACHE = ModuleResourceCaches.create(new Path(SIMPLE_TYPE_DIR_NAME), "File-based folder types", new SimpleFolderTypeCacheHandler());
    private final Map<String, FolderType> _javaFolderTypes = new ConcurrentHashMap<>();  // Map of folder types that are registered via java code
    private final Object FOLDER_TYPE_LOCK = new Object();

    private Map<String, FolderType> _allFolderTypes = null;                              // Map of all folder types, both file-based and code-based

    private FolderTypeManager()
    {
    }

    public static FolderTypeManager get()
    {
        return INSTANCE;
    }

    private static class FolderTypeComparator implements Comparator<String>
    {
        //Sort NONE to the bottom and Collaboration to the top
        private static final String noneStr = FolderType.NONE.getName();
        private static final String collabStr = "Collaboration"; //Cheating

        public int compare(String s, String s1)
        {
            if (s.equals(s1))
                return 0;

            if (noneStr.equals(s))
                return 1;
            if (noneStr.equals(s1))
                return -1;
            if (collabStr.equals(s))
                return -1;
            if (collabStr.equals(s1))
                return 1;

            return s.compareTo(s1);
        }
    }

    /** Remove the named folder type from the list of known options */
    public void unregisterFolderType(String name)
    {
        _javaFolderTypes.remove(name);
    }

    public void registerFolderType(Module sourceModule, FolderType folderType)
    {
        if (_javaFolderTypes.containsKey(folderType.getName()))
        {
            String msg = "Unable to register folder type " + folderType.getName() + " from module " + sourceModule.getName() +
                    ".  A folder type with this name has already been registered ";
            Throwable ex = new IllegalStateException(msg);
            LOG.error(msg, ex);
            ModuleLoader.getInstance().addModuleFailure(sourceModule.getName(), ex);
        }
        else
            _javaFolderTypes.put(folderType.getName(), folderType);
    }

    /**
     * If needed, combine java folder types (typically registered at startup) and the file-based simple folder types (from the cache) to
     * create master map of _allFolderTypes.
     * @return All folder types defined by all modules
     */
    private Map<String, FolderType> ensureAllFolderTypes()
    {
        assert Thread.holdsLock(FOLDER_TYPE_LOCK);

        if (null == _allFolderTypes)
        {
            _allFolderTypes = new TreeMap<>(new FolderTypeComparator());
            _allFolderTypes.putAll(_javaFolderTypes);

            for (Module module : ModuleLoader.getInstance().getModules())
                for (SimpleFolderType folderType : getSimpleFolderTypes(module))
                    _allFolderTypes.put(folderType.getName(), folderType);
        }

        return _allFolderTypes;
    }

    private void clearAllFolderTypes()
    {
        synchronized (FOLDER_TYPE_LOCK)
        {
            _allFolderTypes = null;
        }
    }

    @Nullable
    public FolderType getFolderType(String name)
    {
        synchronized (FOLDER_TYPE_LOCK)
        {
            Map<String, FolderType> allFolderTypes = ensureAllFolderTypes();
            FolderType result = allFolderTypes.get(name);
            if (result != null)
            {
                return result;
            }

            // Check if it's a legacy name for an existing folder type
            for (FolderType folderType : allFolderTypes.values())
            {
                if (folderType.getLegacyNames().contains(name))
                {
                    return folderType;
                }
            }
            return null;
        }
    }

    /** @return an unmodifiable collection of ALL registered folder types, even those that have been disabled on this
     * server by an administrator */
    public Collection<FolderType> getAllFolderTypes()
    {
        synchronized (FOLDER_TYPE_LOCK)
        {
            return Collections.unmodifiableCollection(new ArrayList<>(ensureAllFolderTypes().values()));
        }
    }

    /** @return an unmodifiable collection of ALL registered folder types, even those that have been disabled on this
     * server by an administrator, except if the user does not have EnableRestrictedModules permission then folder types
     * that have restricted modules are excluded */
    public Collection<FolderType> getFolderTypes(boolean userHasEnableRestrictedModules)
    {
        if (userHasEnableRestrictedModules)
        {
            return getAllFolderTypes();
        }

        List<FolderType> allFolderTypes;
        synchronized (FOLDER_TYPE_LOCK)
        {
            allFolderTypes = new LinkedList<>(ensureAllFolderTypes().values());
        }

        List<FolderType> folderTypes = new LinkedList<>();
        for (FolderType folderType : allFolderTypes)
        {
            if (!Container.hasRestrictedModule(folderType))
                folderTypes.add(folderType);
        }
        return folderTypes;
    }

    /** @return all of the folder types that have not been explicitly disabled by an administrator. New folder types
     * that lack specific enabled/disabled state will be considered enabled.
     */
    public Collection<FolderType> getEnabledFolderTypes()
    {
        ArrayList<FolderType> result = new ArrayList<>();
        synchronized (FOLDER_TYPE_LOCK)
        {
            Map<String, String> enabledStates = PropertyManager.getProperties(ContainerManager.getRoot(), FOLDER_TYPE_ENABLED_STATE);
            for (FolderType folderType : ensureAllFolderTypes().values())
            {
                // Unless we have specific saved config setting it to disabled, treat it as enabled
                if (!enabledStates.containsKey(folderType.getName()) || !enabledStates.get(folderType.getName()).equalsIgnoreCase(Boolean.FALSE.toString()))
                {
                    result.add(folderType);
                }
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    /**
     * @param enabledFolderTypes the new set of enabled folder types. This overwrites any previous enabled/disabled state.
     */
    public void setEnabledFolderTypes(Collection<FolderType> enabledFolderTypes)
    {
        PropertyManager.PropertyMap enabledStates = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FOLDER_TYPE_ENABLED_STATE, true);
        // Reset completely based on the supplied config
        enabledStates.clear();
        for (FolderType folderType : getAllFolderTypes())
        {
            enabledStates.put(folderType.getName(), Boolean.toString(enabledFolderTypes.contains(folderType)));
        }
        enabledStates.save();
    }

    private Collection<SimpleFolderType> getSimpleFolderTypes(Module module)
    {
        return CACHE.getResources(module);
    }

    private static class SimpleFolderTypeCacheHandler implements ModuleResourceCacheHandler<String, SimpleFolderType>
    {
        @Override
        public boolean isResourceFile(String filename)
        {
            return StringUtils.endsWithIgnoreCase(filename, SIMPLE_TYPE_FILE_EXTENSION);
        }

        @Override
        public String getResourceName(Module module, String filename)
        {
            return filename;
        }

        @Override
        public String createCacheKey(Module module, String resourceName)
        {
            return ModuleResourceCache.createCacheKey(module, resourceName);
        }

        @Override
        public CacheLoader<String, SimpleFolderType> getResourceLoader()
        {
            return new CacheLoader<String, SimpleFolderType>()
            {
                @Override
                public SimpleFolderType load(String key, @Nullable Object argument)
                {
                    ModuleResourceCache.CacheId id = ModuleResourceCache.parseCacheKey(key);
                    Module module = id.getModule();
                    String filename = id.getName();
                    Path path = new Path(SIMPLE_TYPE_DIR_NAME, filename);
                    Resource resource  = module.getModuleResolver().lookup(path);

                    return SimpleFolderType.create(resource);
                }
            };
        }

        @Nullable
        @Override
        public FileSystemDirectoryListener createChainedDirectoryListener(Module module)
        {
            return new FileSystemDirectoryListener()
            {
                @Override
                public void entryCreated(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    FolderTypeManager.get().clearAllFolderTypes();
                }

                @Override
                public void entryDeleted(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    FolderTypeManager.get().clearAllFolderTypes();
                }

                @Override
                public void entryModified(java.nio.file.Path directory, java.nio.file.Path entry)
                {
                    FolderTypeManager.get().clearAllFolderTypes();
                }

                @Override
                public void overflow()
                {
                    FolderTypeManager.get().clearAllFolderTypes();
                }
            };
        }
    }
}
