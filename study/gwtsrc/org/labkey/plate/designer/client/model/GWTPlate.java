package org.labkey.plate.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.*;

/**
 * User: brittp
 * Date: Feb 2, 2007
 * Time: 3:22:43 PM
 */
public class GWTPlate implements IsSerializable
{
    private String _name;
    private String _type;
    private int _rows;
    private int _cols;
    /**
     * @gwt.typeArgs <org.labkey.plate.designer.client.model.GWTWellGroup>
     */
    private List _groups = new ArrayList();

    /**
     * @gwt.typeArgs <java.lang.String>
     */
    private List _groupTypes;
    /**
     * @gwt.typeArgs <java.lang.String,java.lang.String>
     */
    private Map _plateProperties = new HashMap();
    private transient Map _positionToGroups = null;
    private transient Map _typeToGroups = null;

    public GWTPlate()
    {
    }

    public GWTPlate(String name, String type, int rows, int cols, List groupTypes)
    {
        _name = name;
        _type = type;
        _rows = rows;
        _cols = cols;
        _groupTypes = groupTypes;
    }

    public Map getPositionToGroupsMap()
    {
        if (_positionToGroups == null)
        {
            _positionToGroups = new HashMap();
            Iterator groupIterator = _groups.iterator();
            while (groupIterator.hasNext())
            {
                GWTWellGroup group = (GWTWellGroup) groupIterator.next();
                Iterator positionIterator = group.getPositions().iterator();
                while (positionIterator.hasNext())
                {
                    GWTPosition position = (GWTPosition) positionIterator.next();
                    Set groupList = (Set) _positionToGroups.get(position);
                    if (groupList == null)
                    {
                        groupList = new HashSet();
                        _positionToGroups.put(position, groupList);
                    }
                    groupList.add(group);
                }
            }
        }
        return _positionToGroups;
    }

    public Map getTypeToGroupsMap()
    {
        if (_typeToGroups == null)
        {
            _typeToGroups = new HashMap();
            Iterator groupIterator = _groups.iterator();
            while (groupIterator.hasNext())
            {
                GWTWellGroup group = (GWTWellGroup) groupIterator.next();
                List groupList = (List) _typeToGroups.get(group.getType());
                if (groupList == null)
                {
                    groupList = new ArrayList();
                    _typeToGroups.put(group.getType(), groupList);
                }
                groupList.add(group);
            }
        }
        return _typeToGroups;
    }

    public void removeGroup(GWTWellGroup group)
    {
        _groups.remove(group);
        _typeToGroups = null;
        _positionToGroups = null;
    }

    public void addGroup(GWTWellGroup group)
    {
        _groups.add(group);
        _typeToGroups = null;
        _positionToGroups = null;
    }

    public int getCols()
    {
        return _cols;
    }

    public List getGroups()
    {
        return _groups;
    }

    public int getRows()
    {
        return _rows;
    }

    public List getGroupTypes()
    {
        return _groupTypes;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public void setGroups(List groups)
    {
        _groups = groups;
    }

    public void setPlateProperties(Map plateProperties)
    {
        _plateProperties = plateProperties;
    }

    public Map getPlateProperties()
    {
        return _plateProperties;
    }

    public String getType()
    {
        return _type;
    }
}
