/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.api.view.menu;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.HtmlWriter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.cl;

public class FolderMenu extends NavTreeMenu
{
    public FolderMenu(ViewContext context)
    {
        super(context, "folder-nav-menu", null, null, false, false, getNavTree(context));
        setFrame(FrameType.NONE);
    }

    @Nullable
    public static List<NavTree> getNavTree(ViewContext context)
    {
        if (context.getContainer() != null)
        {
            return Collections.singletonList(ContainerManager.getFolderListForUser(ContainerManager.getRoot(), context));
        }

        return null;
    }

    @Override
    protected void renderView(Object model, HtmlWriter out) throws Exception
    {
        NavTree root;
        List<NavTree> elements = getElements();
        ViewContext context = getViewContext();

        // as shown above in getNavTree, if elements is not null, then there will be only one element.
        if (null != elements && (root = elements.getFirst()) != null && root.hasChildren())
        {
            DIV(
                cl("folder-nav"),
                (Renderable) ret -> {
                    renderChildLinks(root, root.getId(), context, out, null);
                    return ret;
                }
            ).appendTo(out);
        }
    }

    private void renderChildLinks(NavTree nav, String rootId,
                                  ViewContext context, HtmlWriter out, @Nullable Boolean shouldExpand)
    {
        UL(
            (Renderable) ret -> {
                for (NavTree child: nav.getChildren())
                {
                    renderLink(child, rootId, context, out, shouldExpand);
                }
                return ret;
            }
        ).appendTo(out);
    }

    private void renderLink(NavTree nav, String rootId,
                            ViewContext context, HtmlWriter out, Boolean shouldExpand)
    {
        // 34137: Support folder path expansion for containers where label != name
        final Container container = ContainerManager.getForId(nav.getId());
        if (container == null)
        {
            renderChildLinks(nav, rootId, context, out, false);
            return;
        }

        final String currentPath = container.getPath().toLowerCase();
        final String containerPath = context.getContainer().getPath().toLowerCase();
        final boolean finalShouldExpand;

        finalShouldExpand = Objects.requireNonNullElseGet(shouldExpand, () -> containerPath.startsWith(currentPath));

        boolean isSelected = finalShouldExpand && currentPath.equals(containerPath);
        boolean hasChildren = nav.hasChildren();

        List<String> liCls = new ArrayList<>();
        liCls.add("folder-tree-node");
        if (hasChildren)
        {
            liCls.add("clbl");
            if (finalShouldExpand)
                liCls.add("expand-folder");
            else
                liCls.add("collapse-folder");
        }

        LI(
            cl(String.join(" ", liCls)),
            SPAN(
                cl(hasChildren, "marked"),
                HtmlString.NBSP   // Safari
            ),
            (Renderable) ret -> {
                String link = nav.getHref();
                if (null != link)
                {
                    LinkBuilder builder = nav.toSimpleLinkBuilder();

                    if (isSelected)
                        builder.addClass("nav-tree-selected").id("folder-target");

                    out.write(builder);
                }
                else
                {
                    SPAN(
                        cl("noread"),
                        nav.getText()
                    ).appendTo(out);
                }

                if (hasChildren)
                {
                    if (finalShouldExpand)
                        renderChildLinks(nav, rootId, context, out, null);
                    else
                        renderChildLinks(nav, rootId, context, out, false);
                }
                return ret;
            }
        ).appendTo(out);
    }
}
