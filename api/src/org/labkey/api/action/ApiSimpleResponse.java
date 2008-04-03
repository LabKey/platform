/*
 * Copyright (c) 2007 LabKey Software Foundation
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

import java.util.HashMap;
import java.util.Map;

/**
 * Use this for simple responses from Api actions.
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: Feb 13, 2008
 * Time: 4:44:39 PM
 */
public class ApiSimpleResponse extends HashMap<String,Object> implements ApiResponse
{
    public ApiSimpleResponse()
    {
    }

    public ApiSimpleResponse(String key, Object value)
    {
        put(key, value);
    }

    public ApiSimpleResponse(String key, int value)
    {
        put(key, Integer.valueOf(value));
    }

    public ApiSimpleResponse(String key, boolean value)
    {
        put(key, Boolean.valueOf(value));
    }

    public Map<String, Object> getProperties()
    {
        return this;
    }
}
