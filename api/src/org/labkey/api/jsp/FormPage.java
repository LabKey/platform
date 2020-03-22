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

package org.labkey.api.jsp;

import org.labkey.api.action.HasViewContext;
import org.labkey.api.view.JspView;
import org.springframework.validation.BindException;

abstract public class FormPage<FORM extends HasViewContext> extends JspBase
{
    public FORM __form;
    
    public static <F extends HasViewContext> JspView<F> getView(String jspPath, F form, BindException errors)
    {
        JspView<F> view = new JspView<>(jspPath, form, errors);
        ((FormPage<F>)view.getPage()).setForm(form);
        return view;
    }

    public static <F extends HasViewContext> JspView<F> getView(String jspPath, F form)
    {
        return getView(jspPath, form, null);
    }

    public void setForm(FORM form)
    {
        setViewContext(form.getViewContext());
        __form = form;
    }

    protected FORM getForm()
    {
        return __form;
    }
}
