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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.template.PageConfig;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

/**
 * Base class for actions that want to show a full-page confirmation step prior to performing the actual operation.
 * User: matthewb
 * Date: May 17, 2007
 */
public abstract class ConfirmAction<FORM> extends BaseViewAction<FORM>
{
    public String getConfirmText()
    {
        return "OK";
    }

    public String getCancelText()
    {
        return "Cancel";
    }

    public URLHelper getCancelUrl()
    {
        return null;
    }

    public boolean isPopupConfirmation()
    {
        return false;
    }

    @Override
    public final ModelAndView handleRequest() throws Exception
    {
        BindException errors = bindParameters(getPropertyValues());
        FORM form = (FORM)errors.getTarget();

        boolean success = !errors.hasErrors();

        if (success && null != form)
            validate(form, errors);
        success = !errors.hasErrors();

        if (success)
        {
            if (isPost())
            {
                success = handlePost(form, errors);

                if (success)
                {
                    ModelAndView mv = getSuccessView(form);
                    if (null != mv)
                        return mv;
                    throw new RedirectException(getSuccessURL(form));
                }
            }
            else
            {
                ModelAndView confirmView = getConfirmView(form, errors);
                JspView<ConfirmAction<FORM>> confirmWrapper = new JspView<>("/org/labkey/api/action/confirmWrapper.jsp", this, errors);
                confirmWrapper.setBody(confirmView);
                getPageConfig().setTemplate(PageConfig.Template.Dialog);

                // catch all for actions that don't set the page title
                if (getPageConfig().getTitle() == null)
                    setTitle("Confirmation");
                return confirmWrapper;
            }
        }

        // We failed... redirect if fail URL is specified, otherwise return the error view
        ActionURL urlFail = getFailURL(form, errors);
        if (null != urlFail)
        {
            throw new RedirectException(urlFail);
        }

        return getFailView(form, errors);
    }

    @Override
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

    @Override
    public void validate(Object form, Errors errors)
    {
        validateCommand((FORM)form, errors);
    }

    /* Generic version of validate */
    public abstract void validateCommand(FORM form, Errors errors);

    @NotNull
    public abstract URLHelper getSuccessURL(FORM form);

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
}
