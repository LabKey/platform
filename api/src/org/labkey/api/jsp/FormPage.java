/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.view.JspView;
import org.labkey.api.action.HasViewContext;
import org.springframework.validation.BindException;

abstract public class FormPage<FORM extends HasViewContext> extends JspBase
{
    public FORM __form;
    
    static public <F extends HasViewContext> FormPage<F> get(Class clazzPackage, F form, String name)
    {
        FormPage<F> ret = (FormPage<F>) JspLoader.createPage(clazzPackage, name);
        ret.setForm(form);
        return ret;
    }

    static public <F extends HasViewContext> JspView<F> getView(Class clazzPackage, F form, BindException errors, String name)
    {
        return get(clazzPackage, form, name).createView(errors);
    }

    static public <F extends HasViewContext> JspView<F> getView(Class clazzPackage, F form, String name)
    {
        return get(clazzPackage, form, name).createView();
    }

    public JspView<FORM> createView(BindException errors)
    {
        return new JspView<>(this, null, errors);
    }

    public JspView<FORM> createView()
    {
        return new JspView<>(this);
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
