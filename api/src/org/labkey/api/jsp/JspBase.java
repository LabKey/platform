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

package org.labkey.api.jsp;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONObject;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.Button.ButtonBuilder;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.JavaScriptFragment;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRender;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.element.Input.InputBuilder;
import org.labkey.api.util.element.Select.SelectBuilder;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependencies;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.io.Writer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.util.HtmlString.EMPTY_STRING;
import static org.labkey.api.util.PageFlowUtil.filter;

/**
 * Base class for nearly all JSP pages that we use.
 * This is the place to put methods that will be useful to lots
 * of pages, regardless of what they do, or what module they are in.
 * <p/>
 * BE VERY CAREFUL NOT TO ADD POORLY NAMED METHODS TO THIS CLASS!!!!
 * <p/>
 * Do not add a method called "filter" to this class.
 */
public abstract class JspBase extends JspContext implements HasViewContext
{
    protected JspBase()
    {
        super();
    }

    private ViewContext _viewContext;
    private PageConfig _pageConfig;

    @Override
    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    @Override
    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
        _pageConfig = HttpView.currentPageConfig();
    }

    public ActionURL getActionURL()
    {
        return _viewContext.getActionURL();
    }

    public Container getContainer()
    {
        return _viewContext.getContainer();
    }

    public User getUser()
    {
        return _viewContext.getUser();
    }

    // Encoded version of the context path
    public HtmlString getContextPath()
    {
        return HtmlString.of(_viewContext.getContextPath());
    }

    /**
     * Returns an encoded URL to a resource in the webapp directory by prefixing a resource path with the context path
     * and encoding it.
     *
     * @param path Relative path to a resource in the webapp directory. Supports both "/"-prefixed and non-prefixed paths.
     * @return HtmlString containing a properly encoded URL
     */
    public HtmlString getWebappURL(String path)
    {
        return HtmlString.of(PageFlowUtil.staticResourceUrl(path));
    }

    /**
     * Returns a well-formed, single-line JavaScript include tag, of the form:<br><br>
     * {@code <script src="/<contextpath>/<path>" type="text/javascript"></script>}
     *
     * @param path Relative path to a resource in the webapp directory. Supports both "/"-prefixed and non-prefixed paths.
     * @return HtmlString containing a well-formed {@code <script>} include tag
     */
    public static HtmlString getScriptTag(String path)
    {
        return PageFlowUtil.getScriptTag(path);
    }

    /**
     * Returns a URL to an ExtJS 3.x image given a short, relative path
     * @param shortPath A path relative to /images/default/, such as "tree/folder.gif"
     * @return An HtmlString containing the full path to the image, such as "/labkey/ext-3.4.1/resources/images/default/tree/folder.gif"
     */
    public HtmlString getExt3Image(String shortPath)
    {
        return HtmlStringBuilder.of(getContextPath()).append("/").append(PageFlowUtil.extJsRoot()).append("/resources/images/default/").append(shortPath).getHtmlString();
    }

    /**
     * No-op encoding
     * Indicate that you explicitly want to include a string in the page WITHOUT encoding
     * TODO: HtmlString - Eventually, remove this method and all usages.
     *
     * Use HtmlString.unsafe() instead, or even better use h() if possible
     */
    @Deprecated
    public HtmlString text(String s)
    {
        return HtmlString.unsafe(s);
    }

    /**
     * Convenience method for asserting that a String contains valid, properly encoded HTML.
     *
     * @param s Valid, properly encoded HTML
     * @return An HtmlString that wraps the parameter without encoding
     */
    public HtmlString unsafe(String s)
    {
        return HtmlString.unsafe(s);
    }

    /**
     * Convenience method for asserting that a CharSequence contains valid, properly encoded HTML.
     *
     * @param cs Valid, properly encoded HTML
     * @return An HtmlString that wraps the parameter without encoding
     */
    public HtmlString unsafe(CharSequence cs)
    {
        return HtmlString.unsafe(cs.toString());
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public HtmlString h(String str)
    {
        return HtmlString.of(str);
    }

    /**
     * Html escape an object.toString().
     * The name comes from Embedded Ruby.
     */
    public HtmlString h(Object o)
    {
        return HtmlString.of(o == null ? null : o.toString());
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public HtmlString h(String str, boolean encodeSpace)
    {
        return HtmlString.of(str, encodeSpace);
    }

    public HtmlString h(URLHelper url)
    {
        return HtmlString.of(url == null ? null : url.toString());
    }

    // Note: If you have a stream, use JSONArray.collector()
    public JSONArray toJsonArray(Collection<?> c)
    {
        return new JSONArray(c);
    }

    public JSONObject toJsonObject(Collection<Object> c)
    {
        return new JSONObject(c);
    }

    public JSONObject toJsonObject(Map<?, ?> c)
    {
        return new JSONObject(c);
    }

    /**
     * Quotes a javascript string.
     * Returns a javascript string literal which is wrapped with ', and is properly escaped inside.
     * Note that if you think that you require double quotes (") to be escaped, then it probably means that you
     * need to HTML escape the quoted string (i.e. call "hq" instead of "q").
     * Javascript inside of element event attributes (e.g. onclick="dosomething") needs to be HTML escaped.
     * Javascript inside of &lt;script> tags should NEVER be HTML escaped.
     */
    final protected JavaScriptFragment q(String str)
    {
        return null == str ? JavaScriptFragment.NULL : JavaScriptFragment.unsafe(PageFlowUtil.jsString(str));
    }

    final protected JavaScriptFragment q(HtmlString hs)
    {
        return null == hs ? JavaScriptFragment.NULL : JavaScriptFragment.unsafe(PageFlowUtil.jsString(hs.toString()));
    }

    /**
     * Convenience method that returns a local URL as a properly escaped JavaScript identifier.
     * @param url Some URLHelper
     * @return A relative URL in a properly escaped single-quoted string literal JavaScriptFragment
     */
    final protected JavaScriptFragment q(@Nullable URLHelper url)
    {
        return q(null != url ? url.toString() : null);
    }

    final protected JavaScriptFragment q(SafeToRender str)
    {
        return null == str ? JavaScriptFragment.NULL : JavaScriptFragment.unsafe(PageFlowUtil.jsString(str.toString()));
    }

    protected HtmlString hq(String str)
    {
        return h(q(str));
    }

    /**
     * Creates a JavaScript string literal of an HTML escaped value.
     *
     * Ext, for example, will use the 'id' config parameter as an attribute value in an XTemplate.
     * The string value is inserted directly into the dom and so should be HTML encoded.
     */
    protected JavaScriptFragment qh(String str)
    {
        return JavaScriptFragment.unsafe(PageFlowUtil.qh(str));
    }

    /**
     * URL encode a string.
     */
    public String u(String str)
    {
        return PageFlowUtil.encode(str);
    }

    /**
     * Creates a JavaScript URL object for the passed in ActionURL.
     * Returns the JavaScript fragment:<br>
     * <br>
     * <code>new URL(url.getURLString());</code><br>
     * <br>
     * Using the browser native URL object is preferred over manually constructing a url string to avoid
     * URL encoding issues. Example usage:<br>
     * <br>
     * <code>
     *     // in a script block of a JSP<br>
     *     let url = <%=jsURL(new ActionURL(...))>;<br>
     *     url.searchParams.set('icecream?', 'salt &amp; straw');<br>
     *     window.location.href = url;
     * </code>
     */
    public JavaScriptFragment jsURL(@NotNull URLHelper url)
    {
        // Issue 42605: Apply path relative to current location
        return JavaScriptFragment.unsafe("new URL(" + q(url) + ", window.location.origin)");
    }


    private static final HtmlString CHECKED = HtmlString.of(" checked");

    /** Returns " checked" (if true) or "" (false) */
    public HtmlString checked(boolean checked)
    {
        return checked ? CHECKED : EMPTY_STRING;
    }


    private static final HtmlString SELECTED = HtmlString.of(" selected");

    /** Returns " selected" (if true) or "" (false) */
    public HtmlString selected(boolean selected)
    {
        return selected ? SELECTED : EMPTY_STRING;
    }

    /** Returns " selected" if a.equals(b) */
    public HtmlString selected(Object a, Object b)
    {
        return selected(Objects.equals(a,b));
    }

    /** Returns " selected" (if a.equals(b)) */
    public HtmlString selectedEq(Object a, Object b)
    {
        return selected(null==a ? null==b : a.equals(b));
    }


    private static final HtmlString DISABLED = HtmlString.of(" disabled");

    /** Returns " disabled" (if true) or "" (false) */
    public HtmlString disabled(boolean disabled)
    {
        return disabled ? DISABLED : EMPTY_STRING;
    }

    private static final HtmlString READ_ONLY = HtmlString.of(" readonly");

    /** Returns " readonly" (if true) or "" (false) */
    public HtmlString readonly(boolean readOnly)
    {
        return readOnly ? READ_ONLY: EMPTY_STRING;
    }


    private static final HtmlString ALTERNATE_ROW = HtmlString.of("labkey-alternate-row");
    private static final HtmlString ROW = HtmlString.of("labkey-row");

    // Returns "labkey-alternate-row" (true) or "labkey-row" (false)
    public HtmlString getShadeRowClass(boolean shade)
    {
        return shade ? ALTERNATE_ROW : ROW;
    }


    // Returns "labkey-alternate-row" (row is even) or "labkey-row" (row is odd)
    public HtmlString getShadeRowClass(int row)
    {
        return getShadeRowClass(row % 2 == 0);
    }


    // Obfuscate the passed in text if this user is in "demo" mode in this container
    public String id(String id)
    {
        return DemoMode.id(id, getContainer(), getUser());
    }

    public HtmlString getSpringFieldMarker()
    {
        return h(SpringActionController.FIELD_MARKER);
    }

    /**
     * Given the Class of an action in a Spring controller, returns the view URL to the action.
     *
     * @param actionClass Action class in a Spring controller
     * @return view url
     */
    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        return new ActionURL(actionClass, getContainer());
    }

    /** @return true if the UrlProvider exists. */
    public <P extends UrlProvider> boolean hasUrlProvider(Class<P> inter)
    {
        return PageFlowUtil.hasUrlProvider(inter);
    }

    /**
     * Convenience function for getting a specified <code>UrlProvider</code> interface
     * implementation, for use in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface
     */
    @Nullable
    public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return PageFlowUtil.urlProvider(inter);
    }

    public LinkBuilder iconLink(String iconCls, String tooltip, URLHelper url)
    {
        return new LinkBuilder().iconCls(iconCls).tooltip(tooltip).href(url);
    }

    public LinkBuilder link(String text)
    {
        return new LinkBuilder(text);
    }

    // Link to another action in the current container
    public LinkBuilder link(String text, @NotNull Class<? extends Controller> actionClass)
    {
        return link(text).href(actionClass, getContainer());
    }

    // Link to a URLHelper
    public LinkBuilder link(String text, @NotNull URLHelper url)
    {
        return link(text).href(url);
    }

    public InputBuilder<?> input()
    {
        return new InputBuilder();
    }

    public HtmlString generateBackButton()
    {
        return PageFlowUtil.generateBackButton();
    }

    public HtmlString generateBackButton(String text)
    {
        return PageFlowUtil.generateBackButton(text);
    }

    public ButtonBuilder button(String text)
    {
        return new ButtonBuilder(text);
    }

    public ButtonBuilder button(HtmlString html)
    {
        return new ButtonBuilder(html);
    }

    public SelectBuilder select()
    {
        return new SelectBuilder();
    }

    public @NotNull HtmlString makeHtmlId(@Nullable String s)
    {
        return PageFlowUtil.makeHtmlId(s);
    }

    public HtmlString generateReturnUrlFormField(URLHelper returnURL)
    {
        return ReturnUrlForm.generateHiddenFormField(returnURL);
    }

    public HtmlString generateReturnUrlFormField(ReturnUrlForm form)
    {
        return generateReturnUrlFormField(form.getReturnActionURL());
    }

    public void include(ModelAndView view, Writer writer) throws Exception
    {
        HttpView.currentView().include(view, writer);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String helpText)
    {
        return PageFlowUtil.popupHelp(helpText);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        if (null == title && !htmlHelpText)
            return PageFlowUtil.popupHelp(helpText);
        if (!htmlHelpText)
            return PageFlowUtil.popupHelp(HtmlString.unsafe(filter(helpText,true)), title);
        return PageFlowUtil.popupHelp(HtmlString.unsafe(helpText), title);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, HtmlString helpHtml)
    {
        return PageFlowUtil.popupHelp(helpHtml, title);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, String helpText, boolean htmlHelpText, int width)
    {
        return helpPopup(title, helpText, htmlHelpText).width(width);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, HtmlString helpHtml, int width)
    {
        return PageFlowUtil.popupHelp(helpHtml, title).width(width);
    }

    public PageFlowUtil.HelpPopupBuilder helpPopup(String title, String helpText, boolean htmlHelpText, String linkText, int width)
    {
        return helpPopup(title, helpText, htmlHelpText).link(HtmlString.of(linkText)).width(width);
    }

    public HtmlString helpLink(String helpTopic, String displayText)
    {
        return new HelpTopic(helpTopic).getSimpleLinkHtml(displayText);
    }

    // Format date using the container-configured date format and HTML filter the result
    public HtmlString formatDate(Date date)
    {
        return HtmlString.of(null == date ? "" : DateUtil.formatDate(getContainer(), date));
    }

    // Format LocalDate using the container-configured date format and HTML filter the result
    public HtmlString formatDate(LocalDate date)
    {
        return HtmlString.of(null == date ? "" : DateUtil.formatDate(getContainer(), date));
    }

    // Format date & time using the container-configured date & time format and HTML filter the result
    public HtmlString formatDateTime(Date date)
    {
        return HtmlString.of(null == date ? "" : DateUtil.formatDateTime(getContainer(), date));
    }

    // Format date & time using the specified date & time format and HTML filter the result
    public HtmlString formatDateTime(Date date, String pattern)
    {
        return HtmlString.of(null == date ? "" : DateUtil.formatDateTime(date, pattern));
    }

    public String getMessage(ObjectError e)
    {
        if (e == null)
            return "";
        return getViewContext().getMessage(e);
    }

    //
    // Spring error handling helpers
    //
    // CONSIDER: move into PageFlowUtil
    //

    public Errors getErrors(String bean)
    {
        return (Errors)getViewContext().getRequest().getAttribute(BindingResult.MODEL_KEY_PREFIX + bean);
    }

    protected List<ObjectError> _getErrorsForPath(String path)
    {
        // determine name of the object and property
        String beanName;
        String field;

        int dotPos = path.indexOf('.');
        if (dotPos == -1)
        {
            beanName = path;
            field = null;
        }
        else
        {
            beanName = path.substring(0, dotPos);
            field = path.substring(dotPos + 1);
        }

        Errors errors = getErrors(beanName);
        List<ObjectError> objectErrors = null;

        if (errors != null)
        {
            if (field != null)
            {
                if ("*".equals(field))
                {
                    objectErrors = errors.getAllErrors();
                }
                else if (field.endsWith("*"))
                {
                    objectErrors = Collections.unmodifiableList(errors.getFieldErrors(field));
                }
                else
                {
                    objectErrors = Collections.unmodifiableList(errors.getFieldErrors(field));
                }
            }

            else
            {
                objectErrors = errors.getGlobalErrors();
            }
        }
        return (null == objectErrors ? Collections.emptyList() : objectErrors);
    }

    public List<ObjectError> getErrorsForPath(String path)
    {
        List<ObjectError> l = _getErrorsForPath(path);
        // mark errors as displayed
        for (ObjectError e : l)
            _returnedErrors.put(e,path);
        return l;
    }

    public ObjectError getErrorForPath(String path)
    {
        List<ObjectError> l = _getErrorsForPath(path);
        ObjectError e = l.size() == 0 ? null : l.get(0);
        _returnedErrors.put(e,path);
        return e;
    }

    public String formatErrorsForPathStr(String path)
    {
        List<ObjectError> l = getErrorsForPath(path);
        return _formatErrorList(l, false);
    }

    public HtmlString formatErrorsForPath(String path)
    {
        return HtmlString.unsafe(formatErrorsForPathStr(path));
    }

    //Set<String> _returnedErrors = new HashSet<String>();
    IdentityHashMap<ObjectError, String> _returnedErrors = new IdentityHashMap<>();

    // For extra credit, return list of errors not returned by formatErrorsForPath() or formatErrorForPath()
    public List<ObjectError> getMissedErrors(String bean)
    {
        Errors errors = getErrors(bean);
        ArrayList<ObjectError> missed = new ArrayList<>();

        if (null != errors)
        {
            for (ObjectError e : errors.getAllErrors())
            {
                if (!_returnedErrors.containsKey(e))
                {
                    missed.add(e);
                    _returnedErrors.put(e,"missed");
                }
            }
        }
        return missed;
    }

    protected String formatMissedErrorsStr(String bean)
    {
        List<ObjectError> l = getMissedErrors(bean);
        // fieldNames==true is ugly, but these errors are probably not displayed in the right place on the form
        return _formatErrorList(l, true);
    }

    protected HtmlString formatMissedErrors(String bean)
    {
        return HtmlString.unsafe(formatMissedErrorsStr(bean));
    }

    protected HtmlString formatMissedErrors(String bean, String prefix, String suffix)
    {
        String str = formatMissedErrorsStr(bean);
        if (StringUtils.isEmpty(str))
            return HtmlString.of(str);
        else
            return HtmlString.unsafe(prefix + str + suffix);
    }

    // If errors exist, returns formatted errors in a <tr> with the specified colspan (or no colspan, for 0 or 1) followed by a blank line
    // If no errors, returns an empty string
    protected HtmlString formatMissedErrorsInTable(String bean, int colspan)
    {
        String errorHTML = formatMissedErrorsStr(bean);

        if (StringUtils.isEmpty(errorHTML))
            return HtmlString.of(errorHTML);
        else
            return HtmlString.unsafe("\n<tr><td" + (colspan > 1 ? " colspan=" + colspan : "") + ">" + errorHTML + "</td></tr>\n<tr><td" + (colspan > 1 ? " colspan=" + colspan : "") + ">&nbsp;</td></tr>");
    }

    // TODO: Should return HtmlString!
    protected String _formatErrorList(List<ObjectError> l, boolean fieldNames)
    {
        if (l.size() == 0)
            return "";
        ViewContext context = getViewContext();
        StringBuilder message = new StringBuilder();
        String br = "";
        message.append("<div class=\"labkey-error\">");
        for (ObjectError e : l)
        {
            message.append(br);
            br = "<br>";
            if (fieldNames && e instanceof FieldError)
            {
                message.append("<b>").append(h(((FieldError) e).getField())).append(":</b>&nbsp;");
            }
            message.append(h(context.getMessage(e)));
        }
        message.append("</div>");
        return message.toString();
    }

    protected enum Method
    {
        Get("view"), Post("post");

        private final String _suffix;

        Method(String suffix)
        {
            _suffix = suffix;
        }

        public String getSuffix()
        {
            return _suffix;
        }

        public String getMethod()
        {
            return name().toLowerCase();
        }
    }

    @Deprecated // Use <labkey:form> instead; it takes care of CSRF and other details.
    protected HtmlString formAction(Class<? extends Controller> actionClass, Method method)
    {
        return HtmlString.unsafe("action=\"" + h(urlFor(actionClass)) + "\" method=\"" + method.getMethod() + "\"");
    }

    // Provides a unique integer within the context of this request. Handy for generating element ids, etc. See UniqueID for caveats and warnings.
    @Deprecated // makeId() is preferred
    protected int getRequestScopedUID()
    {
        return UniqueID.getRequestScopedUID(getViewContext().getRequest());
    }

    protected String makeId(String prefix)
    {
        return _pageConfig.makeId(prefix);
    }

    // JSPs must override addClientDependencies(ClientDependencies) to add their own dependencies.
    public final LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> dependencies = new LinkedHashSet<>();
        ClientDependencies clientDependencies = new ClientDependencies(dependencies);
        addClientDependencies(clientDependencies);

        return dependencies;
    }

    // Override to add client dependencies
    @SuppressWarnings("UnusedParameters")
    public void addClientDependencies(ClientDependencies dependencies)
    {
    }

    // Returns the standard Troubleshooter warning if the current user is not a site administrator
    protected HtmlString getTroubleshooterWarning(boolean canUpdate)
    {
        return getTroubleshooterWarning(canUpdate, EMPTY_STRING, EMPTY_STRING);
    }

    // Returns the standard Troubleshooter warning plus the provided suffix if the current user is not a site administrator
    protected HtmlString getTroubleshooterWarning(boolean canUpdate, HtmlString suffix)
    {
        return getTroubleshooterWarning(canUpdate, EMPTY_STRING, suffix);
    }

    // Returns the standard Troubleshooter warning plus the provided prefix & suffix if the current user is not a site or app administrator
    protected HtmlString getTroubleshooterWarning(boolean canUpdate, HtmlString prefix, HtmlString suffix)
    {
        if (canUpdate)
            return EMPTY_STRING;
        else
            return HtmlStringBuilder.of(prefix).append(HtmlString.unsafe("<strong>Note: You have permission to read these settings but not modify them. Changes will not be saved.</strong><br>")).append(suffix).getHtmlString();
    }

    protected HtmlString getScriptNonce()
    {
        return _pageConfig.getScriptNonce();
    }

    protected void addHandler(String id, String event, String handler)
    {
        _pageConfig.addHandler(id,event,handler);
    }

    protected PageConfig getPageConfig()
    {
        return _pageConfig;
    }
}
