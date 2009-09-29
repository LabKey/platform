package org.labkey.api.action;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.springframework.beans.PropertyValues;
import org.apache.commons.lang.StringUtils;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Sep 28, 2009
 * Time: 4:09:42 PM
 *
 * This is a hybrid Api/Form action.
 *  GET is like SimpleViewForm
 *  POST is like ExtFormAction
 *
 *  Do we need ExtFormAction as well?
 */
public abstract class FormApiAction<FORM> extends ExtFormAction<FORM> implements NavTrailAction
{
    protected boolean _print = false;

    @Override
    protected ModelAndView handleGet() throws Exception
    {
        FORM form = null;
        BindException errors = null;
        if (null == getCommandClass())
            errors = new BindException(new Object(), "command");
        else
            errors = bindParameters(getPropertyValues());

        form = (FORM)errors.getTarget();
        validate(form, errors);

        ModelAndView v;

        if (null != StringUtils.trimToNull((String) getProperty("_print")) ||
            null != StringUtils.trimToNull((String) getProperty("_print.x")))
            v = getPrintView(form, errors);
        else
            v = getView(form, errors);
        return v;
    }

    public abstract ModelAndView getView(FORM form, BindException errors) throws Exception;

    public ModelAndView getPrintView(FORM form, BindException errors) throws Exception
    {
        _print = true;
        return getView(form, errors);
    }

    public BindException bindParameters(PropertyValues pvs) throws Exception
    {
        return SimpleViewAction.defaultBindParameters(getCommand(), getCommandName(), pvs);
    }
}
