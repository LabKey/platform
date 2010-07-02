/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.jsp;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

abstract public class ContextPage extends JspBase
{
    private ViewContext _context;
    public ContextPage()
    {
    }

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public ViewContext getViewContext()
    {
        return _context;
    }

    public ActionURL getActionURL()
    {
        return _context.getActionURL();
    }

    public Container getContainer()
    {
        return _context.getContainer();
    }

    public User getUser()
    {
        return _context.getUser();
    }
}
