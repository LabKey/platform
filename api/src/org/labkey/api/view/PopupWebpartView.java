/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.util.UniqueID;

import java.io.PrintWriter;

/**
 * User: Nick Arnold
 */
public class PopupWebpartView extends PopupMenuView
{
    private boolean visible;

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
        visible = true;
        if (visible)
            super.renderInternal(model, out);
        else
            out.write("&nbsp;");
    }

    public PopupWebpartView(final ViewContext context, NavTree menu)
    {
        if (menu != null)
        {
            menu.setId("webpartMenu" + UniqueID.getRequestScopedUID(context.getRequest()));
            setNavTree(menu);
        }
        
        setAlign(PopupMenu.Align.RIGHT);
        setButtonStyle(PopupMenu.ButtonStyle.TEXT);
    }
}
