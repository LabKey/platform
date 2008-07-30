/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.view.template;

import org.labkey.api.util.HelpTopic;
import org.labkey.api.module.Module;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.List;

/**
 * User: brittp
 * Date: Sep 21, 2005
 * Time: 4:26:39 PM
 *
 * This class is used by an action to configure the template page.
 */
public class PageConfig
{
    public enum Template
    {
        None,
        Home,
        Fast,   // NYI (same as Home)
        Print,
        Dialog,
        Custom  // must be handled by module
    }

    private Template _template = Template.Home;
    private String _title;
    private HelpTopic _helpTopic;
    private boolean _exploratoryFeatures = false;
    private boolean _appendPathToTitle;
    private Module _moduleOwner;
    private String _focus = null;
    private String _focusId = null;
    private boolean _showPrintDialog = false;
    private String _anchor;
    private ActionURL _rssUrl = null;
    private String _rssTitle = null;
    private boolean _includeHeader = true;
    private boolean _includeLoginLink = true;
    private int _minimumWidth = 400;
    private String _styleSheet;
    private String _styles;
    private String _script;
    private boolean _showHeader = false;
    private List<NavTree> _navTrail;

    public PageConfig()
    {
    }

    public PageConfig(String title)
    {
        setTitle(title);
    }

    public PageConfig setTitle(String title)
    {
        return setTitle(title, true);
    }

    public PageConfig setTitle(String title, boolean appendPathToTitle)
    {
        _title = title;
        _appendPathToTitle = appendPathToTitle;
        return this;
    }


    public PageConfig setHelpTopic(HelpTopic topic)
    {
        _helpTopic = topic;
        return this;
    }

    public PageConfig setExploratoryFeatures(boolean exploratoryFeatures)
    {
        _exploratoryFeatures = exploratoryFeatures;
        return this;
    }

    public boolean getExploratoryFeatures()
    {
        return _exploratoryFeatures;
    }

    public HelpTopic getHelpTopic()
    {
        return _helpTopic;
    }

    public String getTitle()
    {
        return _title;
    }

    public boolean shouldAppendPathToTitle()
    {
        return _appendPathToTitle;
    }

    public Module getModuleOwner()
    {
        return _moduleOwner;
    }

    public void setModuleOwner(Module module)
    {
        _moduleOwner = module;
    }

    public Template getTemplate()
    {
        return _template;
    }

    public void setTemplate(Template template)
    {
        _template = template;
    }

    public String getFocusId()
    {
        return _focusId;
    }

    public void setFocusId(String focusId)
    {
        _focusId = focusId;
    }

    public String getFocus()
    {
        if (null != _focus)
            return _focus;

        if (null != _focusId)
            return "getElementById('" + _focusId + "')";

        return null;
    }

    @Deprecated  // Use setFocusId() instead
    public void setFocus(String focus)
    {
        _focus = focus;
    }

    public void setShowPrintDialog(boolean showPrintDialog)
    {
        _showPrintDialog = showPrintDialog;
    }

    public boolean getShowPrintDialog()
    {
        return _showPrintDialog;
    }

    public String getAnchor()
    {
        return _anchor;
    }

    public void setAnchor(String anchor)
    {
        _anchor = anchor;
    }

    public ActionURL getRssUrl()
    {
        return _rssUrl;
    }

    public String getRssTitle()
    {
        return _rssTitle;
    }

    public void setRssProperties(ActionURL rssUrl, String rssTitle)
    {
        _rssUrl = rssUrl;
        _rssTitle = rssTitle;
    }

    public boolean shouldIncludeHeader()
    {
        return _includeHeader;
    }

    public void setIncludeHeader(boolean includeHeader)
    {
        _includeHeader = includeHeader;
    }

    public boolean shouldIncludeLoginLink()
    {
        return _includeLoginLink;
    }

    public void setIncludeLoginLink(boolean includeLoginLink)
    {
        _includeLoginLink = includeLoginLink;
    }

    public int getMinimumWidth()
    {
        return _minimumWidth;
    }

    public void setMinimumWidth(int minimumWidth)
    {
        _minimumWidth = minimumWidth;
    }

    public String getStyleSheet()
    {
        return _styleSheet;
    }

    public void setStyleSheet(String styleSheet)
    {
        _styleSheet = styleSheet;
    }

    public String getStyles()
    {
        return _styles;
    }

    public void setStyles(String styles)
    {
        _styles = styles;
    }

    public String getScript()
    {
        return _script;
    }

    public void setScript(String script)
    {
        _script = script;
    }

    public void setShowHeader(boolean show)
    {
        _showHeader = show;
    }

    public boolean showHeader()
    {
        return _showHeader;
    }

    public List<NavTree> getNavTrail()
    {
        return _navTrail;
    }

    public void setNavTrail(List<NavTree> navTrail)
    {
        _navTrail = navTrail;
    }
}
