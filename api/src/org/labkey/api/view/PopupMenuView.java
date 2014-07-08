/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import java.io.PrintWriter;

/**
 * User: Mark Igra
 * Date: Jun 20, 2007
 * Time: 9:25:42 PM
 */
public class PopupMenuView extends HttpView<PopupMenu>
{
    public PopupMenuView()
    {
        super(new PopupMenu());
    }
    
    public PopupMenuView(NavTree navTree)
    {
        this(new PopupMenu(navTree));
    }

    public PopupMenuView(PopupMenu menu)
    {
        super(menu);
    }

    public NavTree getNavTree()
    {
        return getModelBean().getNavTree();
    }

    public void setNavTree(NavTree navTree)
    {
        getModelBean().setNavTree(navTree);
    }

    public PopupMenu.Align getAlign()
    {
        return getModelBean().getAlign();
    }

    public void setAlign(PopupMenu.Align align)
    {
        getModelBean().setAlign(align);
    }

    public PopupMenu.ButtonStyle getButtonStyle()
    {
        return getModelBean().getButtonStyle();
    }

    public void setButtonStyle(PopupMenu.ButtonStyle buttonStyle)
    {
        getModelBean().setButtonStyle(buttonStyle);
    }

    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
       model.render(out);
    }

    public boolean hasChildren()
    {
        return getNavTree().hasChildren();
    }
}
