/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.labkey.api.util.URLHelper;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Base class for actions that don't want to render their own page,
 * but instead just bounce the user to another URL via a HTTP response code that redirects the browser
 */
public abstract class SimpleRedirectAction<FORM> extends SimpleViewAction<FORM>
{
    public final ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        URLHelper url;

        try
        {
            url = getRedirectURL(form);
        }
        catch (Exception e)
        {
            return getErrorView(e, errors);
        }

        if (null != getViewContext().getRequest().getHeader("template"))
            url.addParameter("_template", getViewContext().getRequest().getHeader("template"));
        return HttpView.redirect(url);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

    public abstract URLHelper getRedirectURL(FORM form) throws Exception;

    // Called whenever getRedirectURL() throws an exception.  Standard code rethrows the exception.  Override this
    // method to customize the handling of certain exceptions (e.g., display an error view). 
    protected ModelAndView getErrorView(Exception e, BindException errors) throws Exception
    {
        throw e;
    }

    protected String getCommandClassMethodName()
    {
        return "getRedirectURL";
    }
}
