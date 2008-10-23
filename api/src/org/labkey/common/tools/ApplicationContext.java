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

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * User: migra
 * Date: Nov 30, 2004
 * Time: 11:06:23 AM
 */
public class ApplicationContext
    {
    static Logger _log = Logger.getLogger(ApplicationContext.class);

    public interface ApplicationContextProvider
        {
        public void status(String message);
	    public void errorMessage(String message, Throwable t);
	    public void infoMessage(String message);
        public JFrame getFrame();
        public Object getProperty(String propName);
        public void setProperty(String propName, Object value);
        public void addPropertyChangeListener(PropertyChangeListener listener);
        public void addPropertyChangeListener(String name, PropertyChangeListener listener);
        }

    private static ApplicationContextProvider _callback = new DefaultApplicationContext();

    public static void setMessage(String message)
        {
        _callback.status(message);
        }

	public static void errorMessage(String message, Throwable t)
		{
		_callback.errorMessage(message, t);
		}

	public static void infoMessage(String message)
		{
		_callback.infoMessage(message);
		}

    public static JFrame getFrame()
        {
        return _callback.getFrame();
        }

    public static Object getProperty(String propName)
        {
        return _callback.getProperty(propName);
        }

    public static void setProperty(String propName, Object value)
        {
        _callback.setProperty(propName, value);
        }

    public static void addPropertyChangeListener(PropertyChangeListener listener)
        {
        _callback.addPropertyChangeListener(listener);
        }

    public static void addPropertyChangeListener(String name, PropertyChangeListener listener)
        {
        _callback.addPropertyChangeListener(name, listener);
        }

    public static synchronized ApplicationContextProvider getImpl()
        {
        return _callback;
        }

    public static synchronized void setImpl(ApplicationContextProvider callback)
        {
        _callback = callback;
        }

    /**
     * Command line host callback
     */
    public static class DefaultApplicationContext implements ApplicationContextProvider
        {
        PropertyBag _properties = new PropertyBag();

        public void status(String message)
            {
            _log.info(message);
            }

	    public void errorMessage(String message, Throwable t)
		    {
		    if (null != message)
		        System.err.println(message);
		    if (null != t)
			    t.printStackTrace(System.err);
		    }

/*
 * dhmay adding 12/15/2005
 * Prints an informational message, with no associated stack trace
 */
	    public void infoMessage(String message)
		    {
		    if (null != message)
		        System.err.println(message);
		    }

        public JFrame getFrame()
            {
            return null;
            }

        public void setProperty(String name, Object value)
            {
            _properties.put(name, value);
            }


        public Object getProperty(String name)
            {
            return _properties.get(name);
            }


        public void addPropertyChangeListener(PropertyChangeListener listener)
            {
            _properties.addPropertyChangeListener(listener);
            }


        public void addPropertyChangeListener(String name, PropertyChangeListener listener)
            {
            _properties.addPropertyChangeListener(name, listener);
            }

        }
    }
