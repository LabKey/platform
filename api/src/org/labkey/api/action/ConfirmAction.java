package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.PropertyValue;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 17, 2007
 * Time: 12:52:55 PM
 */
public abstract class ConfirmAction<FORM> extends BaseViewAction
{
    public static final String CONFIRMACTION = ConfirmAction.class.getName();
    boolean includeHeader = true;

    protected ConfirmAction()
    {
    }

    public String getConfirmText()
    {
        return "OK";
    }

    public String getCancelText()
    {
        return "Cancel";
    }

    public ActionURL getCancelUrl()
    {
        return null;
    }

    public boolean isPopupConfirmation()
    {
        return false;
    }

    public final ModelAndView handleRequest() throws Exception
    {
        ViewContext context = HttpView.currentContext();

        FORM form = null;
        BindException errors = null;

        PropertyValue confirm = getPropertyValues().getPropertyValue("_confirm");
        PropertyValue confirmX = getPropertyValues().getPropertyValue("_confirm.x");
        boolean confirmed = confirm != null || confirmX != null;

        if (null != getCommandClass())
        {
            errors = bindParameters(getPropertyValues());
            form = (FORM)errors.getTarget();
        }

        boolean success = errors == null || !errors.hasErrors();

        if (success && null != form)
            validate(form, errors);
        success = errors == null || !errors.hasErrors();

        if (success)
        {
            if (confirmed && "POST".equals(context.getRequest().getMethod()))
            {
                success = handlePost(form, errors);

                if (success)
                {
                    ModelAndView mv = getSuccessView(form);
                    if (null != mv)
                        return mv;
                    HttpView.throwRedirect(getSuccessURL(form));
                }
            }
            else
            {
                ModelAndView confirmView = getConfirmView(form, errors);
                JspView confirmWrapper = new JspView("/org/labkey/api/action/confirmWrapper.jsp");
                confirmWrapper.addObject(CONFIRMACTION, this);
                confirmWrapper.setBody(confirmView);
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return confirmWrapper;
            }
        }

        // We failed... redirect if fail URL is specified, otherwise return the error view
        ActionURL urlFail = getFailURL(form, errors);
        if (null != urlFail)
            HttpView.throwRedirect(urlFail);

        return getFailView(form, errors);
    }


    protected String getCommandClassMethodName()
    {
        return "handlePost";
    }

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    /**
     * View with text and buttons.  Should NOT include &lt;form&gt; 
     */
    public abstract ModelAndView getConfirmView(FORM form, BindException errors) throws Exception;


    /**
     * may call throwRedirect() on success
     *
     * handlePost() can call setReshow(false) to force record to be reselected
     * return a view to display or null to call getView(form,true);
     */
    public abstract boolean handlePost(FORM form, BindException errors) throws Exception;

    public void validate(Object form, Errors errors)
    {
        validateCommand((FORM)form, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM form, Errors errors);


    public abstract ActionURL getSuccessURL(FORM form);

    // not usually used but some actions return views that close the current window etc...
    public ModelAndView getSuccessView(FORM form)
    {
        return null;
    }

    public ActionURL getFailURL(FORM form, BindException errors)
    {
        return null;
    }

    public ModelAndView getFailView(FORM form, BindException errors)
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);
        return new SimpleErrorView(errors);
    }

    // return false for no header (e.g. for a dialog)
    public boolean getIncludeHeader()
    {
        return includeHeader;
    }

    public void setIncludeHeader(boolean h)
    {
        includeHeader = h;
    }
}
