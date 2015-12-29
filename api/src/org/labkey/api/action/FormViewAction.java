/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.labkey.api.miniprofiler.MiniProfiler;
import org.labkey.api.miniprofiler.Timing;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * User: matthewb
 * Date: May 17, 2007
 * Time: 12:52:55 PM
 *
 * Is this better than BaseCommandController?  Probably not, but it understands TableViewForm.
 *
 * CONSIDER: make a subclass of BaseCommandController
 */
public abstract class FormViewAction<FORM> extends BaseViewAction<FORM> implements NavTrailAction
{
    private boolean _reshow = false;

    public FormViewAction()
    {
    }

    public FormViewAction(Class<? extends FORM> commandClass)
    {
        super(commandClass);
    }

    protected void setReshow(boolean reshow)
    {
        _reshow = reshow;
    }

    protected boolean getReshow()
    {
        return _reshow;
    }

    public final ModelAndView handleRequest() throws Exception
    {
        FORM form;
        BindException errors;
        try (Timing ignored = MiniProfiler.step("bind"))
        {
            if (null != getCommandClass())
            {
                errors = bindParameters(getPropertyValues());
                form = (FORM)errors.getTarget();
            }
            else
            {
                // If the action has not specified a generic form, we should not hand them
                // a null BindException -- just new one up
                form = (FORM)new Object();
                errors = new NullSafeBindException(form, getCommandName());
            }
        }

        return handleRequest(form, errors);
    }

    public ModelAndView handleRequest(FORM form, BindException errors) throws Exception
    {
        boolean success = errors == null || !errors.hasErrors();

        if ("POST".equals(getViewContext().getRequest().getMethod()))
        {
            setReshow(true);

            try (Timing ignored = MiniProfiler.step("validate"))
            {
                if (success && null != form)
                    validate(form, errors);
            }
            success = errors == null || !errors.hasErrors();

            try (Timing ignored = MiniProfiler.step("handlePost"))
            {
                if (success)
                    success = handlePost(form, errors);
            }

            if (success)
            {
                URLHelper url = getSuccessURL(form);
                if (null != url)
                    return HttpView.redirect(url);
                try (Timing ignored = MiniProfiler.step("createView"))
                {
                    ModelAndView successView = getSuccessView(form);
                    if (null != successView)
                        return successView;
                }
            }
        }

        try (Timing ignored = MiniProfiler.step("createView"))
        {
            return getView(form, getReshow(), errors);
        }
    }


    protected String getCommandClassMethodName()
    {
        return "handlePost";
    }

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        return defaultBindParameters(getCommand(), m);
    }


    public void validate(Object target, Errors errors)
    {
        if (target instanceof HasValidator)
        {
            ((HasValidator)target).validate(errors);
            if (0 < errors.getErrorCount())
                return;
        }
        validateCommand((FORM)target, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM target, Errors errors);
    
    public abstract ModelAndView getView(FORM form, boolean reshow, BindException errors) throws Exception;


    /**
     * may call throwRedirect() on success
     * 
     * handlePost() can call setReshow(false) to force record to be reselected
     * return a view to display or null to call getView(form, true);
	 *
	 * return true to indicate success, false will call getView(reshow=true)
     */
    public abstract boolean handlePost(FORM form, BindException errors) throws Exception;

    public abstract URLHelper getSuccessURL(FORM form);

    // not usually used but some actions return views that close the current window etc...
    public ModelAndView getSuccessView(FORM form) throws Exception
    {
        return null;
    }
}
