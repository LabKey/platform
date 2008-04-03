package org.labkey.api.exp.property;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.util.MemTracker;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.Map;
import java.util.LinkedHashMap;


public class SystemProperty
{
    static final private Logger _log = Logger.getLogger(SystemProperty.class);
    static private Map<String, SystemProperty> _systemProperties = new LinkedHashMap();
    static private boolean _registered = false;

    private String _propertyURI;
    private PropertyType _type;
    private String _name;
    private PropertyDescriptor _pd;

    public SystemProperty(String propertyURI, PropertyType type, String name)
    {
        if (_registered)
            throw new IllegalStateException("System properties can only be registered at startup");
        if (_systemProperties.containsKey(propertyURI))
            throw new IllegalArgumentException("System property " + propertyURI + " is already registered.");
        _systemProperties.put(propertyURI, this);
        _propertyURI = propertyURI;
        _type = type;
        _name = name;
    }

    public SystemProperty(String propertyURI, PropertyType type)
    {
        this(propertyURI, type, null);
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        return _pd;
    }

    private void register() throws SQLException
    {
        _pd = OntologyManager.getPropertyDescriptor(_propertyURI, getContainer());
        assert MemTracker.remove(_pd);  // these are globals now, so don't track
        if (_pd == null)
        {
            PropertyDescriptor pd = constructPropertyDescriptor();
            _pd = OntologyManager.insertPropertyDescriptor(pd);
            assert MemTracker.remove(_pd);
        }
    }

    protected PropertyDescriptor constructPropertyDescriptor()
    {
        return new PropertyDescriptor(_propertyURI, _type.getTypeUri(), _name, getContainer());
    }

    static public void registerProperties()
    {
        if (_registered)
            throw new IllegalStateException("System properties have already been registered");
        for (SystemProperty property : _systemProperties.values())
        {
            try
            {
                property.register();
            }
            catch (SQLException e)
            {
                _log.error("Error", e);
            }
        }
        _registered = true;
    }

    protected Container getContainer()
    {
        return ContainerManager.getSharedContainer();
    }
}
