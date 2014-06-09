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
package org.labkey.api.laboratory.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.laboratory.DataProvider;
import org.labkey.api.laboratory.LaboratoryService;
import org.labkey.api.laboratory.QueryCountNavItem;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

/**

 */
public class AssaySummaryNavItem extends QueryCountNavItem
{
    private ExpProtocol _protocol;

    public AssaySummaryNavItem(DataProvider provider, String schema, String query, LaboratoryService.NavItemCategory itemType, String reportCategory, String label, ExpProtocol p)
    {
        super(provider, schema, query, itemType, reportCategory, label);
        _protocol = p;
    }

    @Override
    protected ActionURL getActionURL(Container c, User u)
    {
        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, _protocol);
    }
}
