/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
package org.labkey.api.action;

import org.json.JSONObject;

import java.util.Map;

/**
 * User: jgarms
 * Date: Aug 13, 2008
 * Time: 3:39:02 PM
 */
public class SimpleApiJsonForm implements CustomApiForm
{
    protected JSONObject json;

    public void bindProperties(Map<String,Object> properties)
    {
        if (properties instanceof JSONObject)
            json = (JSONObject)properties;
        else
            json = new JSONObject(properties);
    }

    public JSONObject getJsonObject()
    {
        return json;
    }
}
