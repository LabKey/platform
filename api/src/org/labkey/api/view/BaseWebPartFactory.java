/*
 * Copyright (c) 2005-2017 LabKey Corporation
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

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;
import org.labkey.api.view.Portal.WebPart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Nov 1, 2005
 * Time: 5:09:48 PM
 */
public abstract class BaseWebPartFactory implements WebPartFactory
{
    private static final Logger LOG = Logger.getLogger(Portal.class);

    private final boolean _editable;
    private final boolean _showCustomizeOnInsert;

    private String _name;
    private Module _module = null;
    private List<String> _legacyNames = Collections.emptyList();

    protected Set<String> _allowableLocations;

    public BaseWebPartFactory(String name, boolean isEditable, boolean showCustomizeOnInsert, @NotNull String defaultLocation, String... additionalLocations)
    {
        if (!isEditable && showCustomizeOnInsert)
            throw new IllegalArgumentException("CustomizeOnInsert is only valid when web part is editable.");
        _name = name;
        _showCustomizeOnInsert = showCustomizeOnInsert;
        _editable = isEditable;
        Set<String> locations = new LinkedHashSet<>();
        locations.add(defaultLocation);
        locations.addAll(Arrays.asList(additionalLocations));
        if (locations.contains(null))
        {
            throw new IllegalArgumentException("Can't add a null allowable location");
        }
        _allowableLocations = Collections.unmodifiableSet(locations);
    }

    public BaseWebPartFactory(String name, boolean isEditable, boolean showCustomizeOnInsert)
    {
        this(name, isEditable, showCustomizeOnInsert, WebPartFactory.LOCATION_BODY);
    }

    public BaseWebPartFactory(String name, @NotNull String defaultLocation, String... additionalLocations)
    {
        this(name, false, false, defaultLocation, additionalLocations);
    }

    public BaseWebPartFactory(String name)
    {
        this(name, WebPartFactory.LOCATION_BODY);
    }

    public String getName()
    {
        return _name;
    }

    @Override
    public String getDisplayName(Container container, String location)
    {
        return getName();
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Set<String> getAllowableLocations()
    {
        return _allowableLocations;
    }

    public String getDefaultLocation()
    {
        return _allowableLocations.iterator().next();
    }

    public HttpView getEditView(WebPart webPart, ViewContext context)
    {
        return null;
    }

    public WebPart createWebPart()
    {
        return createWebPart(getDefaultLocation());
    }

    public WebPart createWebPart(String location)
    {
        WebPart part = new WebPart();
        part.setLocation(location);
        part.setName(getName());
        return part;
    }

    protected void populateProperties(WebPartView view, Map<String, String> properties)
    {
        for (Map.Entry<String, String> entry : properties.entrySet())
        {
            if (PropertyUtils.isWriteable(view, entry.getKey()))
            {
                try
                {
                    BeanUtils.setProperty(view, entry.getKey(), entry.getValue());
                }
                catch (Exception e)
                {
                    // Unfortunately, we have to catch Exception here, since BeanUtils throws RuntimeExceptions
                    // for various failures.
                    LOG.error("Couldn't set property " + entry.getKey() + " to value " + entry.getValue(), e);
                }
            }
            else
                view.addObject(entry.getKey(), entry.getValue());
        }
    }

    public void addLegacyNames(String... names)
    {
        List<String> newNames = new ArrayList<>(_legacyNames);
        newNames.addAll(Arrays.asList(names));
        _legacyNames = Collections.unmodifiableList(newNames);
    }

    public boolean isEditable()
    {
        return _editable;
    }

    public boolean showCustomizeOnInsert()
    {
        return _showCustomizeOnInsert;
    }

    public Module getModule()
    {
        if (_module == null)
            throw new IllegalStateException("Module has not been set.");
        return _module;
    }

    public void setModule(Module module)
    {
        if (_module != null && !_module.equals(module))
            throw new IllegalStateException("WebPart " + getClass().getName() + " (" + getName() + "): Module has already been set to " + _module.getName() + "; attempt to set to " + module.getName() + " failed.");
        _module = module;
    }

    public List<String> getLegacyNames()
    {
        return _legacyNames;
    }

    public boolean isAvailable(Container c, String location)
    {
        if (!getAllowableLocations().contains(location))
        {
            return false;
        }
        for (WebPart webPart : c.getFolderType().getPreferredWebParts())
        {
            if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
            {
                return true;
            }
        }
        for (WebPart webPart : c.getFolderType().getRequiredWebParts())
        {
            if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
            {
                return true;
            }
        }
        return FolderType.NONE.equals(c.getFolderType()) || c.getActiveModules().contains(getModule());
    }

    @Override
    public Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        return propertyMap;
    }

    @Override
    public Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap)
    {
        return propertyMap;
    }

    @Override
    public boolean includeInExport(ImportContext ctx, WebPart webPart)
    {
        return true;
    }
}
