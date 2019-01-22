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

package org.labkey.api.action;

import org.apache.log4j.Logger;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;


/**
 * Base class for action that never want to serve up a regular HTTP response page, and always
 * want to redirect the client to a different URL, typically after performing some work.
 *
 * User: adamr
 * Date: September 19, 2007
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
        {
            URLHelper s = getSuccessURL(form);
            if (null != s)
                throw new RedirectException(s);
            Logger.getLogger(this.getClass()).warn("NULL redirect URL in action " + this.getClass().getName(), new NullPointerException());
            errors.reject(ERROR_MSG, "Sorry, I seem to have lost my way and don't know where to go!");
        }

        return getErrorView(form, errors);
    }


    protected String getCommandClassMethodName()
    {
        return "getSuccessURL";
    }

    // TODO: Pass in errors for failure cases?
    public abstract URLHelper getSuccessURL(FORM form);

    @Deprecated // TODO: We want to migrate most RedirectActions to FormHandlerAction and eliminate this method. See #36532.
    public boolean doAction(FORM form, BindException errors) throws Exception
    {
        return true;
    }

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    public void validate(Object target, Errors errors)
    {
        validateCommand((FORM)target, errors);
    }

    /* Generic version of validate */
    public void validateCommand(FORM target, Errors errors)
    {
    }

    // Override to put up a fancier error page
    public ModelAndView getErrorView(FORM form, BindException errors)
    {
        getPageConfig().setTemplate(PageConfig.Template.Dialog);
        return new SimpleErrorView(errors);
    }
}
