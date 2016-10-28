/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 10/1/12
 * Time: 8:44 AM
 */

/**
 * Experimental.  This describes
 */
public interface NavItem
{
    public static final String PROPERTY_CATEGORY = "ldk.navItem";
    public static final String VIEW_PROPERTY_CATEGORY = "ldk.navItemDefaultView";

    public DataProvider getDataProvider();

    public String getName();

    public String getLabel();

    public String getReportCategory();

    public String getRendererName();

    public boolean isVisible(Container c, User u);

    public boolean getDefaultVisibility(Container c, User u);

    public JSONObject toJSON(Container c, User u);

    public String getPropertyManagerKey();

    public LaboratoryService.NavItemCategory getItemType();
}
