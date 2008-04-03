package org.labkey.api.reports.chart;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 6, 2008
 */
public class RenderInfo implements ChartRenderInfo
{
    private String _imageMapCallback;
    private String[] _imageMapCallbackColumns;

    public RenderInfo(String imageMapCallback)
    {
        this(imageMapCallback, new String[0]);
    }

    public RenderInfo(String imageMapCallback, String[] imageMapCallbackColumns)
    {
        _imageMapCallback = imageMapCallback;
        _imageMapCallbackColumns = imageMapCallbackColumns;
    }

    public String getImageMapCallback()
    {
        return _imageMapCallback;
    }

    public String[] getImageMapCallbackColumns()
    {
        return _imageMapCallbackColumns != null ? _imageMapCallbackColumns : new String[0];
    }
}
