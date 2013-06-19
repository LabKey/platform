/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package gwt.client.org.labkey.study.chart.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;

/**
 * User: Karl Lum
 * Date: Dec 10, 2007
 */
public class GWTPair implements IsSerializable
{
    private String _key;
    private String _value;

    public GWTPair(){}
    public GWTPair(String key, String value)
    {
        _key = key;
        _value = value;
    }

    public String getKey()
    {
        return _key;
    }

    public void setKey(String key)
    {
        _key = key;
    }

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }
}
