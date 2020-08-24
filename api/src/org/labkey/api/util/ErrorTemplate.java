package org.labkey.api.util;

import org.labkey.api.view.JspView;
import org.labkey.api.view.template.PageConfig;

public class ErrorTemplate extends JspView<PageConfig>
{
    private final ErrorRenderer _renderer;

    public ErrorTemplate(ErrorRenderer renderer, PageConfig pageConfig)
    {
        super("/org/labkey/api/view/template/errorView.jsp", pageConfig);
        _renderer = renderer;
    }

    public ErrorRenderer getErrorRender()
    {
        return _renderer;
    }
}
