/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

/**
 * User: bimber
 * Date: 11/21/12
 * Time: 5:52 PM
 */
public class DetailsUrlWithoutLabelNavItem extends AbstractUrlNavItem
{
    public DetailsUrlWithoutLabelNavItem(DataProvider provider, String itemText, DetailsURL itemUrl, LaboratoryService.NavItemCategory itemType, String reportCategory)
    {
        super(provider, itemText, null, itemUrl, itemType, reportCategory);
    }

    public static DetailsUrlWithoutLabelNavItem createForQuery(DataProvider provider, User u, Container c, String schema, String query, String label, LaboratoryService.NavItemCategory itemType, String reportCategory)
    {
        ActionURL url = QueryService.get().urlFor(u, c, QueryAction.executeQuery, schema, query);

        return new DetailsUrlWithoutLabelNavItem(provider, label, new DetailsURL(url), itemType, reportCategory);
    }
}
