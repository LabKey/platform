package org.labkey.api.view;

import org.labkey.api.util.DOM;
import org.springframework.web.servlet.ModelAndView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class AbstractViewBox extends WebPartView<Object>
{
    protected final List<ModelAndView> _views;

    public AbstractViewBox(ModelAndView... views)
    {
        super(WebPartView.FrameType.NONE);
        _views = new ArrayList<>(Arrays.asList(views));
    }

    @Override
    public boolean isVisible()
    {
        return null != _views && !_views.isEmpty();
    }

    public void addView(ModelAndView v)
    {
        if (null == v)
            return;
        _views.add(v);
    }

    public void addView(DOM.Renderable r)
    {
        if (null == r)
            return;
        _views.add(new HtmlView(r));
    }

    public void addView(ModelAndView v, int index)
    {
        if (null == v)
            return;
        _views.add(index, v);
    }

    @Override
    public List<ModelAndView> getViews()
    {
        return new ArrayList<>(_views);
    }
}
