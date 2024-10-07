/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import jakarta.servlet.http.HttpSession;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.security.User;
import org.labkey.api.view.HttpView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * User: Karl Lum
 * Date: Jan 19, 2007
 */
public class PreferenceService
{
    private static final String PREFERENCE_SERVICE_MAP_KEY = "PreferenceServiceMap";
    private static final PreferenceService _instance = new PreferenceService();

    private final Map<String, Map<String, String>> _nullPreferenceMap = Collections.synchronizedMap(new HashMap<>());

    public static PreferenceService get()
    {
        return _instance;
    }

    private PreferenceService()
    {
    }

    public Object getProperty(String name, Container container)
    {
        return getPreferences(container).get(name);
    }

    /** Uses a session-based set of properties if the user is a guest, and a database-backed set if authenticated */
    public String getProperty(String name, User user)
    {
        if (user == null || user.isGuest())
            return getSessionPreferences().get(name);

        return getPreferences(user).get(name);
    }

    /**
     * Returns the container that the specified property is set on. Will return the
     * first container matching the property name in the inheritance chain, from
     * most specific to least specific.
     *
     * @param initialScope - the container of interest (to start the search from)
     */
    public Container findContainerFor(String name, Container initialScope)
    {
        if (initialScope == null)
            throw new IllegalArgumentException("Initial container scope cannot be null");

        if(initialScope.isRoot())
            return initialScope;

        Container c = initialScope;
        while (c != null)
        {
            Map<String, String> p = getReadOnlyPreferences(c);
            if (p != null)
            {
                if (p.containsKey(name))
                    return c;
            }
            if (c.isRoot())
                break;
            c = c.getParent();
        }
        return null;
    }

    public boolean isInherited(String name, Container container)
    {
        if (container == null || container.isRoot())
            return false;

        Container owningContainer = findContainerFor(name, container);
        return (owningContainer != null && !container.equals(owningContainer));
    }

    public void setProperty(String name, String value, Container container)
    {
        WritablePropertyMap prefs = getWritablePreferences(container);
        prefs.put(name, value);

        prefs.save();
        _nullPreferenceMap.remove(container.getId());
    }

    /** Uses a session-based set of properties if the user is a guest, and a database-backed set if authenticated */
    public void setProperty(String name, String value, User user)
    {
        if (user.isGuest())
        {
            Map<String, String> prefs = getSessionPreferences();
            prefs.put(name, value);
        }
        else
        {
            WritablePropertyMap prefs = getWritablePreferences(user);
            prefs.put(name, value);
            prefs.save();
        }
    }

    public void deleteProperty(String name, Container container)
    {
        WritablePropertyMap prefs = getWritablePreferences(container);
        prefs.remove(name);

        prefs.save();
        _nullPreferenceMap.remove(container.getId());
    }

    public void deleteProperty(String name, User user)
    {
        if (user.isGuest())
        {
            Map<String, String> prefs = getSessionPreferences();
            prefs.remove(name);
        }
        else
        {
            WritablePropertyMap prefs = getWritablePreferences(user);
            prefs.remove(name);
            prefs.save();
        }
    }

    private Map<String, String> getPreferences(Container container)
    {
        Stack<Container> stack = new Stack<>();
        stack.push(container);
        while (!container.isRoot())
        {
            stack.push(container.getParent());
            container = container.getParent();
        }

        Map<String, String> prefs = new HashMap<>();
        while (!stack.isEmpty())
        {
            Container c = stack.pop();
            Map<String, String> p = getReadOnlyPreferences(c);
            if (p != null)
            {
                prefs.putAll(p);
            }
        }
        return Collections.unmodifiableMap(prefs);
    }

    private Map<String, String> getSessionPreferences()
    {
        HttpSession session = HttpView.currentRequest().getSession(true);
        Map<String, String> prefs = (Map<String, String>) session.getAttribute(PREFERENCE_SERVICE_MAP_KEY);

        if (null == prefs)
        {
            prefs = new HashMap<>();
            session.setAttribute(PREFERENCE_SERVICE_MAP_KEY, prefs);
        }

        return prefs;
    }

    private PropertyManager.PropertyMap getPreferences(User user)
    {
        return PropertyManager.getProperties(user, ContainerManager.getRoot(), PREFERENCE_SERVICE_MAP_KEY);
    }

    private WritablePropertyMap getWritablePreferences(Container container)
    {
        return PropertyManager.getWritableProperties(container, PREFERENCE_SERVICE_MAP_KEY, true);
    }

    private WritablePropertyMap getWritablePreferences(User user)
    {
        return PropertyManager.getWritableProperties(user, ContainerManager.getRoot(), PREFERENCE_SERVICE_MAP_KEY, true);
    }

    private PropertyManager.PropertyMap getReadOnlyPreferences(Container container)
    {
        String containerId = container.getId();
        if (_nullPreferenceMap.containsKey(containerId))
            return null;
        PropertyManager.PropertyMap prefs = PropertyManager.getProperties(container, PREFERENCE_SERVICE_MAP_KEY);
        if (prefs.isEmpty())
        {
            _nullPreferenceMap.put(containerId, null);
        }
        return prefs;
    }
}
