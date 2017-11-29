/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.UnauthorizedException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Module properties allow administrators to set key configuration values for an install. They are accessible through
 * client APIs and in Folder Management. Properties can be set at a site-wide level, or at an individual container.
 * If a value is not set explicitly in a given container, it will inherit from its parent container or the site-level
 * setting.
 * User: bbimber
 * Date: 6/12/12
 */
public class ModuleProperty
{
    /*
    Note that module properties can be defined in module.xml files, so if adding property options here, they should also
    be added to propertyType in module.xsd and wired through in DefaultModule.loadXmlFile()
     */
    private Module _module;
    private String _name;
    private String _label;
    private boolean _canSetPerContainer = false;
    private boolean _excludeFromClientContext = false;
    private String _defaultValue = null;
    private String _description = null;
    private boolean _showDescriptionInline = false;
    private int _inputFieldWidth = 300;
    private InputType _inputType = InputType.text;
    private List<Option> _options = null;
    // optionsSupplier is intentionally omitted from module.xsd as it must be backed by java code
    private OptionSupplier _optionsSupplier = null;
    // Will the options list potentially be different for different containers? Only relevant if optionsSupplier is set.
    // Omitted from module.xsd as optionsSupplier is not used there either.
    private boolean _optionsByContainer = false;
    
    private List<Class<? extends Permission>> _editPermissions;

    public enum InputType
    {
        text,
        select {
            @Override
            boolean hasOptions() {return true;}
        },
        combo {
            @Override
            boolean hasOptions() {return true;}
        },
        checkbox;

        boolean hasOptions() {return false;}
    }

    public ModuleProperty(Module module, String name)
    {
        this(module, name, InputType.text);
    }

    public ModuleProperty(Module module, String name, InputType inputType, String description, String label, boolean canSetPerContainer)
    {
        this(module, name,inputType);
        _description = description;
        _label = label;
        _canSetPerContainer = canSetPerContainer;
    }

    public ModuleProperty(Module module, String name, InputType inputType)
    {
        _module = module;
        _name = name;

        //default to requiring admin permission
        _editPermissions = new ArrayList<>();
        _editPermissions.add(AdminPermission.class);

        _inputType = inputType;
    }

    public String getCategory()
    {
        return "moduleProperties" + "." + _module.getName();
    }

    public Module getModule()
    {
        return _module;
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label != null ? _label : getName();
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public boolean isCanSetPerContainer()
    {
        return _canSetPerContainer;
    }

    public void setCanSetPerContainer(boolean canSetPerContainer)
    {
        _canSetPerContainer = canSetPerContainer;
    }

    public String getDefaultValue()
    {
        return _defaultValue;
    }

    public void setDefaultValue(String defaultValue)
    {
        _defaultValue = defaultValue;
    }

    @NotNull
    public List<Class<? extends Permission>> getEditPermissions()
    {
        return _editPermissions == null ? new ArrayList<>(): _editPermissions;
    }

    public void setEditPermissions(List<Class<? extends Permission>> editPermissions)
    {
        _editPermissions = editPermissions;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public boolean isShowDescriptionInline()
    {
        return _showDescriptionInline;
    }

    public void setShowDescriptionInline(boolean showDescriptionInline)
    {
        _showDescriptionInline = showDescriptionInline;
    }

    public int getInputFieldWidth()
    {
        return _inputFieldWidth;
    }

    public void setInputFieldWidth(int inputFieldWidth)
    {
        _inputFieldWidth = inputFieldWidth;
    }

    public boolean isExcludeFromClientContext()
    {
        return _excludeFromClientContext;
    }

    public void setExcludeFromClientContext(boolean excludeFromClientContext)
    {
        _excludeFromClientContext = excludeFromClientContext;
    }

    public InputType getInputType()
    {
        return _inputType;
    }

    public void setInputType(InputType inputType)
    {
        _inputType = inputType;
    }

    public List<Option> getOptions()
    {
        return _options;
    }

    public void setOptions(List<Option> options)
    {
        _options = options;
    }

    public OptionSupplier getOptionsSupplier()
    {
        return _optionsSupplier;
    }

    public void setOptionsSupplier(OptionSupplier optionsSupplier, boolean optionsByContainer)
    {
        _optionsSupplier = optionsSupplier;
        _optionsByContainer = optionsByContainer;
    }

    public boolean isOptionsByContainer()
    {
        return _optionsByContainer;
    }

    public JSONObject toJson(Container c)
    {
        JSONObject ret = new JSONObject();

        ret.put("name", getName());
        ret.put("label", getLabel());
        ret.put("module", getModule().getName());
        ret.put("canSetPerContainer", isCanSetPerContainer());
        ret.put("defaultValue", getDefaultValue());
        ret.put("editPermissions", getEditPermissions());
        ret.put("description", getDescription());
        ret.put("showDescriptionInline", isShowDescriptionInline());
        ret.put("inputFieldWidth", getInputFieldWidth());
        ret.put("inputType", getInputType().toString());
        if (getInputType().hasOptions())
        {
            if (null != _optionsSupplier && !_optionsByContainer)
            {
                ret.put("options", toOptionMaps(getOptionsSupplier().get(c)));
            }
            else if (null != _options)
            {
                ret.put("options", toOptionMaps(getOptions()));
            }
        }
        return ret;
    }

    public void saveValue(@Nullable User user, Container c, @Nullable String value)
    {
        if (!isCanSetPerContainer() && !c.isRoot())
            throw new IllegalArgumentException("This property can not be set for this container.  It can only be set site-wide, which means it must be set on the root container.");

        // Don't bother checking permissions if we don't have a user
        if (user != null)
        {
            //properties provide their edit permissions, so we only enforce read on the container
            if (!c.hasPermission(user, ReadPermission.class))
                throw new UnauthorizedException("The user does not have read permission on this container");

            for (Class<? extends Permission> p : getEditPermissions())
            {
                if (!c.hasPermission(user, p))
                    throw new UnauthorizedException("The user does not have " + p.getName() + " permission on this container");
            }
        }

        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(PropertyManager.SHARED_USER, c, getCategory(), true);

        if (!StringUtils.isEmpty(value))
            props.put(getName(), value);
        else
            props.remove(getName());

        props.save();
    }

    /**
     * NOTE: does not test permissions
     */
    public String getEffectiveValue(Container c)
    {
        User propertyUser = PropertyManager.SHARED_USER;  // Only shared properties are supported

        String value;
        if(isCanSetPerContainer())
            value = PropertyManager.getCoalecedProperty(propertyUser, c, getCategory(), getName());
        else
            value = PropertyManager.getCoalecedProperty(propertyUser, ContainerManager.getRoot(), getCategory(), getName());

        if(value == null)
            value = getDefaultValue();

        return value;
    }

    public String getValueContainerSpecific(Container c)
    {
        User propertyUser = PropertyManager.SHARED_USER;  // Only shared properties are supported

        String value;
        if (isCanSetPerContainer())
            value = PropertyManager.getProperty(propertyUser, c, getCategory(), getName());
        else
            value = PropertyManager.getProperty(propertyUser, ContainerManager.getRoot(), getCategory(), getName());

        if (value == null)
            value = getDefaultValue();

        return value;
    }

    public static List<Map<String, String>> toOptionMaps(List<Option> options)
    {
        return options.stream().map(Option::toMap).collect(Collectors.toList());
    }

    /**
     * Convenience method to sort an Option list by the displayed field
     * @param options List to sort
     */
    public static void sortOptions(List<Option> options)
    {
        options.sort(Comparator.comparing(o -> o._display));
    }

    /**
     * Simple bean for an option in a combobox. Provided as the js store is expecting properties called "display" and "value"
     * to use as the display and value fields.
     */
    public static class Option
    {
        final String _display;
        final String _value;

        public Option(@NotNull String display, @NotNull String value)
        {
            _display = display;
            _value = value;
        }

        public Map<String, String> toMap()
        {
            Map<String, String> map = new HashMap<>();
            map.put("display", _display);
            map.put("value", _value);
            return map;
        }
    }

    /**
     * Functional interface to provide a dynamic list of options generated at runtime.
     */
    public interface OptionSupplier
    {
        List<Option> get(Container c);
    }
}
