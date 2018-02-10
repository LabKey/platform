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

package org.labkey.api.view.template;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Sep 21, 2005
 * Time: 4:26:39 PM
 *
 * This class is used by an action to configure the template page.
 */
public class PageConfig
{
    /**
     * Warning: Any changes to this enum require corresponding changes in ViewServiceImpl
     */
    public enum Template
    {
        None,
        Home,
        Print,
        Framed, // In an Iframe same as print except tries to maintain template on navigate
        Dialog,
        Wizard,
        Body,
        Custom,  // must be handled by module
        App;

        public HttpView<PageConfig> getTemplate(ViewContext context, ModelAndView body, PageConfig page)
        {
            return ViewService.get().getTemplate(this, context, body, page);
        }
    }

	public enum TrueFalse
	{
		Default, True, False
	}

    private Template _template = Template.Home;
    private String _title;
    private HelpTopic _helpTopic;
    private boolean _appendPathToTitle;
    private Module _moduleOwner;
    private String _focus = null;
    private boolean _showPrintDialog = false;
    private String _anchor;
    private ActionURL _rssUrl = null;
    private String _rssTitle = null;
    private boolean _includeLoginLink = true;
    private int _minimumWidth = 400;
    private LinkedHashSet<ClientDependency> _resources = new LinkedHashSet<>();
    private TrueFalse _showHeader = TrueFalse.Default;
    private List<NavTree> _navTrail;
    private AppBar _appBar;
    private MultiValuedMap<String, String> _meta = new ArrayListValuedHashMap<>();
    private FrameOption _frameOption = FrameOption.ALLOW;
    private boolean _trackingScript = true;
    private String _canonicalLink = null;
    private List<String> _warningMessages = null;
    private List<String> _dismissibleWarningMessages = null;
    private boolean _skipPHIBanner = false;

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

    public @NotNull HelpTopic getHelpTopic()
    {
        return _helpTopic == null ? HelpTopic.DEFAULT_HELP_TOPIC : _helpTopic;
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

    public void setFocusId(String focusId)
    {
        _focus = "getElementById('" + focusId + "')";
    }

    public String getFocus()
    {
        return _focus;
    }

    public void setShowPrintDialog(boolean showPrintDialog)
    {
        _showPrintDialog = showPrintDialog;
    }

    public boolean getShowPrintDialog()
    {
        return _showPrintDialog;
    }

    @Nullable
    public String getAnchor(URLHelper url)
    {
        String anchor = _anchor;
        if (null == StringUtils.trimToNull(anchor))
            anchor = StringUtils.trimToNull(url.getParameter("_anchor"));
        return anchor;
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

    public void setShowHeader(boolean show)
    {
        _showHeader = show ? TrueFalse.True : TrueFalse.False;
    }

    public TrueFalse showHeader()
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

    public AppBar getAppBar()
    {
        return _appBar;
    }

    public void setAppBar(AppBar appBar)
    {
        _appBar = appBar;
    }


    public void addMetaTag(String name, String value)
    {
        if (!_meta.containsMapping(name,value))
            _meta.put(name,value);
    }
    

    public void setMetaTag(String name, String value)
    {
        _meta.remove(name);
        if (null != value)
            _meta.put(name,value);
    }


    public void setNoIndex()
    {
        _meta.removeMapping("robots", "index");
        addMetaTag("robots", "noindex");
    }


    public void setNoFollow()
    {
        _meta.removeMapping("robots", "follow");
        addMetaTag("robots", "nofollow");
    }


    public void setCanonicalLink(String link)
    {
        _canonicalLink = link;
    }


    String[] ignoreParameters = new String[] {"_dc", "_template", "_print", "_debug", "_docid", DataRegion.LAST_FILTER_PARAM};

    @Nullable
    private String getCanonicalLink(URLHelper current)
    {
        if (null != _canonicalLink)
            return _canonicalLink;
        if (null == current)
            return null;
        URLHelper u = null;
        if (current instanceof ActionURL && !((ActionURL)current).isCanonical())
            u = current.clone();
        for (String p : ignoreParameters)
        {
            if (null != current.getParameter(p))
                u = (null==u ? current.clone() : u).deleteParameter(p);
        }
        return null == u ? null : u.getURIString();
    }


    public String getMetaTags(URLHelper url)
    {
        // We want search engines to index our regular pages (with navigation) not the print versions
        if (_template == Template.Print)
            setNoIndex();

        StringBuilder sb = new StringBuilder();

        String canonical = getCanonicalLink(url);
        if (null != canonical)
        {
            sb.append("<link rel=\"canonical\" href=\"").append(PageFlowUtil.filter(canonical)).append("\">\n");
        }

        if (!_meta.isEmpty())
        {
            for (Map.Entry<String, Collection<String>>  e : _meta.asMap().entrySet())
            {
                sb.append("    <meta name=\"").append(PageFlowUtil.filter(e.getKey())).append("\" content=\"");
                sb.append(PageFlowUtil.filter(StringUtils.join(e.getValue(), ", ")));
                sb.append("\">\n");
            }
        }
        return sb.toString();
    }

    public enum FrameOption
    {
        ALLOW, SAMEORIGIN, DENY
    }
    
    public void setFrameOption(FrameOption option)
    {
        _frameOption = option;
    }

    public FrameOption getFrameOption()
    {
        return null==_frameOption?FrameOption.ALLOW:_frameOption;
    }

    public void setAllowTrackingScript(TrueFalse opt)
    {
        _trackingScript = opt != TrueFalse.False;
    }

    public boolean getAllowTrackingScript()
    {
        return _trackingScript;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return _resources;
    }

    public void addClientDependencies(Set<ClientDependency> resources)
    {
        _resources.addAll(resources);
    }

    public void addClientDependency(ClientDependency resource)
    {
        _resources.add(resource);
    }

    public void addWarningMessage(String html)
    {
        if (_warningMessages == null)
            _warningMessages = new ArrayList<>();
        _warningMessages.add(html);
    }

    public void addWarningMessages(Collection<String> messages)
    {
        if (_warningMessages == null)
            _warningMessages = new ArrayList<>();
        _warningMessages.addAll(messages);
    }

    @NotNull
    public List<String> getWarningMessages()
    {
        if (_warningMessages != null)
            return _warningMessages;
        return Collections.emptyList();
    }

    public void addDismissibleWarningMessage(String html)
    {
        if (_dismissibleWarningMessages == null)
            _dismissibleWarningMessages = new ArrayList<>();
        _dismissibleWarningMessages.add(html);
    }

    public void addDismissibleWarningMessages(Collection<String> messages)
    {
        if (_dismissibleWarningMessages == null)
            _dismissibleWarningMessages = new ArrayList<>();
        _dismissibleWarningMessages.addAll(messages);
    }

    @NotNull
    public List<String> getDismissibleWarningMessages()
    {
        if (_dismissibleWarningMessages != null)
            return _dismissibleWarningMessages;
        return Collections.emptyList();
    }

    public boolean isSkipPHIBanner()
    {
        return _skipPHIBanner;
    }

    public void setSkipPHIBanner(boolean skipPHIBanner)
    {
        _skipPHIBanner = skipPHIBanner;
    }

}
