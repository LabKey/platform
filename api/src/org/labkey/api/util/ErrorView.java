package org.labkey.api.util;

import org.labkey.api.view.JspView;

public class ErrorView extends JspView<ErrorRenderer>
{
    public ErrorView(ErrorRenderer renderer)
    {
        super("/org/labkey/api/view/template/errorView.jsp", renderer);
    }
}
