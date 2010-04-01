/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client.model;

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
    private List<GWTWellGroup> _groups = new ArrayList<GWTWellGroup>();

    private List<String> _groupTypes;
    private Map<String, Object> _plateProperties = new HashMap<String, Object>();
    private transient Map<GWTPosition, Set<GWTWellGroup>> _positionToGroups = null;
    private transient Map<String, List<GWTWellGroup>> _typeToGroups = null;

    public GWTPlate()
    {
    }

    public GWTPlate(String name, String type, int rows, int cols, List<String> groupTypes)
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
            _positionToGroups = new HashMap<GWTPosition, Set<GWTWellGroup>>();
            for (GWTWellGroup group : _groups)
            {
                for (GWTPosition position : group.getPositions())
                {
                    Set<GWTWellGroup> groupList = _positionToGroups.get(position);
                    if (groupList == null)
                    {
                        groupList = new HashSet<GWTWellGroup>();
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
            _typeToGroups = new HashMap<String, List<GWTWellGroup>>();
            for (GWTWellGroup group : _groups)
            {
                List<GWTWellGroup> groupList = _typeToGroups.get(group.getType());
                if (groupList == null)
                {
                    groupList = new ArrayList<GWTWellGroup>();
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

    public List<GWTWellGroup> getGroups()
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

    public void setGroups(List<GWTWellGroup> groups)
    {
        _groups = groups;
    }

    public void setPlateProperties(Map<String, Object> plateProperties)
    {
        _plateProperties = plateProperties;
    }

    public Map<String, Object> getPlateProperties()
    {
        return _plateProperties;
    }

    public String getType()
    {
        return _type;
    }
}
