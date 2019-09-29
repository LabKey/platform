/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.element.Option;
import org.labkey.api.util.element.Option.OptionBuilder;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.WebPartView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by marty on 7/5/2017.
 */
public interface TemplateProperties
{
    String SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME = "ShowOnlyInProjectRoot";
    String NONE = "none";

    String getDisplayConfigs();
    String getDisplayPropertyName();
    String getModulePropertyName();
    String getFileName();
    String getPropertyDisplayType();
    Container getContainer();
    String getDefaultModule();
    TemplateProperties getRootProperties();

    private Boolean isDisplay(boolean inherit)
    {
        String displayProp = getProperty(getDisplayPropertyName(), inherit);
        return BooleanUtils.toBooleanObject(displayProp);
    }

    default void setDisplay(Boolean isDisplay)
    {
        setProperty(getDisplayPropertyName(), isDisplay != null ? String.valueOf(isDisplay) : null);
    }

    private @Nullable Module getModule()
    {
        return ModuleLoader.getInstance().getModule(getModuleName());
    }

    default @Nullable HtmlView getView()
    {
        Module module = getModule();
        HtmlView view = null;

        if (null != module)
        {
            view = ModuleHtmlView.get(module, getFileName());

            if (null != view)
            {
                view.setFrame(WebPartView.FrameType.NONE);
            }
        }

        return view;
    }

    private @Nullable String getModuleName()
    {
        return getModuleName(true);
    }

    private @Nullable String getModuleName(boolean inherit)
    {
        Boolean isDisplay = isDisplay(inherit);

        if (null == isDisplay)
            return getDefaultModule();

        return isDisplay ? getProperty(getModulePropertyName(), inherit) : null;
    }

    default void setModule(String module)
    {
        setProperty(getModulePropertyName(), module);
    }

    /**
     * Should this resource appear in all folders or only in the project root.
     */
    default boolean isShowOnlyInProjectRoot()
    {
        return BooleanUtils.toBoolean(getProperty(SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME));
    }

    default void setIsShowOnlyInProjectRoot(boolean showOnlyInProjectRoot)
    {
        setProperty(SHOW_ONLY_IN_PROJECT_ROOT_PROPERTY_NAME, String.valueOf(showOnlyInProjectRoot));
    }

    private void setProperty(String propName, String value)
    {
        PropertyManager.PropertyMap map;
        Container container = getContainer();

        if (container != null)
        {
            if (container.isRoot())
                map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
            else
                // if not site level, default to project level
                map = PropertyManager.getWritableProperties(container.getProject(), getDisplayConfigs(), true);
        }
        else
            throw new IllegalStateException("Container is null for this TemplateProperty");

        if (value == null)
            map.remove(propName);
        else
            map.put(propName, value);

        map.save();
    }

    private String getProperty(String propName)
    {
        return getProperty(propName, true);
    }

    /**
     * Helper to pull property values from the appropriate scopes
     *
     * @param inherit if true will inherit from the site root if the property is not defined in the container
     */
    private String getProperty(String propName, boolean inherit)
    {
        Map<String, String> map;
        Container container = getContainer();

        if (container != null)
        {
            // check to see if there is a project override, else get the site default
            if (!container.isRoot())
            {
                map = PropertyManager.getProperties(container.getProject(), getDisplayConfigs());
                if (map.containsKey(propName) || !inherit)
                    return map.get(propName);
            }
            map = PropertyManager.getProperties(getDisplayConfigs());
            return map.get(propName);
        }
        else
            throw new IllegalStateException("Container is null for this TemplateProperty");
    }

    private @NotNull String getNotDisplayedSetting()
    {
        return "No " + getPropertyDisplayType() + " Displayed";
    }

    private @NotNull String getInheritSetting()
    {
        TemplateProperties rootProperties = getRootProperties();
        return "Inherit from site settings (" + rootProperties.getCurrentSetting() + ")";
    }

    private @NotNull String getCurrentSetting()
    {
        String currentSetting = null;

        if (getContainer().isRoot())
        {
            String moduleName = getModuleName(false);
            currentSetting = null == moduleName ? getNotDisplayedSetting() : moduleName;
        }
        else
        {
            Boolean isDisplayObj = isDisplay(false);

            if (isDisplayObj == null)
            {
                currentSetting = getInheritSetting();
            }
            else
            {
                if (isDisplayObj)
                    currentSetting = getModuleName(false);

                if (null == currentSetting)
                   currentSetting = getNotDisplayedSetting();
            }
        }

        return currentSetting;
    }

    default List<Option> getOptions()
    {
        Map<String, String> modules = new LinkedHashMap<>();  // Keep the options in insertion order

        // Add the inherit option with appropriate text
        if (getContainer().isProject())
            modules.put(null, getInheritSetting());

        // Add the "No XXX Displayed" option
        modules.put(NONE, getNotDisplayedSetting());

        // Add all modules that contain the appropriate view, in alphabetical order
        ModuleLoader.getInstance().getModules().stream()
            .filter(m->ModuleHtmlView.exists(m, getFileName()))
            .map(Module::getName)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .forEach(name->modules.put(name, name));

        String currentSetting = getCurrentSetting();

        return modules.entrySet().stream()
            .map(e->new OptionBuilder()
                .value(e.getKey())
                .label(e.getValue())
                .selected(e.getValue().equals(currentSetting))
                .build())
            .collect(Collectors.toList());
    }
}
