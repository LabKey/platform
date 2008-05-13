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
