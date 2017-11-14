/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.api.view;

import org.json.JSONObject;
import org.labkey.api.util.Pair;

/**
 * Captures options for auto-complete actions requested from the browser (typically based on partial input).
 * User: adam
 * Date: Sep 23, 2007
 */
public final class AjaxCompletion extends Pair<String, String>
{
    public AjaxCompletion(String display, String insert)
    {
        super(display,insert);
    }

    public AjaxCompletion(String displayAndInsert)
    {
        this(displayAndInsert, displayAndInsert);
    }

    public String getDisplayText()
    {
        return getKey();
    }

    public String getInsertionText()
    {
        return getValue();
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();

        json.put("name", getDisplayText());
        json.put("value", getInsertionText());

        return json;
    }
}