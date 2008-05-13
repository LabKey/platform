/*
 * Copyright (c) 2007 LabKey Corporation
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

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 20, 2007
 * Time: 9:25:42 PM
 */
public class PopupMenuView extends JspView<NavTree>
{
    public enum Align
    {
        LEFT,
        RIGHT
    }

    private NavTree navTree;
    private String elementId;
    private Align align = Align.LEFT;

    public PopupMenuView(String elementId, NavTree navTree)
    {
        super("/org/labkey/api/view/PopupMenu.jsp", navTree);
        this.elementId = elementId;
        this.navTree = navTree;
    }


    public NavTree getNavTree()
    {
        return navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        this.navTree = navTree;
    }

    public String getElementId()
    {
        return elementId;
    }

    public void setElementId(String elementId)
    {
        this.elementId = elementId;
    }

    public Align getAlign()
    {
        return align;
    }

    public void setAlign(Align align)
    {
        this.align = align;
    }
}
