/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.InputBuilder;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import static org.labkey.api.util.DOM.I;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.cl;

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

    @Override
    protected void renderInternal(PopupMenu model, PrintWriter out) throws Exception
    {
       model.render(out);
    }

    public boolean hasChildren()
    {
        return getNavTree().hasChildren();
    }

    public static void renderTree(NavTree tree, Writer oldWriter) throws IOException
    {
        renderTree(tree, HtmlWriter.of(oldWriter));
    }

    public static HtmlWriter renderTree(NavTree tree, HtmlWriter out)
    {
        if (tree != null)
        {
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

                    LI(
                        cl("dropdown-submenu"),
                        LinkBuilder.simpleLink(HtmlStringBuilder.of(child.getText()).append(DOM.createHtmlFragment(I(cl("fa fa-chevron-right")))))
                            .addClass("subexpand")
                            .addClass("subexpand-icon")
                            .tabindex(0),
                        UL(
                            cl("dropdown-layer-menu"),
                            LI(
                                LinkBuilder.simpleLink(HtmlStringBuilder.of(DOM.createHtmlFragment(I(cl("fa fa-chevron-left")))).append(child.getText()))
                                    .addClass("subcollapse")
                                    .tabindex(0)
                            ),
                            (DOM.Renderable) ret -> {
                                renderTreeDivider(out);
                                renderTree(child, out);
                                return ret;
                            }
                        )
                    ).appendTo(out);
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

        return out;
    }

    protected static void renderTreeItem(NavTree item, @Nullable String cls, HtmlWriter out)
    {
        LI(
            cl(cls).cl(item.isDisabled(), "disabled"),
            (DOM.Renderable) ret -> renderLink(item, cls, out)
        ).appendTo(out);
    }

    protected static void renderTreeDivider(HtmlWriter out)
    {
        LI(cl("divider")).appendTo(out);
    }

    protected static HtmlWriter renderLink(NavTree item, String cls, HtmlWriter out)
    {
        out.write(item.toLinkBuilder(cls));
        return out;
    }

    public static String getMenuFilterItemCls(NavTree tree)
    {
        return PageFlowUtil.filter(tree.getText()).replaceAll("\\s", "-").toLowerCase() + "-item";
    }

    private static void renderMenuFilterInput(String menuFilterItemCls, HtmlWriter out)
    {
        LI(
            cl("menu-filter-input"),
            InputBuilder.text()
                .placeholder("Filter")
                .className("dropdown-menu-filter")
                .addDataAttribute("filter-item", menuFilterItemCls)
        ).appendTo(out);
    }
}
