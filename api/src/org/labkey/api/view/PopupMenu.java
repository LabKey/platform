/*
 * Copyright (c) 2008-2011 LabKey Corporation
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
import org.labkey.api.data.RenderContext;
import org.apache.commons.lang.StringUtils;

import java.io.Writer;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: Mark Igra
 * Date: May 13, 2008
 * Time: 3:30:25 PM
 */
public class PopupMenu extends DisplayElement
{
    private NavTree _navTree;
    private Align _align = Align.LEFT;
    private ButtonStyle _buttonStyle = ButtonStyle.MENUBUTTON;
    private String _imageSrc = "";
    private String _imageId = "";
    private String _offset = "-1";
    
    public PopupMenu()
    {
        this(new NavTree());
    }

    public PopupMenu(NavTree navTree)
    {
        _navTree = navTree;
    }

    public PopupMenu(NavTree navTree, Align align, ButtonStyle buttonStyle)
    {
        _navTree = navTree;
        _align = align;
        _buttonStyle = buttonStyle;
    }

    public void setOffset(String offset)
    {
        _offset = offset;
    }
    
    public NavTree getNavTree()
    {
        return _navTree;
    }

    public void setNavTree(NavTree navTree)
    {
        _navTree = navTree;
    }

    public String getImageSrc()
    {
        return _imageSrc;
    }

    public void setImageSrc(String imageSrc)
    {
        _imageSrc = imageSrc;
    }

    public void setImageId(String imageId)
    {
        _imageId = imageId;
    }

    public String getImageId()
    {
        return _imageId;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        render(out);
    }

    public void render(Writer out) throws IOException
    {
        renderMenuButton(out);
        renderMenuScript(out, null);
    }

    public void renderMenuButton(Writer out) throws IOException
    {
        renderMenuButton(out, null, false);
    }

    public void renderMenuButton(Writer out, String dataRegionName, boolean requiresSelection) throws IOException
    {
        if (null == _navTree.getKey())
            return;

        // Issue 11392: DataRegion name escaping in button menus.  Menu id is double-escaped.  Once here, once when rendering.
        String jsStringFilteredMenuId = PageFlowUtil.qh(getId(dataRegionName));

        if (_buttonStyle == ButtonStyle.TEXTBUTTON)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            String link = PageFlowUtil.textLink(_navTree.getKey(), "javascript:void(0)", "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", "");
            out.append(link);
        }
        else if (_buttonStyle == ButtonStyle.MENUBUTTON)
        {
            Map<String, String> attributes = null;
            if (requiresSelection)
                attributes = Collections.singletonMap("labkey-requires-selection", PageFlowUtil.filter(dataRegionName));
            out.append(PageFlowUtil.generateDropDownButton(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", attributes));
        }
        else if (_buttonStyle == ButtonStyle.TEXT || _buttonStyle == ButtonStyle.BOLDTEXT)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            out.append(PageFlowUtil.generateDropDownTextLink(_navTree.getKey(), "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", _buttonStyle == ButtonStyle.BOLDTEXT, _offset, _navTree.getId()));
        }
        else if (_buttonStyle == ButtonStyle.IMAGE)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            assert _imageSrc.length() > 0 : "Must provide an image source for image based popups.";
            out.append(PageFlowUtil.generateDropDownImage(_navTree.getKey(),  "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", _imageSrc, _imageId));
        }
    }

    public void renderMenuScript(Writer out, String dataRegionName) throws IOException
    {
        out.append("<script type=\"text/javascript\">\n");
        out.append("Ext.onReady(function() {\n");
        out.append(renderUnregScript(getId(dataRegionName)));
        out.append("        new Ext.menu.Menu(");
        out.append(renderMenuModel(_navTree.getChildren(), getId(dataRegionName)));
        out.append("         );});\n</script>");
    }

    private String renderUnregScript(String id)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("    var oldMenu = Ext.menu.MenuMgr.get(");
        sb.append(PageFlowUtil.qh(id));
        sb.append(");\n");
        sb.append("    if(oldMenu)\n");
        sb.append("    {\n");
        sb.append("        oldMenu.removeAll();\n");
        sb.append("        Ext.menu.MenuMgr.unregister(oldMenu);\n");
        sb.append("    }\n");
        return sb.toString();
    }

    // UNDONE: use NavTree.toJS()
    private String renderMenuModel(NavTree[] trees, String id)
    {
        String sep = "";
        StringBuilder sb = new StringBuilder();

        sb.append("{cls:'extContainer',");
        sb.append("id:").append(PageFlowUtil.qh(id)).append(",\n");
        sb.append("items:[");
        for (NavTree tree : trees)
        {
            sb.append(sep);
            if (tree == NavTree.MENU_SEPARATOR)
            {
                sb.append("'-'");
                sep = ",";
                continue;
            }

            sb.append("{").append("text:").append(PageFlowUtil.qh(tree.getKey()));
            if (tree.isStrong() || tree.isEmphasis())
            {
                sb.append(", cls:'");
                if (tree.isStrong())
                    sb.append("labkey-strong");
                if (tree.isEmphasis())
                    sb.append(" labkey-emphasis");
                sb.append("'");
            }
            if (StringUtils.isNotEmpty(tree.getId()))
                sb.append(", id:").append(PageFlowUtil.qh(tree.getId()));
            if (StringUtils.isNotEmpty(tree.getDescription()))
                sb.append(", tooltip: ").append(PageFlowUtil.qh(tree.getDescription()));
            if (tree.isSelected())
                sb.append(", checked:true");
            if (null != tree.getImageSrc())
                sb.append(", icon:").append(PageFlowUtil.qh(tree.getImageSrc()));
            if (tree.isDisabled())
                sb.append(", disabled:true");
            // If we don't have a URL, use 'javascript: void(0) or Ext will fill in '#', which causes the browser
            // to scroll to the top of the page.
            sb.append(",").append("href:").append(null != tree.getValue() ? PageFlowUtil.qh(tree.getValue()) : "'javascript: void(0)'");
            if (null != tree.getScript())
                sb.append(", handler:function(){").append(tree.getScript()).append("}");
            if (null != tree.getChildren() && tree.getChildren().length > 0)
            {
                sb.append(", hideOnClick:false");
                sb.append(",\n menu:").append(renderMenuModel(tree.getChildren(), null)).append("\n");
            }
            sb.append("}\n");
            sep = ",";
        }
        sb.append("]}");

        return sb.toString();
    }

    public Align getAlign()
    {
        return _align;
    }

    public void setAlign(Align align)
    {
        _align = align;
    }

    public ButtonStyle getButtonStyle()
    {
        return _buttonStyle;
    }

    public void setButtonStyle(ButtonStyle buttonStyle)
    {
        _buttonStyle = buttonStyle;
    }

    public String getId(String dataRegionName)
    {
        if (null != StringUtils.trimToNull(_navTree.getId()))
        {
            return _navTree.getId();
        }
        if (dataRegionName != null)
        {
            return dataRegionName + ".Menu." + _navTree.getKey();
        }
        return String.valueOf(System.identityHashCode(this));
    }

    public enum Align
    {
        LEFT("tl-bl?"),
        RIGHT("tr-br?");

        String extPosition;
        Align(String position)
        {
            extPosition = position;
        }

        public String getExtPosition()
        {
            return extPosition;
        }
    }

    public enum ButtonStyle
    {
        MENUBUTTON,
        BOLDTEXT,
        TEXT,
        TEXTBUTTON,
        IMAGE
    }
}
