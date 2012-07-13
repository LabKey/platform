/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import com.google.gwt.user.client.Window;
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
    private Map<String, Set<GWTWellGroup>> _groups = new HashMap<String, Set<GWTWellGroup>>();
    private boolean _showWarningPanel;

    private List<String> _groupTypes;
    private Map<String, Object> _plateProperties = new HashMap<String, Object>();
    private Map<String, List<String>> _typesToDefaultGroups = new HashMap<String, List<String>>();
    private transient Map<GWTPosition, Set<GWTWellGroup>> _positionToGroups = null;

    public GWTPlate()
    {
    }

    public GWTPlate(String name, String type, int rows, int cols, List<String> groupTypes, boolean showWarningPanel)
    {
        _name = name;
        _type = type;
        _rows = rows;
        _cols = cols;
        _groupTypes = groupTypes;
        _showWarningPanel = showWarningPanel;
    }

    public Map getPositionToGroupsMap()
    {
        if (_positionToGroups == null)
        {
            _positionToGroups = new HashMap<GWTPosition, Set<GWTWellGroup>>();
            for (Set<GWTWellGroup> typeGroups : _groups.values())
            {
                for (GWTWellGroup group : typeGroups)
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
        }
        return _positionToGroups;
    }

    public Map<String, Set<GWTWellGroup>> getTypeToGroupsMap()
    {
        return _groups;
    }

    public void removeGroup(GWTWellGroup group)
    {
        if (_groups.containsKey(group.getType()))
        {
            _groups.get(group.getType()).remove(group);
            _positionToGroups = null;
        }
    }

    public boolean addGroup(GWTWellGroup group)
    {
        if (!_groups.containsKey(group.getType()))
            _groups.put(group.getType(), new HashSet<GWTWellGroup>());

        Set<GWTWellGroup> g = _groups.get(group.getType());
        if (!g.contains(group))
        {
            g.add(group);
            _positionToGroups = null;

            return true;
        }
        else
        {
            Window.alert("Group : " + group.getName() + " already exists.");
            return false;
        }
    }

    public int getCols()
    {
        return _cols;
    }

    public List<GWTWellGroup> getGroups()
    {
        List<GWTWellGroup> groups = new ArrayList<GWTWellGroup>();

        for (Set<GWTWellGroup> typeGroups : _groups.values())
            groups.addAll(typeGroups);

        return groups;
    }

    public int getRows()
    {
        return _rows;
    }

    public List<String> getGroupTypes()
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
        for (GWTWellGroup group : groups)
            addGroup(group);
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

    public Map<String, List<String>> getTypesToDefaultGroups()
    {
        return _typesToDefaultGroups;
    }

    public void setTypesToDefaultGroups(Map<String, List<String>> typesToDefaultGroups)
    {
        _typesToDefaultGroups = typesToDefaultGroups;
    }

    public boolean isShowWarningPanel()
    {
        return _showWarningPanel;
    }

    public void setShowWarningPanel(boolean showWarningPanel)
    {
        _showWarningPanel = showWarningPanel;
    }
}
