package org.labkey.api.mbean;

import org.apache.log4j.Logger;

import javax.management.DynamicMBean;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Hashtable;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-02-28
 * Time: 2:07 PM
 */
public class LabKeyManagement
{
    public static ObjectName createName(String type, String name) throws MalformedObjectNameException
    {
        Hashtable<String,String> t = new Hashtable<String,String>();
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
        try
        {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.registerMBean(bean, createName(type,name));
        }
        catch (Exception x)
        {
            Logger.getLogger(LabKeyManagement.class).error(x);
        }
    }


    public static void register(DynamicMBean bean, ObjectName name)
    {
        try
        {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            mbs.registerMBean(bean, name);
        }
        catch (Exception x)
        {
            Logger.getLogger(LabKeyManagement.class).error(x);
        }
    }
}
