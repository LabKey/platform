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
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.AjaxResponse;

/**
 * User: kevink
 * Date: Jan 4, 2008 12:18:02 PM
 */
public abstract class AjaxAction<FORM> extends SimpleViewAction<FORM>
{
    public AjaxAction()
    {
        super();
    }

    public AjaxAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    public ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        getPageConfig().setTemplate(PageConfig.Template.None);
        return getResponse(form, errors);
    }

    public abstract AjaxResponse getResponse(FORM form, BindException errors) throws Exception;

    public NavTree appendNavTrail(NavTree root)
    {
        return null;
    }
}
