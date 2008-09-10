/*
 * Copyright (c) 2008 LabKey Corporation
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

/*
* User: Karl Lum
* Date: Sep 10, 2008
* Time: 9:49:39 AM
*/
public class GWTChartColumn implements IsSerializable
{
    private String _caption;
    private String _alias;

    public GWTChartColumn()
    {
    }

    public GWTChartColumn(String caption, String alias)
    {
        _caption = caption;
        _alias = alias;
    }

    public String getCaption()
    {
        return _caption;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public String getAlias()
    {
        return _alias;
    }

    public void setAlias(String alias)
    {
        _alias = alias;
    }
}