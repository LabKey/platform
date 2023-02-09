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

import org.json.old.JSONObject;
import org.labkey.api.data.Container;
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
    String PROPERTY_CATEGORY = "ldk.navItem";
    String VIEW_PROPERTY_CATEGORY = "ldk.navItemDefaultView";

    DataProvider getDataProvider();

    String getName();

    String getLabel();

    String getReportCategory();

    String getRendererName();

    boolean isVisible(Container c, User u);

    boolean getDefaultVisibility(Container c, User u);

    JSONObject toJSON(Container c, User u);

    String getPropertyManagerKey();

    LaboratoryService.NavItemCategory getItemType();
}
