package org.labkey.plate.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 3:16:30 PM
 */
public class GWTWellGroup implements IsSerializable
{
    private String _type;
    private String _name;
    /**
     * @gwt.typeArgs <org.labkey.plate.designer.client.model.GWTPosition>
     */
    private List _positions;

    /**
     * This field is a Map that must always contain Strings.
     *
     * @gwt.typeArgs <java.lang.String, java.lang.String>
     */
    private Map _properties;

    public GWTWellGroup()
    {
        // no-arg constructor for deserialization
    }

    public GWTWellGroup(String type, String name, List positions, Map properties)
    {
        _type = type;
        _name = name;
        _positions = positions;
        _properties = properties;
    }

    public void removePosition(GWTPosition position)
    {
        _positions.remove(position);
    }

    public void addPosition(GWTPosition position)
    {
        if (!_positions.contains(position))
            _positions.add(position);
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getType()
    {
        return _type;
    }

    public List getPositions()
    {
        return _positions;
    }

    public Map getProperties()
    {
        return _properties;
    }

    public void setProperties(Map properties)
    {
        _properties = properties;
    }
}
