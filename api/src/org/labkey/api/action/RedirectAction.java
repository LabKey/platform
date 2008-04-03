package org.labkey.api.action;

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: adamr
 * Date: September 19, 2007
 * Time: 9:15:37 AM
 *
 * Simple action that mimics old style Beehive actions (to an extent).  Handles post or get, plus redirect.
 */
public abstract class RedirectAction<FORM> extends BaseViewAction<FORM>
{
    public RedirectAction()
    {
    }

    public RedirectAction(Class<? extends FORM> commandClass)
    {
        super(commandClass);
    }

    public final ModelAndView handleRequest() throws Exception
    {
        FORM form = null;
        BindException errors = null;
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
            success = doAction(form, errors);
        }

        if (success)
            HttpView.throwRedirect(getSuccessURL(form));

        return getErrorView(form, errors);
    }


    protected String getCommandClassMethodName()
    {
        return "doAction";
    }

    public abstract ActionURL getSuccessURL(FORM form);

    public abstract boolean doAction(FORM form, BindException errors) throws Exception;

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    public void validate(Object target, Errors errors)
    {
        validateCommand((FORM)target, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM target, Errors errors);

    // Override to put up a fancier error page
    public ModelAndView getErrorView(FORM form, BindException errors) throws Exception
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);
        return new SimpleErrorView(errors);
    }
}
