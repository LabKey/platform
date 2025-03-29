/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.util.DOM;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.Input;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.Map;

import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.createHtmlFragment;

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

    @SneakyThrows
    public static HtmlWriter renderTree(NavTree tree, HtmlWriter out)
    {
        renderTree(tree, out, out.unwrap());
        return out;
    }

    public static HtmlWriter renderTree(NavTree tree, Writer oldWriter) throws IOException
    {
        HtmlWriter out = HtmlWriter.of(oldWriter);
        renderTree(tree, out, oldWriter);
        return out;
    }

    private static void renderTree(NavTree tree, HtmlWriter out, Writer oldWriter) throws IOException
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
                    renderTreeDivider(oldWriter);
                }

                hasNonSeparatorItem = true;
                String text = PageFlowUtil.filter(child.getText());

                oldWriter.write("<li class=\"dropdown-submenu\">");
                oldWriter.write("<a class=\"subexpand subexpand-icon\" tabindex=\"0\">" + text + "<i class=\"fa fa-chevron-right\"></i></a>");
                oldWriter.write("<ul class=\"dropdown-layer-menu\">");
                oldWriter.write("<li><a class=\"subcollapse\" tabindex=\"0\"><i class=\"fa fa-chevron-left\"></i>" + text + "</a></li>");
                renderTreeDivider(oldWriter);
                renderTree(child, oldWriter);
                oldWriter.write("</ul>");
                oldWriter.write("</li>");
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
                    renderTreeDivider(oldWriter);
                }

                hasNonSeparatorItem = true;
                renderTreeItem(child, treeItemCls, oldWriter);
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

    protected static void renderTreeDivider(HtmlWriter out)
    {
        LI(cl("divider")).appendTo(out);
    }

    // TODO: Delegate to LinkBuilder instead of replicating all of its rendering code here.
    // We can't call item.toLinkBuilder() because we may need to replace the HTML, and there's no setter for that.
    protected static void renderLink(NavTree item, String cls, Writer oldWriter) throws IOException
    {
        // if the item is "selected" and doesn't have an image cls to use, provide our default
        String itemImageCls = item.getImageCls();
        if (item.isSelected() && null == itemImageCls)
            itemImageCls = "fa fa-check-square-o";
        HtmlStringBuilder html = HtmlStringBuilder.of();
        if (null != itemImageCls)
            html.append(createHtmlFragment(DOM.I(cl(itemImageCls))));
        html.append(item.getText());

        String styleStr = "";
        if (null != itemImageCls)
            styleStr += "padding-left: 0;";
        if (item.isStrong())
            styleStr += "font-weight: bold;";
        if (item.isEmphasis())
            styleStr += "font-style: italic;";

        // NOTE: nofollow is not recommended as way to avoid crawling internal links
        // instead let's use an onclick handler to "hide" the link
        String dataQuery = null;
        String href = item.getHref();
        var config = HttpView.currentPageConfig();

        if (null != href && null == item.getScript() && !item.isPost())
        {
            try
            {
                URLHelper url = new URLHelper(href);
                boolean isLocal = null == url.getHost() && null == url.getScheme() && -1 == url.getPort();
                if (isLocal)
                {
                    var context = HttpView.currentContext();

                    // separate the path (into href) and query/fragment (into dataQuery) portions of URL
                    var fragment = url.getFragment();
                    url.setFragment(null);
                    if (null != context && context.isRobot())
                        url.addParameter(ActionURL.Param._noindex.name(), "1");
                    dataQuery = StringUtils.trimToEmpty(url.getRawQuery());
                    url.deleteParameters();
                    if (!dataQuery.isEmpty())
                        dataQuery = "?" + dataQuery;
                    if (StringUtils.isNotEmpty(fragment))
                        dataQuery += "#" + fragment;

                    href = url.getLocalURIString();
                    config.addHandlerForQuerySelector(
                            "A.noFollowNavigate",
                            "click",
                            "window.location = this.href + this.dataset['query']; return false;");
                    cls = StringUtils.trimToEmpty(cls) + " noFollowNavigate";
                }
            }
            catch (URISyntaxException e)
            {
                // fall through
            }
        }

        LinkBuilder builder = new LinkBuilder(html)
            .id(config.makeId("popupMenuView"))
            .target(item.getTarget())
            .title(item.getDescription())
            .tabindex(0)
            .enabled(!item.isDisabled())
            .clearClasses()
            .href(null != href && !item.isPost() ? href : "#")
            .onClick(item.getScript());

        if (null != itemImageCls)
            builder.style(styleStr);

        if (cls != null)
            builder.addClass(cls);

        if (null != dataQuery)
            builder.attributes(Map.of("data-query", dataQuery));

        oldWriter.write(builder.toString());

//        // if the item is "selected" and doesn't have an image cls to use, provide our default
//        String itemImageCls = item.getImageCls();
//        if (item.isSelected() && null == itemImageCls)
//            itemImageCls = "fa fa-check-square-o";
//
//        String styleStr = "";
//        if (null != itemImageCls)
//            styleStr += "padding-left: 0;";
//        if (item.isStrong())
//            styleStr += "font-weight: bold;";
//        if (item.isEmphasis())
//            styleStr += "font-style: italic;";
//
//        // NOTE: nofollow is not recommended as way to avoid crawling internal links
//        // instead let's use an onclick handler to "hide" the link
//        String dataQuery = null;
//        String href = item.getHref();
//        var config = HttpView.currentPageConfig();
//        if (null != href && null == item.getScript() && !item.isPost())
//        {
//            try
//            {
//                URLHelper url = new URLHelper(href);
//                boolean isLocal = null == url.getHost() && null == url.getScheme() && -1 == url.getPort();
//                if (isLocal)
//                {
//                    var context = HttpView.currentContext();
//
//                    // separate the path (into href) and query/fragment (into dataQuery) portions of URL
//                    var fragment = url.getFragment();
//                    url.setFragment(null);
//                    if (null != context && context.isRobot())
//                        url.addParameter(ActionURL.Param._noindex.name(), "1");
//                    dataQuery = StringUtils.trimToEmpty(url.getRawQuery());
//                    url.deleteParameters();
//                    if (!dataQuery.isEmpty())
//                        dataQuery = "?" + dataQuery;
//                    if (StringUtils.isNotEmpty(fragment))
//                        dataQuery += "#" + fragment;
//
//                    href = url.getLocalURIString();
//                    config.addHandlerForQuerySelector(
//                            "A.noFollowNavigate",
//                            "click",
//                            "window.location = this.href + this.dataset['query']; return false;");
//                    cls = StringUtils.trimToEmpty(cls) + " noFollowNavigate";
//                }
//            }
//            catch (URISyntaxException e)
//            {
//                // fall through
//            }
//        }
//
//        String id = config.makeId("popupMenuView");
//        oldWriter.write("<a id='" + id + "'");
//        if (null != cls)
//            oldWriter.write(" class=\"" + cls + "\"");
//        if (null != href && !item.isPost())
//            oldWriter.write(" href=\"" + PageFlowUtil.filter(href) + "\"");
//        else
//            oldWriter.write(" href=\"#\"");
//        if (null != dataQuery)
//            oldWriter.write(" data-query=\"" + PageFlowUtil.filter(dataQuery) + "\"");
//        if (null != item.getTarget())
//            oldWriter.write(" target=\"" + item.getTarget() + "\"");
//        if (null != item.getDescription())
//            oldWriter.write(" title=\"" + PageFlowUtil.filter(item.getDescription()) + "\"");
//        if (item.isDisabled())
//            oldWriter.write(" disabled");
//        oldWriter.write(" tabindex=\"0\"");
//        oldWriter.write(" style=\"" + styleStr + "\"");
//        if (item.isNoFollow())
//            oldWriter.write(" rel=\"noFollow\"");
//        oldWriter.write(">");
//        if (null != itemImageCls)
//            oldWriter.write("<i class=\"" + itemImageCls + "\"></i>");
//        oldWriter.write(PageFlowUtil.filter(item.getText()));
//        oldWriter.write("</a>");
//        config.addHandler(id, "click", item.getScript());
    }

    public static String getMenuFilterItemCls(NavTree tree)
    {
        return PageFlowUtil.filter(tree.getText()).replaceAll("\\s", "-").toLowerCase() + "-item";
    }

    private static void renderMenuFilterInput(String menuFilterItemCls, HtmlWriter out) throws IOException
    {
        LI(
            cl("menu-filter-input"),
            new Input.InputBuilder<>()
                .type("text")
                .placeholder("Filter")
                .className("dropdown-menu-filter")
                .addDataAttribute("filter-item", menuFilterItemCls)
        ).appendTo(out);
    }
}
