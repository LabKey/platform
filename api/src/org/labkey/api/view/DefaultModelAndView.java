package org.labkey.api.view;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Dec 7, 2006
 * Time: 4:14:27 PM
 */
public class DefaultModelAndView<ModelBean> extends ModelAndView
{
    View _view;
    ModelBean _model;

    public DefaultModelAndView()
    {
    }

    public DefaultModelAndView(View v, ModelBean m)
    {
        super(v);
        _view = v;
        _model = m;
    }

    public void setView(View view)
    {
        _view = view;
    }

    protected void setModelBean(ModelBean model)
    {
        _model = model;
    }

    public View getView()
    {
        return _view;
    }

    public ModelBean getModelBean()
    {
        return _model;
    }

    public void render(HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        _view.render(getModel(), request, response);
    }
}
