/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.admin;

import org.labkey.api.view.NavTree;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: adam
 * Date: May 14, 2008
 * Time: 1:54:57 PM
 */
public class AdminConsole
{
    private static ActionURL adminURL = PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();

    public static NavTree appendAdminNavTrail(NavTree root, String childTitle)
    {
        root.addChild("Admin Console", adminURL).addChild(childTitle);
        return root;
    }
}
