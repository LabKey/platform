/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

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

    public static void renderTree(NavTree tree, Writer out) throws IOException
    {
        if (tree == null || !PageFlowUtil.useExperimentalCoreUI())
            return;

        for (NavTree child : tree.getChildren())
        {
            if (child.hasChildren())
            {
                String text = PageFlowUtil.filter(child.getText());

                out.write("<li class=\"dropdown-submenu\">");
                out.write("<a class=\"subexpand\" tabindex=\"0\">" + text + "<i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-circle-left\"></i>" + text + "</a></li>");
                renderTree(child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else if ("-".equals(child.getText()))
                out.write("<li class=\"divider\"></li>");
            else
            {
                out.write("<li");
                if (child.isDisabled())
                    out.write(" class=\"disabled\" ");
                out.write(">");
                out.write("<a");
                if (null != child.getImageCls())
                    out.write(" style=\"padding-left: 0;\"");
                if (null != child.getScript())
                    out.write(" onclick=\"" + PageFlowUtil.filter(child.getScript()) +"\" ");
                if (null != child.getHref())
                    out.write(" href=\"" + child.getHref() + "\"");
                if (null != child.getTarget())
                    out.write(" target=\"" + child.getTarget() + "\"");
                if (null != child.getDescription())
                    out.write(" title=\"" + PageFlowUtil.filter(child.getDescription()) + "\"");
                out.write(" tabindex=\"0\"");
                out.write(">");
                if (null != child.getImageCls())
                    out.write("<i class=\"" + child.getImageCls() + "\"></i>");
                out.write(PageFlowUtil.filter(child.getText()));
                out.write("</a>");
                out.write("</li>");
            }
        }
    }
}
