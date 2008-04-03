package org.labkey.study.plate;

import org.labkey.api.study.PropertySet;
import org.labkey.api.data.Container;

import java.util.*;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 4:43:49 PM
 */
public class PropertySetImpl implements PropertySet
{
    private Map<String, Object> _properties = new HashMap<String, Object>();
    private String _lsid;
    protected Container _container;

    public PropertySetImpl()
    {

    }

    public PropertySetImpl(Container container)
    {
        _container = container;
    }

    public Set<String> getPropertyNames()
    {
        return _properties.keySet();
    }


    public Object getProperty(String name)
    {
        return _properties.get(name);
    }

    public void setProperty(String name, Object value)
    {
        _properties.put(name, value);
    }

    public Map<String, Object> getProperties()
    {
        return Collections.unmodifiableMap(_properties);
    }
    
    public String getLSID()
    {
        return _lsid;
    }

    public void setLsid(String lsid)
    {
        _lsid = lsid;
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }
}
