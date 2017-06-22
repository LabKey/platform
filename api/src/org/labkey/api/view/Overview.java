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

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.PageFlowUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Class for rendering a series of steps involved with setting something up.
 * Steps may be enabled or disabled.  If disabled, they are shown as grey.
 */
public class Overview
{
    private final User _user;
    private final Container _container;
    private final List<Step> _steps;
    private final List<Action> _actions;
    
    private String _title;
    private String _explanatoryHTML;
    private String _statusHTML;

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

    public String toString()
    {
        StringBuilder ret = new StringBuilder();
        ret.append("<div class=\"labkey-overview\">\n");
        if (!StringUtils.isEmpty(_title))
        {
            ret.append("<b>");
            ret.append(h(_title));
            ret.append("</b><br>\n");
        }
        if (!StringUtils.isEmpty(_explanatoryHTML))
        {
            ret.append("<i>");
            ret.append(_explanatoryHTML);
            ret.append("</i><br>\n");
        }

        if (!StringUtils.isEmpty(_statusHTML))
        {
            ret.append(_statusHTML);
            ret.append("<br>");
        }
        if (_steps.size() != 0)
        {
            ret.append("<ol>");
            for (Step step : _steps)
            {
                ret.append(step);
            }
            ret.append("</ol>");
        }
        for (Action miscAction : _actions)
        {
            ret.append(miscAction);
            ret.append("<br>");
        }
        ret.append("</div>");
        return ret.toString();
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
    public void setExplanatoryHTML(String html)
    {
        _explanatoryHTML = html;
    }

    /**
     * Set the status text which appears at the top.
     * Status text sometimes changes and contains information that may be useful even to people who know how to use the product.
     */
    public void setStatusHTML(String html)
    {
        _statusHTML = html;
    }

    public static class Step
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

        private String _expanatoryHTML;
        private String _statusHTML;

        public Step(String title, Status status)
        {
            _title = title;
            _status = status;
            _actions = new ArrayList<>();
        }

        public void setExplanatoryHTML(String html)
        {
            _expanatoryHTML = html;
        }

        public void setStatusHTML(String html)
        {
            _statusHTML = html;
        }

        public void addAction(Action action)
        {
            if (action == null)
                return;
            _actions.add(action);
        }

        public String toString()
        {
            StringBuilder ret = new StringBuilder("<li");
            ret.append(" style=\"padding-bottom:0.5em\" class=\"labkey-indented");
            if (_status == Status.disabled)
            {
                ret.append(" labkey-disabled");
            }
            ret.append("\">");
            ret.append("<b>");
            ret.append(h(_title));
            ret.append("</b>");
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
            if (!StringUtils.isEmpty(_expanatoryHTML))
            {
                ret.append("<br><i>");
                ret.append(_expanatoryHTML);
                ret.append("</i>");
            }
            if (!(StringUtils.isEmpty(_statusHTML)))
            {
                ret.append("<br>");
                ret.append(_statusHTML);
            }
            for (Action action : _actions)
            {
                ret.append("<br>\n");
                ret.append(action);
            }
            ret.append("</li>");
            return ret.toString();
        }
    }

    public static class Action
    {
        private final String _label;
        private final ActionURL _url;

        private String _explanatoryHTML;
        private String _descriptionHTML;

        public Action(String label, ActionURL url)
        {
            _label = label;
            _url = url;
        }

        public void setExplanatoryHTML(String html)
        {
            _explanatoryHTML = html;
        }

        public void setDescriptionHTML(String html)
        {
            _descriptionHTML = html;
        }

        public String toString()
        {
            StringBuilder ret = new StringBuilder();
            ret.append("<span class=\"action\">");
            if (!StringUtils.isEmpty(_explanatoryHTML))
            {
                ret.append("<i>");
                ret.append(_explanatoryHTML);
                ret.append("</i><br>");
            }
            if (!StringUtils.isEmpty(_descriptionHTML))
            {
                ret.append(_descriptionHTML);
                ret.append("<br>");
            }
            ret.append("<span class=\"action-label\">");
            if (_url != null)
            {
                ret.append(PageFlowUtil.textLink(_label, _url));
            }
            else
            {
                ret.append(_label);
            }
            ret.append("</span></span>");
            return ret.toString();
        }
    }
}
