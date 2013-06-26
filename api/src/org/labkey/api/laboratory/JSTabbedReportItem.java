/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 4/14/13
 * Time: 9:31 AM
 */
public class JSTabbedReportItem extends TabbedReportItem
{
    private String _jsHandler;

    public JSTabbedReportItem(DataProvider provider, String name, String label, String category, String jsHandler)
    {
        super(provider, name, label, category);
        _reportType = "js";
        _jsHandler = jsHandler;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        json.put("jsHandler", _jsHandler);
        return json;
    }
}
