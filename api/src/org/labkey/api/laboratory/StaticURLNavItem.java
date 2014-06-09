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

import org.labkey.api.query.DetailsURL;

/**
 * User: bimber
 * Date: 11/21/12
 * Time: 5:52 PM
 */
public class StaticURLNavItem extends AbstractUrlNavItem
{
    public StaticURLNavItem(DataProvider provider, String label, String itemText, String urlString, LaboratoryService.NavItemCategory itemType, String reportCategory)
    {
        super(provider, label, itemText, urlString, itemType, reportCategory);
    }
}
