package org.labkey.api.util;

import org.labkey.api.view.JspView;

public class ErrorView extends JspView<ErrorRenderer>
{
    public static String ERROR_PAGE_TITLE = "Error Page";

    public ErrorView(ErrorRenderer renderer)
    {
        super("/org/labkey/api/view/template/errorView.jsp", renderer);
    }
}
