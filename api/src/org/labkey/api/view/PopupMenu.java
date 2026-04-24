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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.RenderContext;
import org.labkey.api.util.ButtonBuilder;
import org.labkey.api.util.DOM;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.UniqueID;
import org.labkey.api.writer.HtmlWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.cl;

/**
 * A menu which is not fully rendered/visible at original page render time, but instead
 * is shown in response to show user action, like clicking on a button.
 */
public class PopupMenu extends DisplayElement
{
    // a menu that appears on the page only once, can use id's,
    // however a menu that can appear on the page multiple times
    // should not use id's
    private boolean _singletonMenu = false;
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

    public void setIsSingletonMenu(boolean singletonMenu)
    {
        // basically indicates that is OK to render id's on menu items (useful for testing)
        _singletonMenu = singletonMenu;
    }

    @Override
    public void render(RenderContext ctx, HtmlWriter out)
    {
        render(out);
    }

    public void render(Writer out) throws IOException
    {
        render(HtmlWriter.of(out));
    }

    public void render(HtmlWriter out)
    {
        renderMenuButton(null, out, false, null);
    }

    public void renderMenuButton(@Nullable RenderContext ctx, HtmlWriter out, boolean requiresSelection, @Nullable ActionButton button)
    {
        if (null == _navTree.getText())
            return;

        if (_singletonMenu && StringUtils.isNotEmpty(_navTree.getId()))
            _safeID = _navTree.getId();

        Map<String, String> attributes = new HashMap<>();
        String onClickScript = null;

        attributes.put("data-toggle", "dropdown");

        String dataRegionName;

        if (ctx != null && ctx.getCurrentRegion() != null)
            dataRegionName = ctx.getCurrentRegion().getName();
        else
            dataRegionName = null;

        DIV(
            cl("lk-menu-drop dropdown"),
            (DOM.Renderable) ret -> {

                if (_buttonStyle == ButtonStyle.TEXTBUTTON)
                {
                    assert !requiresSelection : "Only button-style popups can require selection.";
                    out.write(LinkBuilder.labkeyLink(_navTree.getText()).onClick(onClickScript).attributes(attributes).addClass("dropdown-toggle"));
                }
                else if (_buttonStyle == ButtonStyle.MENUBUTTON)
                {
                    if (requiresSelection)
                        attributes.put("data-labkey-requires-selection", dataRegionName);

                    ButtonBuilder bldr = PageFlowUtil.button(_navTree.getText())
                        .dropdown(true)
                        .onClick(onClickScript)
                        .attributes(attributes);

                    if (button != null)
                    {
                        // set additional properties from the button
                        bldr.iconCls(button.getIconCls());
                        bldr.tooltip(button.getTooltip());
                    }

                    out.write(bldr);
                }
                else if (_buttonStyle == ButtonStyle.IMAGE || _buttonStyle == ButtonStyle.IMAGE_AND_TEXT)
                {
                    assert !requiresSelection : "Only button-style popups can require selection.";
                    if (_navTree.getImageCls() != null && !_navTree.getImageCls().isEmpty())
                    {
                        out.write(PageFlowUtil.generateDropDownFontIconImage(_navTree.getText(), "#",
                            onClickScript, _navTree.getImageCls(), _imageId, attributes));
                    }
                    else
                    {
                        assert _navTree.getImageSrc() != null && !_navTree.getImageSrc().isEmpty() : "Must provide an image source or image cls for image based popups.";
                        out.write(PageFlowUtil.generateDropDownImage(_navTree.getText(), "#",
                            onClickScript, _navTree.getImageSrc(), _imageId, _navTree.getImageHeight(), _navTree.getImageWidth(), attributes));
                    }

                    if (_buttonStyle == ButtonStyle.IMAGE_AND_TEXT)
                    {
                        out.write(" ");
                    }
                }

                if (_buttonStyle == ButtonStyle.TEXT || _buttonStyle == ButtonStyle.BOLDTEXT || _buttonStyle == ButtonStyle.IMAGE_AND_TEXT)
                {
                    assert !requiresSelection : "Only button-style popups can require selection.";
                    out.write(PageFlowUtil.generateDropDownTextLink(_navTree.getText(), "#",
                        onClickScript, _buttonStyle == ButtonStyle.BOLDTEXT, _offset, _navTree.getId(), attributes));
                }

                UL(
                    cl("dropdown-menu dropdown-menu-left"),
                    (DOM.Renderable) _ -> PopupMenuView.renderTree(_navTree, out)
                ).appendTo(out);

                return ret;
            }
        ).appendTo(out);
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

        final String extPosition;
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
        /** Text only, rendered like a link */
        TEXT,
        TEXTBUTTON,
        /** Icon only */
        IMAGE,
        /** Icon and text rendered like a link */
        IMAGE_AND_TEXT
    }
}
