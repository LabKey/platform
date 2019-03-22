/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * NavTree can be used three ways in different places in the product
 *
 * 1) as a single navigation element (no children)
 * 2) as a list of navigation elements (ignore the root node, use children as list of elements)
 * 3) as a tree, may be rendered as an tree, or menu
 */

public class NavTree implements Collapsible
{
    private static final NavTree MENU_SEPARATOR = new NavTree("-");

    private String _text;  // TODO: Change to "display"... but there's already a "display" property (??)
    private String _href;
    private boolean _selected = false;
    private boolean _collapsed = false;
    private boolean _canCollapse = true;
    private boolean _strong = false;
    private boolean _emphasis = false;
    private String _script;
    private String _id = "";
    private boolean _disabled;
    private String _description;
    private boolean _nofollow = false;
    private String _imageSrc = null;
    private String _imageCls = null;
    private Integer _imageHeight;
    private Integer _imageWidth;
    private String _target = null;
    private String _tip;
    private URLHelper _imageURL;
    private String _menuFilterItemCls = null;

    private final @NotNull List<NavTree> _children = new LinkedList<>();

    public static String escapeKey(String key)
    {
        return key == null ? null : key.replaceAll("%","%25").replaceAll("/","%2F");
    }

    public NavTree()
    {
        this(null, (String)null);
    }


    public NavTree(String text, String href, boolean collapsed)
    {
        _text = text;
        _href = href;
        _collapsed = collapsed;
    }

    public NavTree(String text, String href)
    {
        this(text, href, false);
    }

    public NavTree(String text, String href, String imageSrc)
    {
        this(text, href, false);
        _imageSrc = imageSrc;
    }

    public NavTree(String text, String href, String imageSrc, String imageCls)
    {
        this(text, href, false);
        _imageSrc = imageSrc;
        _imageCls = imageCls;
    }

    public NavTree(String text, URLHelper urlhelp)
    {
        this(text, urlhelp, false);
    }


    public NavTree(String text, URLHelper urlhelp, boolean collapsed)
    {
        this(text, urlhelp != null ? urlhelp.getLocalURIString() : null, collapsed);
    }


    public NavTree(String text)
    {
        this(text, (String) null);
    }

    /**
     * Creates a new NavTree instance which is a deep copy of the source NavTree
     * @param source The source NavTree
     */
    public NavTree(NavTree source)
    {
        this(source._text, source._href);
        _imageSrc = source._imageSrc;
        _selected = source._selected;
        _collapsed = source._collapsed;
        _canCollapse = source._canCollapse;
        _strong = source._strong;
        _emphasis = source._emphasis;
        _script = source._script;
        _id = source._id;
        _disabled = source._disabled;
        _imageHeight = source._imageHeight;
        _imageWidth = source._imageWidth;
        _target = source._target;
        _tip = source._tip;
        _imageCls = source._imageCls;
        _menuFilterItemCls = source._menuFilterItemCls;

        _children.addAll(source._children.stream().map(NavTree::new).collect(Collectors.toList()));
    }


    public void setText(String text)
    {
        _text = text;
    }

    public String getText()
    {
        return _text;
    }

    public void setHref(String href)
    {
        _href = href;
    }

    public String getHref()
    {
        return _href;
    }

    public String getEscapedKey()
    {
        return escapeKey(getText());
    }


    public void addSeparator()
    {
        addChild(MENU_SEPARATOR);
    }

    public NavTree addChild(NavTree child)
    {
        if (null != child._text)
            _children.add(child);
        else if (child.hasChildren())
            addChildren(child._children);
        return child;
    }

    public NavTree addChild(int pos, NavTree child)
    {
        _children.add(pos, child);
        return child;
    }

    public NavTree addChild(String display)
    {
        return addChild(new NavTree(display));
    }


    public NavTree addChild(String display, String href)
    {
        return addChild(new NavTree(display, href));
    }


    public NavTree addChild(String display, String href, String imageSrc)
    {
        addChild(new NavTree(display, href, imageSrc));
        return this;
    }

    public NavTree addChild(String display, String href, String imageSrc, String imageCls)
    {
        addChild(new NavTree(display, href, imageSrc, imageCls));
        return this;
    }


    public NavTree addChild(String display, @NotNull URLHelper urlhelp)
    {
        addChild(display, urlhelp.getLocalURIString());
        return this;
    }


    public void addChildren(Collection<NavTree> list)
    {
        if (list != null)
            _children.addAll(list);
    }


    public void addChildren(NavTree[] list)
    {
        addChildren(Arrays.asList(list));
    }

    @NotNull
    public List<NavTree> getChildren()
    {
        return Collections.unmodifiableList(_children);
    }

    public boolean hasChildren()
    {
        return !_children.isEmpty();
    }

    // Sort children by name, case-insensitive
    public void sort()
    {
        _children.sort(Comparator.comparing(NavTree::getText, String.CASE_INSENSITIVE_ORDER));
    }


    /**
     * Walk path and return folder. If path starts with /, first element
     * of the path is expected to match key of this navTree. If not, first element is expected
     * to be key of a child. If path starts with / and key does NOT match act as though
     * path did not start with / and try the rest on children
     *
     * @param path Path of folders to expand
     * @return Named subtree
     */
    public NavTree findSubtree(@Nullable String path)
    {
        if (null == path || path.length() == 0 || "/".equals(path))
            return this;

        //use the escaped key for path matching, so that embedded / characters are escaped
        String key = getEscapedKey();
        if (key == null) key = "";

        if (path.charAt(0) == '/')
        {
            if (path.substring(1).equals(key))
                return this;
            else if (path.startsWith("/" + key + "/"))
                return findSubtree(path.substring(key.length() + 2));
            else // Maybe try the children as a last resort
                path = path.substring(1);
        }

        //Now expand a child...
        String childKey;
        int slash = path.indexOf("/");
        if (slash < 0)
            childKey = path;
        else
            childKey = path.substring(0, slash);

        for (NavTree childTree : getChildren())
        {
            if (childKey.equals(childTree.getEscapedKey()))
                if (slash < 0)
                    return childTree;
                else
                    return childTree.findSubtree(path.substring(slash + 1));
        }

        return null;
    }

    public int getChildCount()
    {
        return _children.size();
    }

    public void setImage(String src, int width, int height)
    {
        _imageSrc = src;
        _imageWidth = width;
        _imageHeight = height;
    }


    public String getImageSrc()
    {
        if (null != _imageURL)
            return _imageURL.getLocalURIString();

        return _imageSrc;
    }

    public String getImageCls()
    {
        return _imageCls;
    }


    @Deprecated  // TODO: Delete
    public void setImageSrc(String imageSrc)
    {
        _imageSrc = imageSrc;
    }

    public void setImageSrc(@Nullable URLHelper imageURL)
    {
        _imageURL = imageURL;
    }

    public void setImageCls(@Nullable String imageCls)
    {
        _imageCls = imageCls;
    }

    public Integer getImageHeight()
    {
        return _imageHeight;
    }

    public Integer getImageWidth()
    {
        return _imageWidth;
    }

    public boolean isCollapsed()
    {
        return _collapsed;
    }

    public void setCollapsed(boolean collapsed)
    {
        _collapsed = collapsed;
    }

    public boolean getCanCollapse()
    {
        return _canCollapse;
    }

    public void setCanCollapse(boolean canCollapse)
    {
        _canCollapse = canCollapse;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = id;
    }

    public void setSelected(boolean s)
    {
        _selected = s;
    }

    public boolean isSelected()
    {
        return _selected;
    }

    public boolean isStrong()
    {
        return _strong;
    }

    public void setStrong(boolean strong)
    {
        _strong = strong;
    }

    public boolean isEmphasis()
    {
        return _emphasis;
    }

    public void setEmphasis(boolean emphasis)
    {
        _emphasis = emphasis;
    }

    public String getScript()
    {
        return _script;
    }

    public void setScript(String script)
    {
        _script = script;
    }

    public boolean isDisabled()
    {
        return _disabled;
    }

    public void setDisabled(boolean disabled)
    {
        _disabled = disabled;
    }

    /** Get the description text for this item.  Menu items will render this as a tooltip. */
    public String getDescription()
    {
        return _description;
    }

    /** Set the description text for this item.  Menu items will render this as a tooltip. */
    public void setDescription(String description)
    {
        _description = description;
    }

    public @Nullable String getTarget()
    {
        return _target;
    }

    public void setTarget(@Nullable String target)
    {
        _target = target;
    }

    public void setNoFollow(boolean b)
    {
        _nofollow = b;
    }

    public boolean isNoFollow()
    {
        return _nofollow;
    }

    public String getTip()
    {
        return _tip;
    }

    public void setTip(String tip)
    {
        _tip = tip;
    }

    public void setMenuFilterItemCls(String menuFilterItemCls)
    {
        _menuFilterItemCls = menuFilterItemCls;
    }

    public String getMenuFilterItemCls()
    {
        return _menuFilterItemCls;
    }

    public String childrenToJS()
    {
        return toJS(_children, new StringBuilder(), false).toString();
    }

    public String toJS()
    {
        return toJS(new StringBuilder(), true, false).toString();
    }


    public JSONObject toJSON()
    {
        return toJSON(true, "items");
    }

                                                                                
    public JSONObject toJSON(boolean recursive, String items)
    {
        JSONObject o = new JSONObject();
        o.put("text", getText());
        if (StringUtils.isNotEmpty(getId()))
            o.put("id", getId());
        if (StringUtils.isNotEmpty(getDescription()))
            o.put("description", getDescription());
        if (isSelected())
            o.put("checked", true);
        if (null != getImageSrc())
            o.put("icon", getImageSrc());
        if (null != getImageCls())
            o.put("iconCls", getImageCls());
        if (isDisabled())
            o.put("disabled", true);
        if (null != getHref())
            o.put("href", getHref());
        if (null != getTarget())
            o.put("hrefTarget", getTarget());
        if (null != getScript())
            o.put("handler", "function(){" + getScript() + "}");
        if (null != getTip())
            o.put("tip", getTip());
        if (recursive && hasChildren())
        {
            JSONArray a = new JSONArray();
            for (NavTree c : getChildren())
                a.put(c.toJSON(true, "menu"));
            o.put(items, a);
        }
        else
        {
            o.put("leaf",true);
        }
        return o;
    }

    /**
     * Renders a navtree instance to a javascript object suitable for consumptions by the rendering library.
     * Note that description is translated to: tooltip.
     */
    protected StringBuilder toJS(StringBuilder sb, boolean withIds, boolean asMenu)
    {
        String title = getText();
        sb.append("{").append("text:").append(PageFlowUtil.qh(title));
        if (isStrong() || isEmphasis())
        {
            sb.append(",cls:'");
            if (isStrong())
                sb.append("labkey-strong");
            if (isEmphasis())
                sb.append(" labkey-emphasis");
            sb.append("'");
        }
        if (StringUtils.isNotEmpty(getId()))
        {
            sb.append(",elementId:").append(PageFlowUtil.qh(getId()));
            if (withIds)
                sb.append(",id:").append(PageFlowUtil.qh(getId()));
        }
        if (StringUtils.isNotEmpty(getDescription()))
            sb.append(",tooltip:").append(PageFlowUtil.qh(getDescription()));
        if (isSelected())
            sb.append(",iconCls:'fa fa-check-square-o'");
        if (null != getImageCls())
            sb.append(",iconCls:").append(PageFlowUtil.qh(getImageCls()));
        else if (null != getImageSrc())
            sb.append(",icon:").append(PageFlowUtil.qh(getImageSrc()));
        if (isDisabled())
            sb.append(",disabled:true");
        sb.append(", showSeparator: false");
        sb.append(",href:").append(null != getHref() && !isDisabled() ? PageFlowUtil.qh(getHref()) : "'javascript: void(0)'");
        if (null != getTarget())
            sb.append(",hrefTarget:").append(PageFlowUtil.qh(getTarget()));
        if (null != getScript())
            sb.append(",handler:function(){").append(getScript()).append("}");
        if (hasChildren())
        {
            sb.append(",hideOnClick:false");
            sb.append(",\n").append(asMenu ? "menu:{showSeparator:false,items:" : "children:");
            toJS(_children, sb, asMenu, withIds);
            sb.append(",\n").append(asMenu ? "}" : "");
        }
        else
        {
            sb.append(",leaf:true");
        }
        sb.append("}");
        return sb;
    }

    @Deprecated
    public static StringBuilder toJS(Collection<NavTree> list, @Nullable StringBuilder sb, boolean asMenu)
    {
        return toJS(list,sb,asMenu,!asMenu);
    }

    public static StringBuilder toJS(Collection<NavTree> list, @Nullable StringBuilder sb, boolean asMenu, boolean withIds)
    {
        if (null == sb)
            sb = new StringBuilder();
        String sep = "";
        sb.append("[");
        for (NavTree tree : list)
        {
            sb.append(sep);
            if (tree == NavTree.MENU_SEPARATOR)
                sb.append("'-'");
            else
                tree.toJS(sb, withIds, asMenu);
            sep = ",\n";
        }
        sb.append("]");
        return sb;
    }
}
