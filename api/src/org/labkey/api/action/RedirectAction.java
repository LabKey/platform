/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.util.URLHelper;
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

    public abstract URLHelper getSuccessURL(FORM form);

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
