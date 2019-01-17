/*
 * Copyright (c) 2007-2018 LabKey Corporation
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
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.util.HelpTopic;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.urlProvider;

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

        public @Nullable HttpView<PageConfig> getTemplate(ViewContext context, ModelAndView body, PageConfig page)
        {
            return ViewService.get().getTemplate(this, context, body, page);
        }
    }

	public enum TrueFalse
	{
		Default, True, False
	}

    public static final String SESSION_WARNINGS_BANNER_KEY = "PAGE_CONFIG$SESSION_WARNINGS_BANNER_KEY";

    public static final Collection<String> STATIC_ADMIN_WARNINGS = getStaticAdminWarnings();

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
    private JSONObject _portalContext = new JSONObject();
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

    // Check warning conditions that will never change while the server is running. This will be called once per server
    // session; no need to test on every request.
    private static Collection<String> getStaticAdminWarnings()
    {
        List<String> messages = new LinkedList<>();

        Warnings warnings = Warnings.of(messages);
        WarningService.get().forEachProvider(p->p.addStaticWarnings(warnings));

        return messages;
    }

    protected final static String DISMISSAL_SCRIPT_FORMAT =
            "<script type=\"text/javascript\">\n" +
            "    (function($) {\n" +
            "        function dismissMessage() {\n" +
            "            var config = {\n" +
            "                url: %1$s,\n" +
            "                method: 'POST',\n" +
            "                success: function () {$(\".lk-dismissable-warn\").hide()},\n" +
            "                failure: LABKEY.Utils.displayAjaxErrorResponse\n" +
            "            };\n" +
            "            LABKEY.Ajax.request(config); \n" +
            "            return false;\n" +
            "        }\n" +
            "       $('body').on('click', 'a.lk-dismissable-warn-close', function() {\n" +
            "           dismissMessage();\n" +
            "       });" +
            "    })(jQuery);\n" +
            "</script>\n";

    public static void appendDismissableMessageHtml(ViewContext viewContext, List<String> warnings, StringBuilder html)
    {
        html.append("<div class=\"alert alert-warning alert-dismissable\">")
            .append("<a href=\"#\" class=\"close lk-dismissable-warn-close\" data-dismiss=\"alert\" aria-label=\"dismiss\" title=\"dismiss\">×</a>")
            .append("<div class=\"lk-dismissable-warn\">");
        appendMessageContent(warnings, html);
        html.append("</div>");
        CoreUrls coreUrls = urlProvider(CoreUrls.class);
        if (coreUrls != null)
        {
            String dismissURL = coreUrls.getDismissCoreWarningActionURL(viewContext).toString();
            html.append(String.format(DISMISSAL_SCRIPT_FORMAT, PageFlowUtil.jsString(dismissURL)));
        }
        html.append("</div>");
    }

    // For now, gives a central place to render messaging
    public String renderSiteMessages(ViewContext context)
    {
        // Collect warnings
        List<String> warningMessages = new LinkedList<>();
        User user = context.getUser();

        if (null != user && user.isInSiteAdminGroup())
            warningMessages.addAll(STATIC_ADMIN_WARNINGS);

        Warnings warnings = Warnings.of(warningMessages);
        WarningService.get().forEachProvider(p->p.addWarnings(warnings, context));

        List<String> dismissibleWarningMessages = new LinkedList<>();
        Warnings dismissibleWarnings = Warnings.of(dismissibleWarningMessages);
        WarningService.get().forEachProvider(p->p.addDismissibleWarnings(dismissibleWarnings, context));

        // Render warnings
        StringBuilder messages = new StringBuilder();

        if (!warningMessages.isEmpty())
        {
            messages.append("<div class=\"alert alert-warning\" role=\"alert\">");
            appendMessageContent(warningMessages, messages);
            messages.append("</div>");
        }

        // Keep an empty div for re-addition of dismissable messages onto the page
        messages.append("<div class=\"lk-dismissable-alert-ct\">");
        if (context != null && context.getRequest() != null)
        {
            HttpSession session = context.getRequest().getSession(true);

            if (session.getAttribute(SESSION_WARNINGS_BANNER_KEY) == null)
                session.setAttribute(SESSION_WARNINGS_BANNER_KEY, true);

            // If the sesstion attribute has explicitly been set to false & there are no more warnings, remove it
            else if (!(boolean) session.getAttribute(SESSION_WARNINGS_BANNER_KEY) && dismissibleWarningMessages.isEmpty())
                session.removeAttribute(SESSION_WARNINGS_BANNER_KEY);

            if (session.getAttribute(SESSION_WARNINGS_BANNER_KEY) != null &&
                    (boolean) session.getAttribute(SESSION_WARNINGS_BANNER_KEY) &&
                    !dismissibleWarningMessages.isEmpty())
            {
                appendDismissableMessageHtml(context, dismissibleWarningMessages, messages);
            }
        }
        messages.append("</div>");

        // Display a <noscript> warning message
        messages.append("<noscript>");
        messages.append("<div class=\"alert alert-warning\" role=\"alert\">JavaScript is disabled. For the full experience enable JavaScript in your browser.</div>");
        messages.append("</noscript>");

        return messages.toString();
    }

    public static void appendMessageContent(List<String> messages, StringBuilder html)
    {
        if (!messages.isEmpty())
        {
            if (messages.size() == 1)
                html.append(messages.get(0));
            else
            {
                html.append("<ul>");
                for (String msg : messages)
                    html.append("<li>").append(msg).append("</li>");
                html.append("</ul>");
            }
        }
    }

    public JSONObject getPortalContext()
    {
        return _portalContext;
    }
}
