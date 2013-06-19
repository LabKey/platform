/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import java.util.List;
import java.util.ArrayList;

/**
 * User: Karl Lum
 * Date: Dec 5, 2007
 */
public class GWTChartRenderer implements IsSerializable
{
    private String _type;
    private String _name;

    private List<GWTChartColumn> _columnX = new ArrayList<GWTChartColumn>();

    private List<GWTChartColumn> _columnY = new ArrayList<GWTChartColumn>();

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

    public List<GWTChartColumn> getColumnX()
    {
        return _columnX;
    }

    public void setColumnX(List<GWTChartColumn> columnX)
    {
        _columnX = columnX;
    }

    public List<GWTChartColumn> getColumnY()
    {
        return _columnY;
    }

    public void setColumnY(List<GWTChartColumn> columnY)
    {
        _columnY = columnY;
    }
}
