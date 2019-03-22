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

import org.apache.log4j.Logger;

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
    public static ObjectName createName(String type, String name) throws MalformedObjectNameException
    {
        Hashtable<String,String> t = new Hashtable<>();
        t.put("type", type);
        name = name.replace(": ", "-").replace(':','-');
        t.put("name", name);
        try
        {
            return new ObjectName("LabKey", t);
        }
        catch (MalformedObjectNameException x)
        {
            t.put("type", ObjectName.quote(type));
            t.put("name", ObjectName.quote(name));
            return new ObjectName("LabKey", t);
        }
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
            Logger.getLogger(LabKeyManagement.class).error("error registering mbean : " + String.valueOf(name), x);
        }
        register(bean, oname);
    }


    static final Object _lock = new Object();

    public static void register(DynamicMBean bean, ObjectName name)
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
            Logger.getLogger(LabKeyManagement.class).error("error registering mbean : " + String.valueOf(name), x);
        }
    }
}
