/*
 * Copyright (c) 2005-2013 LabKey Corporation
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
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.Module;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * User: brittp
 * Date: Nov 1, 2005
 * Time: 5:09:48 PM
 */
public abstract class BaseWebPartFactory implements WebPartFactory
{
    private static Logger _log = Logger.getLogger(Portal.class);

    String name;
    protected String defaultLocation;
    Module module = null;
    private boolean editable;
    private boolean showCustomizeOnInsert;
    private List<String> _legacyNames = Collections.emptyList();

    public BaseWebPartFactory(String name, @Nullable String defaultLocation, boolean isEditable, boolean showCustomizeOnInsert)
    {
        if (!isEditable && showCustomizeOnInsert)
            throw new IllegalArgumentException("CustomizeOnInsert is only valid when web part is editable.");
        this.name = name;
        this.showCustomizeOnInsert = showCustomizeOnInsert;
        this.editable = isEditable;
        this.defaultLocation = null == defaultLocation ? HttpView.BODY : defaultLocation;
    }

    public BaseWebPartFactory(String name, @Nullable String defaultLocation)
    {
        this(name, defaultLocation, false, false);
    }

    public BaseWebPartFactory(String name)
    {
        this(name, null);
    }

    public String getName()
    {
        return name;
    }

    @Override
    public String getDisplayName(Container container, String location)
    {
        return getName();
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDefaultLocation()
    {
        return defaultLocation;
    }


    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        return null;
    }

    public Portal.WebPart createWebPart()
    {
        return createWebPart(defaultLocation);
    }

    public Portal.WebPart createWebPart(String location)
    {
        Portal.WebPart part = new Portal.WebPart();
        part.setLocation(location);
        part.setName(getName());
        return part;
    }

    protected void populateProperties(WebPartView view, Map<String, String> properties) throws IllegalAccessException, InvocationTargetException
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
                    _log.error("Couldn't set property " + entry.getKey() + " to value " + entry.getValue(), e);
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
        return editable;
    }

    public boolean showCustomizeOnInsert()
    {
        return showCustomizeOnInsert;
    }

    public Module getModule()
    {
        if (module == null)
            throw new IllegalStateException("Module has not been set.");
        return module;
    }

    public void setModule(Module module)
    {
        if (this.module != null)
            throw new IllegalStateException("Module has already been set.");
        this.module = module;
    }

    public List<String> getLegacyNames()
    {
        return _legacyNames;
    }

    public boolean isAvailable(Container c, String location)
    {
        if (!location.equals(getDefaultLocation()))
        {
            return false;
        }
        if (c.getFolderType() != null)
        {
            for (Portal.WebPart webPart : c.getFolderType().getPreferredWebParts())
            {
                if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
                {
                    return true;
                }
            }
            for (Portal.WebPart webPart : c.getFolderType().getRequiredWebParts())
            {
                if (getName().equals(webPart.getName()) || getLegacyNames().contains(webPart.getName()))
                {
                    return true;
                }
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
}
