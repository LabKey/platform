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

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.ldk.NavItem;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: bimber
 * Date: 5/5/13
 * Time: 9:41 AM
 */
public class ReportItem extends SimpleQueryNavItem
{
    public ReportItem(DataProvider provider, String schema, String query, String category, String label)
    {
        super(provider, schema, query, category, label);
    }

    public ReportItem(DataProvider provider, String schema, String query, String category)
    {
        super(provider, schema, query, category);
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        TabbedReportItem.applyOverrides(this, c, json);

        return json;
    }
}
