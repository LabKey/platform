package org.labkey.api.study.assay;

import org.labkey.api.data.RenderContext;
import org.labkey.api.view.GWTView;

/**
 * User: jeckels
 * Date: Oct 30, 2007
 */
public class ThawListBean
{
    private RenderContext _renderContext;
    private GWTView _listChooser;

    public ThawListBean(RenderContext renderContext, GWTView listChooser)
    {
        _renderContext = renderContext;
        _listChooser = listChooser;
    }

    public RenderContext getRenderContext()
    {
        return _renderContext;
    }

    public GWTView getListChooser()
    {
        return _listChooser;
    }
}
