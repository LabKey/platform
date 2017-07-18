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

package org.labkey.api.data;


import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DisplayElement;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 * A standard, fully-HTML rendered (as opposed to being rendered as ExtJS config),
 * button in the appearance sense (but not necessarily an &lt;input&gt; element).
 */
// TODO: This class has essentially become a wrapper of Button.java. Switch to extend Button rather than repeat.
public class ActionButton extends DisplayElement implements Cloneable
{
    /** How to respond to the user clicking on the button */
    public enum Action
    {
        /** Do an HTTP POST of the surrounding form */
        POST("post"),
        /** Do an HTTP GET of the surrounding form */
        GET("get"),
        /** Do a simple link via an &lt;a&gt; */
        LINK("link"),
        /** Run a JavaScript snippet */
        SCRIPT("script");

        private String _description;

        Action(String desc)
        {
            _description = desc;
        }

        public String getDescription()
        {
            return _description;
        }

        public String toString()
        {
            return getDescription();
        }
    }

    // All of these buttons assume that they can address an action of a predefined name
    // in the same controller as the current page. This isn't usually a good assumption,
    // as it doesn't work for web parts that might be rendered by another controller.
    public static ActionButton BUTTON_DELETE = null;
    public static ActionButton BUTTON_SHOW_INSERT = null;
    public static ActionButton BUTTON_SHOW_UPDATE = null;
    public static ActionButton BUTTON_SHOW_GRID = null;
    public static ActionButton BUTTON_DO_INSERT = null;
    public static ActionButton BUTTON_DO_UPDATE = null;

    static
    {
        BUTTON_DELETE = new ActionButton("delete.post", "Delete");
        BUTTON_DELETE.setDisplayPermission(DeletePermission.class);
        BUTTON_DELETE.setRequiresSelection(true, "Are you sure you want to delete the selected row?", "Are you sure you want to delete the selected rows?");
        BUTTON_DELETE.lock();
        MemTracker.getInstance().remove(BUTTON_DELETE);

        BUTTON_SHOW_INSERT = new ActionButton("showInsert.view", "Insert New");
        BUTTON_SHOW_INSERT.setActionType(Action.LINK);
        BUTTON_SHOW_INSERT.setDisplayPermission(InsertPermission.class);
        BUTTON_SHOW_INSERT.lock();
        MemTracker.getInstance().remove(BUTTON_SHOW_INSERT);

        BUTTON_SHOW_UPDATE = new ActionButton("showUpdate.view", "Edit");
        BUTTON_SHOW_UPDATE.setActionType(Action.GET);
        BUTTON_SHOW_UPDATE.setDisplayPermission(UpdatePermission.class);
        BUTTON_SHOW_UPDATE.lock();
        MemTracker.getInstance().remove(BUTTON_SHOW_UPDATE);

        BUTTON_SHOW_GRID = new ActionButton("begin.view", "Show Grid");
        BUTTON_SHOW_GRID.setURL("begin.view?" + DataRegion.LAST_FILTER_PARAM + "=true");
        BUTTON_SHOW_GRID.setActionType(Action.LINK);
        BUTTON_SHOW_GRID.lock();
        MemTracker.getInstance().remove(BUTTON_SHOW_GRID);

        BUTTON_DO_INSERT = new ActionButton("insert.post", "Submit");
        BUTTON_DO_INSERT.lock();
        MemTracker.getInstance().remove(BUTTON_DO_INSERT);

        BUTTON_DO_UPDATE = new ActionButton("update.post", "Submit");
        BUTTON_DO_UPDATE.lock();
        MemTracker.getInstance().remove(BUTTON_DO_UPDATE);
    }


    private Action _actionType = Action.POST;
    private StringExpression _caption;
    private StringExpression _actionName;
    private StringExpression _url;
    private StringExpression _script;
    private StringExpression _title;
    private String _iconCls;
    private String _target;
    private String _tooltip;
    private boolean _appendScript;
    private boolean _disableOnClick;
    protected boolean _requiresSelection;
    protected Integer _requiresSelectionMinCount;
    protected Integer _requiresSelectionMaxCount;
    private @Nullable String _singularConfirmText;
    private @Nullable String _pluralConfirmText;
    private String _encodedSubmitForm;
    private boolean _noFollow = false;
    private boolean _enabled = true;

    private String _id;

    public ActionButton(String caption)
    {
        _caption = StringExpressionFactory.create(caption);
        setURL("#" + caption);
    }

    public ActionButton(String caption, URLHelper link)
    {
        _caption = StringExpressionFactory.create(caption);
        _url = StringExpressionFactory.create(link.toString(), true);
        _actionType = Action.LINK;
    }

    public ActionButton(String caption, StringExpression link)
    {
        _caption = StringExpressionFactory.create(caption);
        _url = link;
        _actionType = Action.LINK;
    }

    public ActionButton(ActionURL url, String caption)
    {
        _caption = StringExpressionFactory.create(caption);
        _url = StringExpressionFactory.create(url.getLocalURIString(true), true);
    }

    /** Use version that takes an action class instead */
    @Deprecated
    private ActionButton(String actionName, String caption)
    {
        assert StringUtils.containsNone(actionName,"/:?") : "this is for _actions_, use setUrl() or setScript()";
        _actionName = StringExpressionFactory.create(actionName);
        _caption = StringExpressionFactory.create(caption);
    }

    public ActionButton(Class<? extends Controller> action, String caption)
    {
        _caption = StringExpressionFactory.create(caption);
        ActionURL url = new ActionURL(action,null);
        _url = new DetailsURL(url);
        _actionName = StringExpressionFactory.create(url.getAction());
    }

    public ActionButton(ActionURL url, String caption, int displayModes)
    {
        this(url, caption);
        setDisplayModes(displayModes);
    }

    public ActionButton(ActionURL url, String caption, int displayModes, Action actionType)
    {
        this(url, caption);
        setDisplayModes(displayModes);
        setActionType(actionType);
    }

    public ActionButton(Class<? extends Controller> action, String caption, int displayModes, Action actionType)
    {
        this(action, caption);
        setDisplayModes(displayModes);
        setActionType(actionType);
    }

    protected ActionButton(String caption, int displayModes, Action actionType)
    {
        this(caption);
        setDisplayModes(displayModes);
        setActionType(actionType);
    }

    public ActionButton(ActionButton ab)
    {
        _actionName = ab._actionName;
        _actionType = ab._actionType;
        _caption = ab._caption;
        _script = ab._script;
        _title = ab._title;
        _url = ab._url;
        _target = ab._target;
        _requiresSelection = ab._requiresSelection;
        _pluralConfirmText = ab._pluralConfirmText;
        _singularConfirmText = ab._singularConfirmText;
        _noFollow = ab._noFollow;
        _enabled = ab._enabled;
    }

    public String getActionType()
    {
        return _actionType.toString();
    }

    public ActionButton setActionType(Action actionType)
    {
        checkLocked();
        _actionType = actionType;
        return this;
    }

    public ActionButton setActionName(String actionName)
    {
        checkLocked();
        _actionName = StringExpressionFactory.create(actionName);
        return this;
    }

    public String getActionName(RenderContext ctx)
    {
        return _eval(_actionName, ctx);
    }

    public String getCaption(RenderContext ctx)
    {
        if (null == _caption)
            return _eval(_actionName, ctx);
        return _eval(_caption, ctx);
    }

    public String getCaption()
    {
        if (null != _caption)
            return _caption.getSource();
        else if (null != _actionName)
            return _actionName.getSource();
        return null;    
    }

    public void setCaption(String caption)
    {
        checkLocked();
        _caption = StringExpressionFactory.create(caption);
    }

    public ActionButton setDisableOnClick(boolean disableOnClick)
    {
        checkLocked();
        _disableOnClick = disableOnClick;
        return this;
    }

    public ActionButton setIconCls(String iconCls)
    {
        checkLocked();
        _iconCls = iconCls;
        return this;
    }

    @Nullable
    public String getIconCls()
    {
        return _iconCls;
    }

    public void setURL(ActionURL url)
    {
        setURL(url.getLocalURIString());
    }

    public void setURL(String url)
    {
        checkLocked();
        _actionType = Action.LINK;
        _url = StringExpressionFactory.create(url, true);
    }

    public String getURL(RenderContext ctx)
    {
        if (null != _url)
            return _eval(_url, ctx);
        String action = getActionName(ctx);
        assert StringUtils.containsNone(action,"/:?") : "this is for _actions_, use setUrl() or setScript()";
        ActionURL url = new ActionURL(ctx.getViewContext().getActionURL().getController(), action, ctx.getViewContext().getContainer());
        return url.getLocalURIString();
    }

    public String getScript(RenderContext ctx)
    {
        return _eval(_script, ctx);
    }

    public ActionButton setScript(String script)
    {
        return setScript(script, false);
    }

    public ActionButton setScript(String script, boolean appendToDefaultScript)
    {
        checkLocked();
        if (!appendToDefaultScript) // Only change the action type if this is a full script replacement
            _actionType = Action.SCRIPT;
        _script = StringExpressionFactory.create(script);
        _appendScript = appendToDefaultScript;
        return this;
    }

    public ActionButton setTarget(String target)
    {
        _target = target;
        return this;
    }

    public String getTarget()
    {
        return _target;
    }

    public void setNoFollow(boolean noFollow)
    {
        _noFollow = noFollow;
    }

    public void setEnabled(boolean enabled)
    {
        _enabled = enabled;
    }
    
    public void setRequiresSelection(boolean requiresSelection)
    {
        setRequiresSelection(requiresSelection, null, (Integer)null);
    }

    public void setRequiresSelection(boolean requiresSelection, Integer minCount, Integer maxCount)
    {
        setRequiresSelection(requiresSelection, minCount, maxCount, null, null, null);
    }

    // Confirm text strings can include ${selectedCount} -- when the message is rendered, this will be replaced by the actual count.
    public void setRequiresSelection(boolean requiresSelection, @NotNull String singularConfirmText, @NotNull String pluralConfirmText)
    {
        setRequiresSelection(requiresSelection, null, null, singularConfirmText, pluralConfirmText, null);
    }

    // Confirm text strings can include ${selectedCount} -- when the message is rendered, this will be replaced by the actual count.
    public void setRequiresSelection(boolean requiresSelection, Integer minCount, Integer maxCount, @Nullable String singularConfirmText, @Nullable String pluralConfirmText, @Nullable String encodedSubmitForm)
    {
        checkLocked();
        _requiresSelection = requiresSelection;
        _requiresSelectionMinCount = minCount;
        _requiresSelectionMaxCount = maxCount;
        _singularConfirmText = singularConfirmText;
        _pluralConfirmText = pluralConfirmText;
        _encodedSubmitForm = encodedSubmitForm;
    }

    public boolean hasRequiresSelection()
    {
        return _requiresSelection;
    }

    private String renderDefaultScript(RenderContext ctx) throws IOException
    {
        if (_requiresSelection)
        {
            // We pass in the plural text first since some javascript usages of verifySelected() pass in only a single (plural)
            // confirmation message.
            return "return verifySelected(" +
                        (_encodedSubmitForm != null ? _encodedSubmitForm : "this.form") + ", " +
                        "\"" + getURL(ctx) + "\", " +
                        "\"" + _actionType.toString() + "\", " +
                        "\"rows\"" +
                        (_pluralConfirmText != null ? ", \"" + PageFlowUtil.filter(_pluralConfirmText) + "\"" : "") +
                        (_singularConfirmText != null ? ", \"" + PageFlowUtil.filter(_singularConfirmText) + "\"" : "") +
                    ")";
        }
        else
        {
            return "this.form.action=\"" +
                    getURL(ctx) +
                    "\";this.form.method=\"" +
                    _actionType.toString() + "\";";
        }
    }

    public String getId()
    {
        return _id;
    }

    public ActionButton setId(String id)
    {
        _id = id;
        return this;
    }

    public String getTooltip()
    {
        return _tooltip;
    }

    public ActionButton setTooltip(String tooltip)
    {
        _tooltip = tooltip;
        return this;
    }

    public void render(RenderContext ctx, Writer out) throws IOException
    {
        if (!shouldRender(ctx))
            return;

        lock();

        Button.ButtonBuilder button = PageFlowUtil.button(getCaption(ctx))
                .disableOnClick(_disableOnClick)
                .iconCls(getIconCls())
                .tooltip(getTooltip())
                .enabled(_enabled)
                .id(_id);

        Map<String, String> attributes = new HashMap<>();

        if (_requiresSelection)
        {
            DataRegion dataRegion = ctx.getCurrentRegion();
            assert dataRegion != null : "ActionButton.setRequiresSelection() needs to be rendered in context of a DataRegion";
            attributes.put("labkey-requires-selection", dataRegion.getName());
            if (_requiresSelectionMinCount != null)
                attributes.put("labkey-requires-selection-min-count", _requiresSelectionMinCount.toString());
            if (_requiresSelectionMaxCount != null)
                attributes.put("labkey-requires-selection-max-count", _requiresSelectionMaxCount.toString());
        }
        
        if (_noFollow)
            attributes.put("rel", "nofollow");

        if (_actionType.equals(Action.POST) || _actionType.equals(Action.GET))
        {
            StringBuilder onClickScript = new StringBuilder();
            if (null == _script || _appendScript)
                onClickScript.append(renderDefaultScript(ctx));
            if (_script != null)
                onClickScript.append(getScript(ctx));

            String actionName = getActionName(ctx);
            if (actionName != null)
                attributes.put("name", actionName);

            button.onClick(onClickScript.toString())
                    .submit(true);
        }
        else if (_actionType.equals(Action.LINK))
        {
            if (_target != null)
                attributes.put("target", _target);
            if (_script != null)
                button.onClick(getScript(ctx));

            button.href(getURL(ctx));
        }
        else
        {
            if (_appendScript)
                button.onClick(renderDefaultScript(ctx) + getScript(ctx));
            else
                button.onClick(getScript(ctx));

            button.href("javascript:void(0);");
        }

        button.attributes(attributes);

        out.write(button.toString());
    }

    public ActionButton clone()
    {
        try
        {
            ActionButton button = (ActionButton) super.clone();
            button._locked = false;
            return button;
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException("Superclass was expected to be cloneable", e);
        }
    }

    @Nullable
    private static String _eval(StringExpression expr, RenderContext ctx)
    {
        return expr == null ? null : expr.eval(ctx);
    }
}
