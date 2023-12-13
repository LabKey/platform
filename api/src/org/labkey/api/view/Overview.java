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
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.HasHtmlString;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for rendering a series of steps involved with setting something up.
 * Steps may be enabled or disabled.  If disabled, they are shown as grey.
 */
public class Overview implements HasHtmlString
{
    private final User _user;
    private final Container _container;
    private final List<Step> _steps;
    private final List<Action> _actions;
    
    private String _title;
    private HtmlString _explanatoryHTML;
    private HtmlString _statusHTML;

    public Overview(User user, Container container)
    {
        _user = user;
        _container = container;
        _steps = new ArrayList<>();
        _actions = new ArrayList<>();
    }

    static protected String h(Object text)
    {
        return PageFlowUtil.filter(text);
    }

    @Override
    public HtmlString getHtmlString()
    {
        HtmlStringBuilder ret = HtmlStringBuilder.of();
        ret.unsafeAppend("<div class=\"labkey-overview\">\n");
        if (!StringUtils.isEmpty(_title))
        {
            ret.unsafeAppend("<b>");
            ret.append(_title);
            ret.unsafeAppend("</b><br>\n");
        }
        if (!HtmlString.isEmpty(_explanatoryHTML))
        {
            ret.unsafeAppend("<i>");
            ret.append(_explanatoryHTML);
            ret.unsafeAppend("</i><br>\n");
        }

        if (!HtmlString.isEmpty(_statusHTML))
        {
            ret.append(_statusHTML);
            ret.unsafeAppend("<br>");
        }
        if (!_steps.isEmpty())
        {
            ret.unsafeAppend("<ol>");
            for (Step step : _steps)
            {
                ret.append(step);
            }
            ret.unsafeAppend("</ol>");
        }
        for (Action miscAction : _actions)
        {
            ret.append(miscAction);
            ret.unsafeAppend("<br>");
        }
        ret.unsafeAppend("</div>");
        return ret.getHtmlString();
    }

    @Override
    public String toString()
    {
        return getHtmlString().toString();
    }


    public void addStep(Step step)
    {
        _steps.add(step);
    }

    public void addAction(Action action)
    {
        _actions.add(action);
    }

    public boolean hasPermission(Class<? extends Permission> perm)
    {
        return _container.hasPermission(_user, perm);
    }

    public User getUser()
    {
        return _user;
    }

    public Container getContainer()
    {
        return _container;
    }

    /**
     * Sets the title.  The title appears at the top.
     */
    public void setTitle(String title)
    {
        _title = title;
    }

    /**
     * Set the explanatory text which appears at the top.
     * Explanatory text is text that is intended to be ignored by users that know what they're doing.  It is
     * rendered in italics.
     */
    public void setExplanatoryHTML(HtmlString html)
    {
        _explanatoryHTML = html;
    }

    /**
     * Set the status text which appears at the top.
     * Status text sometimes changes and contains information that may be useful even to people who know how to use the product.
     */
    public void setStatusHTML(HtmlString html)
    {
        _statusHTML = html;
    }

    public static class Step implements HasHtmlString
    {
        public enum Status
        {
            normal,
            required,
            optional,
            disabled,
            completed,
        }

        private final String _title;
        private final Status _status;
        private final List<Action> _actions;

        private HtmlString _expanatoryHTML;
        private HtmlString _statusHTML;

        public Step(String title, Status status)
        {
            _title = title;
            _status = status;
            _actions = new ArrayList<>();
        }

        public void setExplanatoryHTML(HtmlString html)
        {
            _expanatoryHTML = html;
        }

        public void setStatusHTML(HtmlString html)
        {
            _statusHTML = html;
        }

        public void addAction(Action action)
        {
            if (action == null)
                return;
            _actions.add(action);
        }

        @Override
        public HtmlString getHtmlString()
        {
            HtmlStringBuilder ret = HtmlStringBuilder.of();
            ret.unsafeAppend("<li style=\"padding-bottom:0.5em\" class=\"labkey-indented");
            if (_status == Status.disabled)
            {
                ret.append(" labkey-disabled");
            }
            ret.unsafeAppend("\">");
            ret.unsafeAppend("<b>");
            ret.append(_title);
            ret.unsafeAppend("</b>");
            if (_status == Status.optional)
            {
                ret.append(" (optional)");
            }
            if (_status == Status.completed)
            {
                ret.append(" (completed)");
            }
            if (_status == Status.required)
            {
                ret.append(" (required)");
            }
            if (!HtmlString.isEmpty(_expanatoryHTML))
            {
                ret.unsafeAppend("<br><i>");
                ret.append(_expanatoryHTML);
                ret.unsafeAppend("</i>");
            }
            if (!(HtmlString.isEmpty(_statusHTML)))
            {
                ret.unsafeAppend("<br>");
                ret.append(_statusHTML);
            }
            for (Action action : _actions)
            {
                ret.unsafeAppend("<br>\n");
                ret.append(action);
            }
            ret.unsafeAppend("</li>");
            return ret.getHtmlString();
        }

        @Override
        public String toString()
        {
            return getHtmlString().toString();
        }
    }

    public static class Action implements HasHtmlString
    {
        private final String _label;
        private final ActionURL _url;

        private HtmlString _explanatoryHTML;
        private HtmlString _descriptionHTML;

        public Action(String label, ActionURL url)
        {
            _label = label;
            _url = url;
        }

        public void setExplanatoryHTML(HtmlString html)
        {
            _explanatoryHTML = html;
        }

        public void setDescriptionHTML(HtmlString html)
        {
            _descriptionHTML = html;
        }

        @Override
        public HtmlString getHtmlString()
        {
            HtmlStringBuilder ret = HtmlStringBuilder.of();
            ret.unsafeAppend("<span class=\"action\">");
            if (!HtmlString.isEmpty(_explanatoryHTML))
            {
                ret.unsafeAppend("<i>");
                ret.append(_explanatoryHTML);
                ret.unsafeAppend("</i><br>");
            }
            if (!HtmlString.isEmpty(_descriptionHTML))
            {
                ret.append(_descriptionHTML);
                ret.unsafeAppend("<br>");
            }
            ret.unsafeAppend("<span class=\"action-label\">");
            if (_url != null)
            {
                ret.append(PageFlowUtil.link(_label).href(_url));
            }
            else
            {
                ret.append(_label);
            }
            ret.unsafeAppend("</span></span>");
            return ret.getHtmlString();
        }

        @Override
        public String toString()
        {
            return getHtmlString().toString();
        }
    }
}
