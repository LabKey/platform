/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.settings.StartupPropertyEntry.modifier.bootstrap;

/**
 * Manager to registering and tracking the various {@link FolderType} implementations provided by modules.
 * Created by adam on 5/8/2015.
 */
public class FolderTypeManager
{
    private static final Logger LOG = LogHelper.getLogger(FolderTypeManager.class, "Manages registry of folder types");
    private static final FolderTypeManager INSTANCE = new FolderTypeManager();
    private static final String SIMPLE_TYPE_DIR_NAME = "folderTypes";
    private static final String SIMPLE_TYPE_FILE_EXTENSION = ".foldertype.xml";
    /** PropertyManager category name for folder type enabled state properties */
    private static final String FOLDER_TYPE_ENABLED_STATE = "FolderTypeEnabledState";
    /** PropertyManager category name for the default folder type property */
    public static final String FOLDER_TYPE_DEFAULT = "FolderTypeDefault";

    private final ModuleResourceCache<Collection<SimpleFolderType>> CACHE = ModuleResourceCaches.create("File-based folder types", new SimpleFolderTypeCacheHandler(), ResourceRootProvider.getStandard(new Path(SIMPLE_TYPE_DIR_NAME)));
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

        @Override
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
        if (!ModuleLoader.getInstance().isStartupInProgress() && !ModuleLoader.getInstance().isStartupComplete())
        {
            throw new IllegalStateException("Don't call registerFolderType() this early; it can cause WebParts to get populated from modules that are about to be pruned.");
        }
        if (_javaFolderTypes.containsKey(folderType.getName()))
        {
            String msg = "Unable to register folder type " + folderType.getName() + " from module " + sourceModule.getName() +
                    ".  A folder type with this name has already been registered ";
            Throwable ex = new IllegalStateException(msg);
            LOG.error(msg, ex);
            ModuleLoader.getInstance().addModuleFailure(sourceModule.getName(), ex);
        }
        else
        {
            _javaFolderTypes.put(folderType.getName(), folderType);
            clearAllFolderTypes(); // Make sure _allFolderTypes doesn't miss anything from _javaFolderTypes
        }
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

    /** @return an unmodifiable collection of registered folder types (those not disabled by site-wide configuration
     *  by an admin). Optionally filter the list to skip folder types defined by restricted modules */
    public Collection<FolderType> getEnabledFolderTypes(boolean includeRestrictedModules)
    {
        List<FolderType> enabled = new ArrayList<>();
        synchronized (FOLDER_TYPE_LOCK)
        {
            Map<String, String> enabledStates = PropertyManager.getProperties(ContainerManager.getRoot(), FOLDER_TYPE_ENABLED_STATE);
            for (FolderType folderType : ensureAllFolderTypes().values())
            {
                // Unless we have specific saved config setting it to disabled, treat it as enabled
                if (!enabledStates.containsKey(folderType.getName()) || !enabledStates.get(folderType.getName()).equalsIgnoreCase(Boolean.FALSE.toString()))
                {
                    enabled.add(folderType);
                }
            }
        }

        if (includeRestrictedModules)
        {
            return Collections.unmodifiableList(enabled);
        }

        // Filter out the restricted ones
        List<FolderType> folderTypes = new LinkedList<>();
        for (FolderType folderType : enabled)
        {
            if (!Container.hasRestrictedModule(folderType))
                folderTypes.add(folderType);
        }
        return Collections.unmodifiableList(folderTypes);
    }

    /**
     * @return Default folder type set by a site administrator, or null if a default has not been set.
     */
    @Nullable
    public FolderType getDefaultFolderType()
    {
        String folderTypeName = PropertyManager.getProperties(FOLDER_TYPE_DEFAULT).get(FOLDER_TYPE_DEFAULT);
        return null == folderTypeName ? null : getFolderType(folderTypeName);
    }

    /**
     * @param enabledFolderTypes the new set of enabled folder types. This overwrites any previous enabled/disabled state.
     * @param defaultFolderType the folder type that should be set as the default. This setting can be used to set the
     *                          default folder type option when creating a new folder, for example. The setting is
     *                          updated only if the folder type is included in the given list of enabled folder types.
     */
    public void setEnabledFolderTypes(Collection<FolderType> enabledFolderTypes, FolderType defaultFolderType)
    {
        PropertyManager.PropertyMap enabledStates = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FOLDER_TYPE_ENABLED_STATE, true);
        // Reset completely based on the supplied config
        enabledStates.clear();
        for (FolderType folderType : getAllFolderTypes())
        {
            enabledStates.put(folderType.getName(), Boolean.toString(enabledFolderTypes.contains(folderType)));
        }
        enabledStates.save();

        if (defaultFolderType != null && enabledFolderTypes.contains(defaultFolderType))
        {
            PropertyManager.PropertyMap propDefaultFolderType = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FOLDER_TYPE_DEFAULT, true);
            propDefaultFolderType.put(FOLDER_TYPE_DEFAULT, defaultFolderType.getName());
            propDefaultFolderType.save();
        }
    }

    private Collection<SimpleFolderType> getSimpleFolderTypes(Module module)
    {
        return CACHE.getResourceMap(module);
    }

    private static class SimpleFolderTypeCacheHandler implements ModuleResourceCacheHandler<Collection<SimpleFolderType>>
    {
        @Override
        public Collection<SimpleFolderType> load(Stream<? extends Resource> resources, Module module)
        {
            return unmodifiable(resources
                .filter(getFilter(SIMPLE_TYPE_FILE_EXTENSION))
                .map(SimpleFolderType::create)
                .collect(Collectors.toList()));
        }

        @Nullable
        @Override
        public ModuleResourceCacheListener createChainedListener(Module module)
        {
            return new ModuleResourceCacheListener()
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

                @Override
                public void moduleChanged(Module module)
                {
                    FolderTypeManager.get().clearAllFolderTypes();
                }
            };
        }
    }

    public enum FolderTypeStartupProperties implements StartupProperty
    {
        disabledTypes
        {
            @Override
            public String getDescription()
            {
                return "Comma-separated list of folder types to disable on this server";
            }
        };
    }

    private static final String SCOPE_FOLDER_TYPES = "FolderTypes";

    public void populateWithStartupProps()
    {
        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(SCOPE_FOLDER_TYPES, FolderTypeStartupProperties.class)
        {
            @Override
            public void handle(Map<FolderTypeStartupProperties, StartupPropertyEntry> properties)
            {
                properties.forEach((sp, cp)->{
                    // apply disabledTypes prop only at bootstrap
                    if (cp.getModifier() == bootstrap)
                        populateDisabledFolderTypesWithStartupProps(cp.getValue());
                });
            }
        });
    }

    private void populateDisabledFolderTypesWithStartupProps(String value)
    {
        if (value != null)
        {
            PropertyManager.PropertyMap enabledStates = PropertyManager.getWritableProperties(ContainerManager.getRoot(), FOLDER_TYPE_ENABLED_STATE, true);
            for (String folderTypeName : StringUtils.split(value, ';'))
                enabledStates.put(folderTypeName, Boolean.toString(false));
            enabledStates.save();
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testModuleResourceCache()
        {
            FolderTypeManager manager = get();

            // Load all the folder types to ensure no exceptions
            int folderTypeCount = manager.CACHE.streamAllResourceMaps()
                .mapToInt(Collection::size)
                .sum();

            LOG.info(folderTypeCount + " folder types defined in all modules");

            // Make sure the cache retrieves the expected number of folder types from the simpletest module, if present

            Module simpleTest = ModuleLoader.getInstance().getModule("simpletest");

            if (null != simpleTest)
                assertEquals("Folder types from the simpletest module", 6, manager.CACHE.getResourceMap(simpleTest).size());
        }
    }
}
