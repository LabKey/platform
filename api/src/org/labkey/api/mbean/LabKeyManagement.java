/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.api.mbean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;

import javax.management.DynamicMBean;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;

/**
 * User: matthewb
 * Date: 2012-02-28
 * Time: 2:07 PM
 */
public class LabKeyManagement
{

    public static final Logger LOG = LogHelper.getLogger(LabKeyManagement.class, "Exports LabKey information via JMX");

    public static ObjectName createName(String type, String name) throws MalformedObjectNameException
    {
        Hashtable<String,String> t = new Hashtable<>();
        name = name.replace(": ", "-").replace(':','-');

        name = replaceSpecialObjectNameCharacters(name);
        type = replaceSpecialObjectNameCharacters(type);

        t.put("type", type);
        t.put("name", name);

        return new ObjectName("LabKey", t);
    }

    /** Issue 47330 - steer clear of potentially problematic object names.
     * Special character lists compliments of ObjectName.quote(), plus comma, which may be separately problematic */
    private static String replaceSpecialObjectNameCharacters(String s)
    {
        final StringBuilder buf = new StringBuilder();
        final int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            c = switch (c)
            {
                case '\n', '\\', '\"', '*', '?', ',' -> '_';
                default -> c;
            };
            buf.append(c);
        }
        return buf.toString();
    }

    public static void register(DynamicMBean bean, String type, String name)
    {
        ObjectName oname=null;
        try
        {
            oname = createName(type,name);
        }
        catch (Exception x)
        {
            LOG.error("error registering mbean : " + name, x);
        }
        register(bean, oname);
    }


    static final Object _lock = new Object();

    public static void register(DynamicMBean bean, @Nullable ObjectName name)
    {
        try
        {
            synchronized (_lock)
            {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                try
                {
                    mbs.unregisterMBean(name);
                }
                catch (InstanceNotFoundException x)
                {
                    /* */
                }
                mbs.registerMBean(bean, name);
            }
        }
        catch (Exception x)
        {
            LOG.error("error registering mbean : " + name, x);
        }
    }
}
