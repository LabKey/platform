/*
 * Copyright (c) 2005-2009 LabKey Corporation
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
package org.labkey.api.util;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

/**
 * User: brittp
 * Date: Sep 22, 2005
 * Time: 5:49:37 PM
 */
public class ContainerTreeSelected extends ContainerTree
{
    protected Container current;

    public ContainerTreeSelected(String rootPath, User user, Class<? extends Permission> perm, ActionURL url, String purpose)
    {
        super(rootPath, user, perm, url, purpose);
    }
    
    public ContainerTreeSelected(String rootPath, User user, Class<? extends Permission> perm, ActionURL url)
    {
        super(rootPath, user, perm, url);
    }

    public void setCurrent(Container c)
    {
        current = c;
    }

    protected void renderCellContents(StringBuilder html, Container c, ActionURL url)
    {
        if (c.equals(current))
            html.append("<span class=\"labkey-nav-tree-selected\">");

        super.renderCellContents(html, c, url);

        if (c.equals(current))
            html.append("</span>");
    }
}
