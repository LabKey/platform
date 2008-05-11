package org.labkey.api.jsp;

import org.labkey.api.view.*;
import org.labkey.api.util.PageFlowUtil;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

abstract public class FormPage<FROM extends ViewForm> extends ContextPage
{
    public FROM __form;
    
    static public <F extends ViewForm> FormPage<F> get(Class clazzPackage, F form, String name)
    {
        FormPage<F> ret = (FormPage<F>) JspLoader.createPage(form.getRequest(), clazzPackage, name);
        ret.setForm(form);
        return ret;
    }

    static public <F extends ViewForm> JspView<F> getView(Class clazzPackage, F form, BindException errors, String name)
    {
        return get(clazzPackage, form, name).createView(errors);
    }

    static public <F extends ViewForm> JspView<F> getView(Class clazzPackage, F form, String name)
    {
        return get(clazzPackage, form, name).createView();
    }

    public JspView<FROM> createView(BindException errors)
    {
        return new JspView<FROM>(this, null, errors);
    }

    public JspView<FROM> createView()
    {
        return new JspView<FROM>(this);
    }

    public void setForm(FROM form)
    {
        setViewContext(form.getViewContext());
        __form = form;
    }

    protected FROM getForm()
    {
        return __form;
    }

    /**
     * Use &lt;labkey:errors/> instead.
     * @return formatted error string
     */
    @Deprecated
    public String errors()
    {
        return PageFlowUtil.getStrutsError(__form.getRequest(), null);
    }
}
