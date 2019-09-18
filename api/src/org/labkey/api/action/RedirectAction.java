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

package org.labkey.api.action;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.annotations.RemoveIn20_1;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;


/**
 * Use SimpleRedirectAction instead. This base action class will be removed in LabKey Server 20.1
 *
 * User: adamr
 * Date: September 19, 2007
 */
@Deprecated
@RemoveIn20_1
public abstract class RedirectAction<FORM> extends BaseViewAction<FORM>
{
    @Override
    public final ModelAndView handleRequest() throws Exception
    {
        BindException errors = bindParameters(getPropertyValues());
        FORM form = (FORM)errors.getTarget();
        boolean success = !errors.hasErrors();

        if (success && null != form)
        {
            validate(form, errors);
            success = !errors.hasErrors();
        }

        if (success)
        {
            URLHelper s = getURL(form, errors);
            if (null != s)
                throw new RedirectException(s);
            if (!errors.hasErrors())
            {
                Logger.getLogger(this.getClass()).warn("NULL redirect URL with no error in " + this.getClass().getName(), new NullPointerException());
                errors.reject(ERROR_MSG, "Sorry, I seem to have lost my way and don't know where to go!");
            }
        }

        return getErrorView(form, errors);
    }


    @Override
    protected String getCommandClassMethodName()
    {
        return "getURL";
    }

    public abstract @Nullable URLHelper getURL(FORM form, Errors errors) throws Exception;

    public @NotNull BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    @Override
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
