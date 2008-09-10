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

import java.util.List;
import java.util.ArrayList;

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
     * @gwt.typeArgs <org.labkey.api.gwt.client.model.GWTChartColumn>
     */
    private List _columnX = new ArrayList();

    /**
     * @gwt.typeArgs <org.labkey.api.gwt.client.model.GWTChartColumn>
     */
    private List _columnY = new ArrayList();

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

    public List getColumnX()
    {
        return _columnX;
    }

    public void setColumnX(List columnX)
    {
        _columnX = columnX;
    }

    public List getColumnY()
    {
        return _columnY;
    }

    public void setColumnY(List columnY)
    {
        _columnY = columnY;
    }
}
