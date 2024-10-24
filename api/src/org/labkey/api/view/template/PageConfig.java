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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.DataRegion;
import org.labkey.api.module.Module;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRender;
import org.labkey.api.util.SafeToRenderBuilder;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewService;
import org.labkey.api.view.ViewServlet;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.labkey.api.util.PageFlowUtil.jsString;
import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

/**
 * This class is used by an action to configure the template page.
 */
public class PageConfig
{
    private static final String HEADER_NONCE = "org.labkey.filters.ContentSecurityPolicyFilter#NONCE";  // needs to match ContentSecurityPolicyFilter.HEADER_NONCE

    private static final Logger LOG = LogHelper.getLogger(PageConfig.class, "Log problems generating pages.");

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

    private record EventHandler(String id, String selector, @NotNull String event, @NotNull String handler) {
        public EventHandler {
            assert (null==id) != (null==selector) : "exactly one of id or selector must be non-null";
            assert !StringUtils.containsWhitespace(id) : "id should not contain any whitespace";
            assert !StringUtils.containsWhitespace(event) : "event name should not contain any whitespace";
            assert !event.startsWith("on") : "event name should not include the 'on' prefix";
        }

        public String getKey()
        {
            return null!=id ? ("#" + id + "." + event) : (selector + "." + event);
        }
    }

    // collected javascript handlers

    // Cap the total number of handlers to prevent excessive memory use. Typical key/event/handler strings are
    // a combined 100-200 characters
    private static final int MAX_EVENT_HANDLERS = 100_000;
    private final Map<String,EventHandler> _eventHandlers = new LinkedHashMap<>();
    private final List<String> _onDomLoaded = new ArrayList<>();
    private final List<String> _onDocumentLoaded = new ArrayList<>();


    private final HttpServletRequest _request;
    private final AtomicInteger _uid;       // request counter
    private final String _sid;              // session counter value

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
    private boolean _includePermissions = false;
    private boolean _includeInheritableFormats = false;

    public final Date createTime = new Date();
    public final Throwable createThrowable = new Throwable();

    /* TODO make private */
    public PageConfig(HttpServletRequest request)
    {
        _request = request;
        UniqueID.initializeRequestScopedUID(_request);

        _uid = (AtomicInteger)_request.getAttribute(ViewServlet.REQUEST_UID_COUNTER);
        // kinda random looking hex value, but takes a while to repeat
        _sid = String.format("%04x", UniqueID.getSessionScopedUID(_request));
    }

    /* TODO make private */
    public PageConfig(HttpServletRequest request, String title)
    {
        this(request);
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
        _focus = focusId;
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
    public String getAnchor()
    {
        String anchor = _anchor;
        if (null == StringUtils.trimToNull(anchor))
        {
            var context = HttpView.currentContext();
            if (null != context && null != context.getActionURL())
                anchor = StringUtils.trimToNull(context.getActionURL().getParameter("_anchor"));
        }
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

    public boolean isIncludePermissions()
    {
        return _includePermissions;
    }

    public boolean isIncludeInheritableFormats()
    {
        return _includeInheritableFormats;
    }

    public void setIncludePermissions(boolean includePermissions)
    {
        _includePermissions = includePermissions;
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


    String[] ignoreParameters = new String[] {"_dc", "_template", "_print", "_debug", "_docid", "_test", DataRegion.LAST_FILTER_PARAM};

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
        // Preloading is sometimes great but is a waste if the resource isn't actually used in a way
        // that impacts the initial page render. Need to refine the list to just the ones that will be used
        // above the fold if we want to reintroduce this. Largely moot after the initial render due to caching.
        final List<String> fonts = List.of(
//                "/fonts/Roboto/Roboto-Regular.ttf",
//                "/fonts/TitilliumWeb/TitilliumWeb-Regular.ttf",
//                "/fonts/TitilliumWeb/TitilliumWeb-Bold.ttf",
//                "/fonts/Roboto/Roboto-Bold.ttf"
        );
        HtmlStringBuilder sb = HtmlStringBuilder.of();
        //noinspection RedundantOperationOnEmptyContainer
        fonts.stream().map(PageFlowUtil::staticResourceUrl).forEach(url->
            sb.unsafeAppend("<link rel=\"preload\" as=\"font\" type=\"font/ttf\" crossorigin href=\"")
                .append(url)
                .unsafeAppend("\">")
        );
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
            sb.unsafeAppend("<link rel=\"canonical\" href=\"")
                .append(canonical).unsafeAppend("\">\n");
        }

        if (!_meta.isEmpty())
        {
            for (Map.Entry<String, Collection<String>>  e : _meta.asMap().entrySet())
            {
                sb.unsafeAppend("<meta name=\"").append(e.getKey())
                    .unsafeAppend("\" content=\"").append(StringUtils.join(e.getValue(), ", "))
                    .unsafeAppend("\">\n");
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

        // Suppress warnings in print mode
        if (_template != Template.Print)
        {
            // Keep an empty div for re-addition of dismissible messages onto the page
            messages.unsafeAppend("<div class=\"lk-dismissable-alert-ct\">");
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
            messages.unsafeAppend("</div>");

            // Display a <noscript> warning message
            messages.unsafeAppend("<noscript>");
            messages.unsafeAppend("<div class=\"alert alert-warning\" role=\"alert\">JavaScript is disabled. For the full experience enable JavaScript in your browser.</div>");
            messages.unsafeAppend("</noscript>");
        }

        return messages;
    }

    public JSONObject getPortalContext()
    {
        return _portalContext;
    }


    /** Helper to create unique id's for HTML elements.  Is similar to the .jsp pattern:
     *      var id = "prefix" + getRequestScopedUID();
     *  Instead in JSP use:
     *      var id = makeId("prefix");
     *  or in JAVA:
     *     var id = config.makeId("prefix");
     * @param prefix non-empty String
     * @return unique element id
     */
    public String makeId(String prefix)
    {
        return prefix + _sid + _uid.incrementAndGet(); // we can concatenate without a separator because _sid is fixed width
    }


    @NotNull
    public static String getScriptNonceHeader(HttpServletRequest request)
    {
        String nonce = (String)request.getAttribute(HEADER_NONCE);
        if (nonce != null)
            return nonce;
        nonce = GUID.makeHash();
        request.setAttribute(HEADER_NONCE, nonce);
        return nonce;
    }


    /* helpers for complying with strict Content-Security-Policy */
    @NotNull
    public HtmlString getScriptNonce()
    {
        return HtmlString.of(getScriptNonceHeader(_request));
    }


    public HtmlString getScriptTagStart()
    {
        HtmlString nonce = getScriptNonce();
        return HtmlStringBuilder.of(HtmlString.unsafe("\n<script type=\"text/javascript\" nonce=\"")).append(nonce).unsafeAppend("\">").getHtmlString();
    }


    public SafeToRender buildScriptTagBlock(JavaScriptFragment javaScriptFragment)
    {
        SafeToRenderBuilder scriptBuilder = SafeToRenderBuilder.of();
        scriptBuilder.append(HttpView.currentPageConfig().getScriptTagStart());
        scriptBuilder.append(HtmlString.unsafe("\n"));
        scriptBuilder.append(javaScriptFragment);
        scriptBuilder.append(HtmlString.unsafe("\n</script>\n"));
        return scriptBuilder.getSafeToRender();
    }



    private void _addHandler(EventHandler eh)
    {
        if (_eventHandlers.size() <= MAX_EVENT_HANDLERS)
        {
            if (_eventHandlers.size() == MAX_EVENT_HANDLERS)
            {
                LOG.error("Limit of " + MAX_EVENT_HANDLERS + " JavaScript event handlers reached. Subsequent handlers will be dropped. Current handler for " + eh.event + ": " + eh.handler);
            }
            var prev = _eventHandlers.put(eh.getKey(), eh);
            assert null == prev || prev.handler.equals(eh.handler) : "Duplicate handler registered. event:" + eh.getKey() + " handler:" + eh.handler();
        }
    }


    /**
     *  NOTE element.addListener(function) is not the same as element.onclick=function!
     * This is for onevent handlers
     */
    public void addHandler(String id, String event, @Nullable String handler)
    {
        if (StringUtils.isBlank(id) || StringUtils.isBlank(event))
            throw new IllegalArgumentException();
        if (StringUtils.isBlank(handler))
            return;
        _addHandler(new EventHandler(id,null,event,handler));
    }


    /**
     *   When a page wants to attach an event handler to a lot of elements, it may be
     *   more efficient to specify a selector than a long list of ids.
     *   All elements that match this selector must have the same handler!
     */
    public void addHandlerForQuerySelector(String selector, String event, String handler)
    {
        if (StringUtils.isBlank(selector) || StringUtils.isBlank(event))
            throw new IllegalArgumentException();
        if (StringUtils.isBlank(handler))
            return;
        _addHandler(new EventHandler(null, selector, event,handler));
    }


    public void addDocumentLoadHandler(JavaScriptFragment jsf)
    {
        if (null != jsf)
        {
            String s = jsf.toString();
            if (isNotBlank(s))
                _onDocumentLoaded.add(s);
        }
    }


    public void addDOMContentLoadedHandler(JavaScriptFragment jsf)
    {
        if (null != jsf)
        {
            String s = jsf.toString();
            if (isNotBlank(s))
                _onDomLoaded.add(s);
        }
    }


    public void endOfBodyScript(Writer out) throws IOException
    {
        // IMMEDIATE

        // DOM CONTENT LOADED

        if (AppProps.getInstance().isDevMode())
        {
            Map<String, EventHandler> eventMap = new HashMap<>();
            for (EventHandler h : _eventHandlers.values())
            {
                final String eventId = h.getKey();
                EventHandler prev = eventMap.put(eventId, h);
                if (null != prev && !StringUtils.equals(prev.handler, h.handler))
                    LOG.error("Malformed page. Multiple JavaScript handlers defined for the same '<element_id>#<event>': " + eventId);
            }
        }

        out.write("(function() {\n"); // anonymous scope

        out.write("function _on_dom_content_loaded_(){");
        {
            out.write("const A = function(a,b,c){LABKEY.Utils.attachEventHandler(a,b,c,1);}\n");
            // NOTE: there can be lots of handlers, this is simple de-duping
            HashMap<String, Integer> codeMap = new HashMap<>();
            for (var l : _eventHandlers.values())
            {
                var index = codeMap.size();
                var handler = StringUtils.appendIfMissing(l.handler, ";");
                var prev = codeMap.putIfAbsent(handler, index);
                if (null == prev)
                    out.write("const h" + index + "=function (){\n" + handler + "\n};\n"); // newlines make it easier to set breakpoints
                index = requireNonNullElse(prev, index);
                if (null != l.id)
                    out.write("A(" + jsString(l.id) + "," + jsString(l.event) + ",h" + requireNonNullElse(prev, index) + ");\n");
                else
                    out.write("LABKEY.Utils.attachEventHandlerForQuerySelector(" + jsString(l.selector) + "," + jsString(l.event) + ",h" + requireNonNullElse(prev, index) + ",1);\n");
            }
            _eventHandlers.clear();

            for (var handler : _onDomLoaded)
            {
                out.write(handler);
                out.write("\n");
            }
            _onDomLoaded.clear();
        }
        out.write("}\n");   // _on_dom_content_loaded_

        // LOAD

        out.write("function _on_document_loaded_(){\n");
        {
            for (var handler : _onDocumentLoaded)
            {
                out.write(handler);
                out.write("\n");
            }
            _onDocumentLoaded.clear();

            if (isNotEmpty(getFocus()))
                out.write("const elFocus=document.getElementById(" + jsString(getFocus().trim()) + "); elFocus?elFocus.focus():null;\n");
            if (getShowPrintDialog())
                out.write("window.print();\n");
            String anchor = getAnchor();
            if (isNotEmpty(anchor))
                out.write("window.location.href = '#' + " + jsString(anchor.trim()) + ";\n");
        }
        out.write("}\n");   // _on_document_loaded_


        out.write("""
            if (document.readyState !== "loading")
            {
                _on_dom_content_loaded_();
                if (document.readyState === "complete")
                    _on_document_loaded_();
            }
            document.addEventListener('readystatechange', function()
            {
                if (document.readyState === 'interactive')
                    _on_dom_content_loaded_();
                else if (document.readyState === 'complete')
                    _on_document_loaded_();
            });
            """
        );

        out.write("})();\n"); // end of anonymous scope
    }
}
