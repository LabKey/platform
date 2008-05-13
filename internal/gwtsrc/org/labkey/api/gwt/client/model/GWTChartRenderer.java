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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 5, 2007
 */
public class GWTChartRenderer implements IsSerializable
{
    private String _type;
    private String _name;

    /**
     * Map of column captions to aliases
     *
     * @gwt.typeArgs <java.lang.String, java.lang.String>
     */
    private Map _columnX;
    /**
     * Map of column captions to aliases
     *
     * @gwt.typeArgs <java.lang.String, java.lang.String>
     */
    private Map _columnY;

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Map getColumnX()
    {
        return _columnX;
    }

    public void setColumnX(Map columnX)
    {
        _columnX = columnX;
    }

    public Map getColumnY()
    {
        return _columnY;
    }

    public void setColumnY(Map columnY)
    {
        _columnY = columnY;
    }
}
