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

package org.labkey.api.view.template;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

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
        Dialog,
        Wizard,
        Body,
        Custom,  // must be handled by module
        App;

        public @Nullable HttpView<PageConfig> getTemplate(ViewContext context, ModelAndView body, PageConfig page)
        {
            return ViewService.get().getTemplate(this, context, body, page);
        }
    }

	public enum TrueFalse
	{
		Default, True, False
	}

    private final LinkedHashSet<ClientDependency> _resources = new LinkedHashSet<>();
    private final MultiValuedMap<String, String> _meta = new ArrayListValuedHashMap<>();
    private final JSONObject _portalContext = new JSONObject();

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
    private boolean _includeSearch = true;
    private int _minimumWidth = 400;
    private TrueFalse _showHeader = TrueFalse.Default;
    private List<NavTree> _navTrail;
    private AppBar _appBar;
    private FrameOption _frameOption = FrameOption.ALLOW;
    private boolean _trackingScript = true;
    private String _canonicalLink = null;
    private boolean _includePostParameters = false;

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

    public PageConfig setHelpTopic(String topic)
    {
        _helpTopic = new HelpTopic(topic);
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

    public boolean shouldIncludeSearch()
    {
        return _includeSearch;
    }

    public void setIncludeSearch(boolean includeSearch)
    {
        _includeSearch = includeSearch;
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

    public boolean shouldIncludePostParameters()
    {
        return _includePostParameters;
    }

    public void setIncludePostParameters(boolean includePostParameters)
    {
        _includePostParameters = includePostParameters;
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

    public HtmlString getPreloadTags()
    {
        final List<String> fonts = List.of(
                "/fonts/Roboto/Roboto-Regular.ttf",
                "/fonts/TitilliumWeb/TitilliumWeb-Regular.ttf",
                "/fonts/TitilliumWeb/TitilliumWeb-Bold.ttf",
                "/fonts/Roboto/Roboto-Bold.ttf");
        HtmlStringBuilder sb = HtmlStringBuilder.of();
        fonts.stream().map(PageFlowUtil::staticResourceUrl).forEach(url->
                sb.append(HtmlString.unsafe("<link rel=\"preload\" as=\"font\" type=\"font/ttf\" crossorigin href=\"")).append(url).append(HtmlString.unsafe("\">")));
        return sb.getHtmlString();
    }

    public HtmlString getMetaTags(URLHelper url)
    {
        // We want search engines to index our regular pages (with navigation) not the print versions
        if (_template == Template.Print)
            setNoIndex();

        HtmlStringBuilder sb = HtmlStringBuilder.of();

        String canonical = getCanonicalLink(url);
        if (null != canonical)
        {
            sb.append(HtmlString.unsafe("<link rel=\"canonical\" href=\""))
                    .append(canonical).append(HtmlString.unsafe("\">\n"));
        }

        if (!_meta.isEmpty())
        {
            for (Map.Entry<String, Collection<String>>  e : _meta.asMap().entrySet())
            {
                sb.append(HtmlString.unsafe("<meta name=\"")).append(e.getKey())
                        .append(HtmlString.unsafe("\" content=\"")).append(StringUtils.join(e.getValue(), ", "))
                        .append(HtmlString.unsafe("\">\n"));
            }
        }
        return sb.getHtmlString();
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

    // For now, gives a central place to render messaging
    public HtmlStringBuilder renderSiteMessages(ViewContext context)
    {
        HtmlStringBuilder messages = HtmlStringBuilder.of();

        // Keep an empty div for re-addition of dismissable messages onto the page
        messages.append(HtmlString.unsafe("<div class=\"lk-dismissable-alert-ct\">"));
        if (context != null && context.getRequest() != null)
        {
            Warnings warnings = WarningService.get().getWarnings(context);
            HttpSession session = context.getRequest().getSession(true);

            if (session.getAttribute(SESSION_WARNINGS_BANNER_KEY) == null)
                session.setAttribute(SESSION_WARNINGS_BANNER_KEY, true);

            // If the session attribute has explicitly been set to false & there are no more warnings, remove it
            else if (!(boolean) session.getAttribute(SESSION_WARNINGS_BANNER_KEY) && warnings.isEmpty())
                session.removeAttribute(SESSION_WARNINGS_BANNER_KEY);

            if (session.getAttribute(SESSION_WARNINGS_BANNER_KEY) != null &&
                    (boolean) session.getAttribute(SESSION_WARNINGS_BANNER_KEY) &&
                    !warnings.isEmpty())
            {
                messages.append(WarningService.get().getWarningsHtml(warnings, context));
            }
        }
        messages.append(HtmlString.unsafe("</div>"));

        // Display a <noscript> warning message
        messages.append(HtmlString.unsafe("<noscript>"));
        messages.append(HtmlString.unsafe("<div class=\"alert alert-warning\" role=\"alert\">JavaScript is disabled. For the full experience enable JavaScript in your browser.</div>"));
        messages.append(HtmlString.unsafe("</noscript>"));

        return messages;
    }

    public JSONObject getPortalContext()
    {
        return _portalContext;
    }
}
