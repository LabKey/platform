/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;
import org.labkey.api.view.NavTree;

import javax.servlet.http.HttpServletResponse;

/**
* User: adam
* Date: Jan 10, 2008
* Time: 1:32:35 PM
*/
public abstract class ExportAction<FORM> extends SimpleViewAction<FORM>
{
    public ExportAction()
    {
        super();
    }

    protected ExportAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    protected String getCommandClassMethodName()
    {
        return "export";
    }

    public final ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        try
        {
            export(form, getViewContext().getResponse(), errors);
        }
        catch (ExportException e)
        {
            return e.getErrorView();
        }
        return null;
    }

    public final NavTree appendNavTrail(NavTree root)
    {
        return null;
    }

    public abstract void export(FORM form, HttpServletResponse response, BindException errors) throws Exception;
}
