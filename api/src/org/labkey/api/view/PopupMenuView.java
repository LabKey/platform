/*
 * Copyright (c) 2007-2017 LabKey Corporation
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
        if (tree == null)
            return;

        // These flags act as a trimming boundaries for menu separators. They are used to prevent
        // "empty" menu items between separators as well as prevent beginning or ending with a separator
        boolean hasNonSeparatorItem = false;
        boolean lastIsSeparator = false;

        String treeItemCls = null;

        for (NavTree child : tree.getChildren())
        {
            // check if this is the first child with the menu filter cls, if so add the filter input item
            if (child.getMenuFilterItemCls() != null)
            {
                if (treeItemCls == null || !treeItemCls.equals(child.getMenuFilterItemCls()))
                {
                    treeItemCls = child.getMenuFilterItemCls();
                    renderMenuFilterInput(treeItemCls, out);
                }
            }
            else
            {
                // clear the cls to stop the menu filter section, note that this means that menu filter items
                // must be consecutively placed in the menu in order to work with the filter input
                treeItemCls = null;
            }

            if (child.hasChildren())
            {
                if (lastIsSeparator)
                {
                    lastIsSeparator = false;
                    renderTreeDivider(out);
                }

                hasNonSeparatorItem = true;
                String text = PageFlowUtil.filter(child.getText());

                out.write("<li class=\"dropdown-submenu\">");
                out.write("<a class=\"subexpand subexpand-icon\" tabindex=\"0\">" + text + "<i class=\"fa fa-chevron-right\"></i></a>");
                out.write("<ul class=\"dropdown-layer-menu\">");
                out.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
                renderTreeDivider(out);
                renderTree(child, out);
                out.write("</ul>");
                out.write("</li>");
            }
            else if ("-".equals(child.getText()))
            {
                if (hasNonSeparatorItem)
                    lastIsSeparator = true;
            }
            else
            {
                if (lastIsSeparator)
                {
                    lastIsSeparator = false;
                    renderTreeDivider(out);
                }

                hasNonSeparatorItem = true;
                renderTreeItem(child, treeItemCls, out);
            }
        }
    }

    protected static void renderTreeItem(NavTree item, String cls, Writer out) throws IOException
    {
        out.write("<li");
        if (item.isDisabled())
            cls = cls != null ? cls + " disabled" : "disabled";
        if (cls != null)
            out.write(" class=\"" + cls + "\"");
        out.write(">");
        renderLink(item, null, out);
        out.write("</li>");
    }

    protected static void renderTreeDivider(Writer out) throws IOException
    {
        out.write("<li class=\"divider\"></li>");
    }

    protected static void renderLink(NavTree item, String cls, Writer out) throws IOException
    {
        // if the item is "selected" and doesn't have have an image cls to use, provide our default
        String itemImageCls = item.getImageCls();
        if (item.isSelected() && null == itemImageCls)
            itemImageCls = "fa fa-check-square-o";

        String styleStr = "";
        if (null != itemImageCls)
            styleStr += "padding-left: 0;";
        if (item.isStrong())
            styleStr += "font-weight: bold;";
        if (item.isEmphasis())
            styleStr += "font-style: italic;";

        out.write("<a");
        if (null != cls)
            out.write(" class=\"" + cls + "\"");
        if (null != item.getScript())
            out.write(" onclick=\"" + PageFlowUtil.filter(item.getScript()) +"\" ");
        if (null != item.getHref())
            out.write(" href=\"" + item.getHref() + "\"");
        else
            out.write(" href=\"javascript:void(0);\"");
        if (null != item.getTarget())
            out.write(" target=\"" + item.getTarget() + "\"");
        if (null != item.getDescription())
            out.write(" title=\"" + PageFlowUtil.filter(item.getDescription()) + "\"");
        if (item.isDisabled())
            out.write(" disabled");
        out.write(" tabindex=\"0\"");
        out.write(" style=\"" + styleStr + "\"");
        out.write(">");
        if (null != itemImageCls)
            out.write("<i class=\"" + itemImageCls + "\"></i>");
        out.write(PageFlowUtil.filter(item.getText()));
        out.write("</a>");
    }

    public static String getMenuFilterItemCls(NavTree tree)
    {
        return PageFlowUtil.filter(tree.getText()).replaceAll("\\s", "-").toLowerCase() + "-item";
    }

    private static void renderMenuFilterInput(String menuFilterItemCls, Writer out) throws IOException
    {
        out.write("<li class=\"menu-filter-input\">");
        out.write("<input type=\"text\" placeholder=\"Filter\" class=\"dropdown-menu-filter\" data-filter-item=\"" + menuFilterItemCls + "\"/>");
        out.write("</li>");
    }
}
