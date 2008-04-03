package org.labkey.api.jsp;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.JspView;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.UrlProvider;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.validation.ObjectError;
import org.springframework.validation.Errors;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import javax.servlet.http.HttpServlet;
import javax.servlet.jsp.HttpJspPage;
import java.io.Writer;
import java.util.*;

/**
 * Base class for nearly all JSP pages that we use.
 * This is the place to put methods that will be useful to lots
 * of pages, regardless of what they do, or what module they are in.
 * <p/>
 * BE VERY CAREFUL NOT TO ADD POORLY NAMED METHODS TO THIS CLASS!!!!
 * <p/>
 * Do not add a method called "filter" to this class.
 */
abstract public class JspBase extends HttpServlet implements HttpJspPage, HasViewContext
{
    public void jspInit()
    {
    }

    public void jspDestroy()
    {
    }

    ViewContext _viewContext;

    public ViewContext getViewContext()
    {
        return _viewContext;
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    public Object getModelBean()
    {
        return HttpView.currentModel();
    }

    /**
     * Html escape an object.toString().
     * The name comes from Embedded Ruby.
     */
    public String h(Object o)
    {
        return PageFlowUtil.filter(o);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str)
    {
        return PageFlowUtil.filter(str);
    }

    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str, boolean encodeSpace)
    {
        return PageFlowUtil.filter(str, encodeSpace);
    }

    public String h(URLHelper url)
    {
        return PageFlowUtil.filter(url);
    }

    /**
     * Quotes a javascript string.
     * Returns a javascript string literal which is wrapped with ', and is properly escaped inside.
     * Note that if you think that you require double quotes (") to be escaped, then it probably means that you
     * need to HTML escape the quoted string (i.e. call "hq" instead of "q").
     * Javascript inside of element event attributes (e.g. onclick="dosomething") needs to be HTML escaped.
     * Javascript inside of &lt;script> tags should NEVER be HTML escaped.
     */
    protected String q(String str)
    {
        str = StringUtils.replace(str, "\\", "\\\\");
        str = StringUtils.replace(str, "'", "\\'");
        str = StringUtils.replace(str, "\n", "\\n");
        str = StringUtils.replace(str, "\r", "\\r");
        return "'" + str + "'";
    }

    protected String hq(String str)
    {
        return h(q(str));
    }

    /**
     * URL encode a string.
     */
    public String u(String str)
    {
        return PageFlowUtil.encode(str);
    }


    /**
     * Use controller class version instead for Spring controllers.
     * 
     * @param action an enum named to match a controller action
     * @return view url
     */
    @Deprecated
    public ActionURL urlFor(Enum action)
    {
        return getViewContext().getContainer().urlFor(action);
    }

    /**
     * Given the Class of an action in a Spring controller, returns the view URL to the action.
     *
     * @param action Action in a Spring controller
     * @return view url
     */
    public ActionURL urlFor(Class<? extends Controller> action)
    {
        return new ActionURL(action, getViewContext().getContainer());
    }

    /**
     * Convenience function for getting a specified <code>UrlProvider</code> interface
     * implementation, for use in writing URLs implemented in other modules.
     *
     * @param inter interface extending UrlProvider
     * @return an implementation of the interface
     */
    public <P extends UrlProvider> P urlProvider(Class<P> inter)
    {
        return PageFlowUtil.urlProvider(inter);
    }

    public String textLink(String text, String href, String id)
    {
        return PageFlowUtil.textLink(text, href, id);
    }

    public String textLink(String text, String href)
    {
        return PageFlowUtil.textLink(text, href, null, null);
    }

    public String textLink(String text, String href, String onClickScript, String id)
    {
        return PageFlowUtil.textLink(text, href, onClickScript, id);
    }

    public String textLink(String text, ActionURL url)
    {
        return PageFlowUtil.textLink(text, url);
    }

    /**
     * Renders a button wrapped in an &lt;a> tag.
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public String buttonLink(String text, String href)
    {
        return PageFlowUtil.buttonLink(text, href);
    }

    /**
     * Renders a button wrapped in an &lt;a> tag.
     * Consider: is there any way to name this method in such a way as to
     * make the order of parameters unambiguous?
     */
    public String buttonLink(String text, String href, String onClickScript)
    {
        return PageFlowUtil.buttonLink(text, href, onClickScript);
    }

    public String buttonLink(String text, ActionURL href)
    {
        return PageFlowUtil.buttonLink(text, href);
    }

    public String buttonLink(String text, ActionURL href, String onClickScript)
    {
        return PageFlowUtil.buttonLink(text, href, onClickScript);
    }

    /**
     * Renders a &lt;input type="image" tag.
     */
    public String buttonImg(String text)
    {
        return "<input value=\"" + text + "\" type=\"image\" src=\"" + PageFlowUtil.buttonSrc(text) + "\">";
    }

    public void include(ModelAndView view, Writer writer) throws Exception
    {
        HttpView.currentView().include(view, writer);
    }

    public String buttonImg(String text, String onClickScript)
    {
        return "<input value=\"" + text + "\" type=\"image\" src=\"" + PageFlowUtil.buttonSrc(text)
                + "\" onclick=\"" + onClickScript + "\">";
    }
    
    public String helpPopup(String helpText)
    {
        return helpPopup(null, helpText, false);
    }

    public String helpPopup(String title, String helpText)
    {
        return helpPopup(title, helpText, false);
    }

    public String helpPopup(String title, String helpText, boolean htmlHelpText)
    {
        return PageFlowUtil.helpPopup(title, helpText, htmlHelpText);
    }

    public String formatDate(Date date)
    {
        return DateUtil.formatDate(date);
    }

    public String formatDateTime(Date date)
    {
        return DateUtil.formatDateTime(date);
    }

    public String getMessage(ObjectError e)
    {
        if (e == null)
            return "";
        return getViewContext().getMessage(e);
    }

    JspView _me = null;
    
    JspView getView()
    {
        if (null == _me)
            _me = (JspView)HttpView.currentView();
        return _me;
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
        List objectErrors = null;

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
                    objectErrors = errors.getFieldErrors(field);
                }
                else
                {
                    objectErrors = errors.getFieldErrors(field);
                }
            }

            else
            {
                objectErrors = errors.getGlobalErrors();
            }
        }
        return (List<ObjectError>)(null == objectErrors ? Collections.emptyList() : objectErrors);
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

    public String formatErrorsForPath(String path)
    {
        List<ObjectError> l = getErrorsForPath(path);
        return _formatErrorList(l, false);
    }

    //Set<String> _returnedErrors = new HashSet<String>();
    IdentityHashMap<ObjectError,String> _returnedErrors = new IdentityHashMap<ObjectError,String>();

    // For extra credit, return list of errors not returned by formatErrorsForPath() or formatErrorForPath()
    public List<ObjectError> getMissedErrors(String bean)
    {
        Errors errors = getErrors(bean);
        ArrayList<ObjectError> missed = new ArrayList<ObjectError>();

        if (null != errors)
        {
            for (ObjectError e : (List<ObjectError>)errors.getAllErrors())
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

    public String formatMissedErrors(String bean)
    {
        List<ObjectError> l = getMissedErrors(bean);
        // fieldNames==true is ugly, but these errors are probably not displayed in the right place on the form
        return _formatErrorList(l, true);
    }

    protected String _formatErrorList(List<ObjectError> l, boolean fieldNames)
    {
        if (l.size() == 0)
            return "";
        ViewContext context = getViewContext();
        StringBuffer message = new StringBuffer();
        String br = "";
        message.append("<span style=\"color:red;\">");
        for (ObjectError e : l)
        {
            message.append(br);
            br = "<br>";
            if (fieldNames && e instanceof FieldError)
            {
                message.append("<b>" + h(((FieldError)e).getField()) + ":</b>&nbsp;");
            }
            message.append(h(context.getMessage(e)));
        }
        message.append("</span>");
        return message.toString();
    }
}