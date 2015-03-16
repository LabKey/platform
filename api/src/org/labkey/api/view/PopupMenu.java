/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A menu which is not fully rendered/visible at original page render time, but instead
 * is shown in response to show user action, like clicking on a button.
 *
 * User: Mark Igra
 * Date: May 13, 2008
 */
public class PopupMenu extends DisplayElement
{
    private NavTree _navTree;
    private Align _align = Align.LEFT;
    private ButtonStyle _buttonStyle = ButtonStyle.MENUBUTTON;
    private String _imageId = "";
    private String _offset = "-1";
    private String _safeID = "lk-menu-" + UniqueID.getServerSessionScopedUID();

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

    public void setImageId(String imageId)
    {
        _imageId = imageId;
    }

    public String getImageId()
    {
        return _imageId;
    }

    public String getSafeID()
    {
        return _safeID;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        render(out);
    }

    public void render(Writer out) throws IOException
    {
        renderMenuButton(out);
        renderMenuScript(out);
    }

    public void renderMenuButton(Writer out) throws IOException
    {
        renderMenuButton(out, null, false);
    }

    public void renderMenuButton(Writer out, String dataRegionName, boolean requiresSelection) throws IOException
    {
        if (null == _navTree.getText())
            return;

        // Issue 11392: DataRegion name escaping in button menus.  Menu id is double-escaped.  Once here, once when rendering.
        String jsStringFilteredMenuId = PageFlowUtil.qh(_safeID);

        Map<String, String> attributes = new HashMap<>();
        attributes.put("lk-menu-id", _safeID);

        if (_buttonStyle == ButtonStyle.TEXTBUTTON)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            String link = PageFlowUtil.textLink(_navTree.getText(), "javascript:void(0)", "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", "", attributes);
            out.append(link);
        }
        else if (_buttonStyle == ButtonStyle.MENUBUTTON)
        {
            if (requiresSelection)
                attributes.put("labkey-requires-selection", PageFlowUtil.filter(dataRegionName));
            out.append(PageFlowUtil.generateDropDownButton(_navTree.getText(), "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", attributes));
        }
        else if (_buttonStyle == ButtonStyle.TEXT || _buttonStyle == ButtonStyle.BOLDTEXT)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            out.append(PageFlowUtil.generateDropDownTextLink(_navTree.getText(), "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", _buttonStyle == ButtonStyle.BOLDTEXT, _offset, _navTree.getId()));
        }
        else if (_buttonStyle == ButtonStyle.IMAGE)
        {
            assert !requiresSelection : "Only button-style popups can require selection.";
            assert _navTree.getImageSrc() != null && _navTree.getImageSrc().length() > 0 : "Must provide an image source for image based popups.";
            out.append(PageFlowUtil.generateDropDownImage(_navTree.getText(),  "javascript:void(0)",
                    "showMenu(this, " + jsStringFilteredMenuId + ",'" + _align.getExtPosition() + "');", _navTree.getImageSrc(), _imageId, _navTree.getImageHeight(), _navTree.getImageWidth()));
        }
    }

    public void renderMenuScript(Writer out) throws IOException
    {
        out.append("<script type=\"text/javascript\">\n");
        renderExtMenu(out);
        out.append("\n</script>");
    }

    private void renderExtMenu(Writer out) throws IOException
    {
        out.append("if (typeof(Ext4) != 'undefined') { Ext4.onReady(function() {");
        out.append(renderUnregScript());
        out.append(" var m = Ext4.create('Ext.menu.Menu',");
        out.append(renderMenuModel(_navTree.getChildList(), _safeID));
        out.append("); }); } else { console.error('Unable to render menu. Ext4 is not available.'); }");
    }

    private String renderUnregScript()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("    var oldMenu = Ext4.menu.Manager.get(");
        sb.append(PageFlowUtil.qh(_safeID));
        sb.append(");\n");
        sb.append("    if(oldMenu)\n");
        sb.append("    {\n");
        sb.append("        oldMenu.removeAll();\n");
        sb.append("        Ext4.menu.Manager.unregister(oldMenu);\n");
        sb.append("    }\n");
        return sb.toString();
    }

    private static String renderMenuModel(Collection<NavTree> trees, String id)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("{showSeparator: false, ");
        sb.append("id:").append(PageFlowUtil.qh(id)).append(",\n");
        sb.append("items:");
        NavTree.toJS(trees, sb, true);
        sb.append("}");

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
            return dataRegionName + ".Menu." + _navTree.getText();
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
