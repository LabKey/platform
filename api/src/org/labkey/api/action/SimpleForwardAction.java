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

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewServlet;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Similar to {@link org.labkey.api.action.SimpleRedirectAction}, but forwards to the URL instead of redirecting.
 * This preserves post data, etc because it's handled internally within the web server instead of asking the browser
 * to send the request to a different URL
 */
public abstract class SimpleForwardAction<FORM> extends SimpleViewAction<FORM>
{
    public ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        ViewServlet.forwardActionURL(getViewContext().getRequest(), getViewContext().getResponse(), getForwardURL(form));
        return null;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

    public abstract ActionURL getForwardURL(FORM form) throws Exception;

    protected String getCommandClassMethodName()
    {
        return "getForwardURL";
    }
}