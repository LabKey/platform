/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;
import java.awt.*;


/**
 * User: mbellew
 * Date: May 21, 2004
 * Time: 6:08:37 PM
 */
public class PropertyBag
    {
	protected static Logger _log = Logger.getLogger(PropertyBag.class);

    protected HashMap map = new HashMap();
    protected PropertyChangeSupport _propertyChangeSupport;


    public PropertyBag(Object o)
        {
        _propertyChangeSupport = new PropertyChangeSupport(o);
        }


    public PropertyBag()
        {
        _propertyChangeSupport = new PropertyChangeSupport(this);
        }


    public synchronized void put(final String key, final Object value)
        {
        final Object old = map.put(key, value);

        if (old == value)
	        return;
        if (null != old && old.equals(value))
	        return;

        _log.debug("put(" + key + "=" + value + ")");
        _propertyChangeSupport.firePropertyChange(key, old, value);
        }


    public synchronized Object get(String key)
        {
        return map.get(key);
        }


    public synchronized void addPropertyChangeListener(PropertyChangeListener listener)
        {
        _propertyChangeSupport.addPropertyChangeListener(listener);
        }


    public synchronized void addPropertyChangeListener(String name, PropertyChangeListener listener)
        {
        _propertyChangeSupport.addPropertyChangeListener(name, listener);
        }
    }
