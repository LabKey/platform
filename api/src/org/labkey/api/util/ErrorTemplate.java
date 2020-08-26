package org.labkey.api.util;

import org.labkey.api.view.JspView;

// TODO: ErrorPage - rename this to ErrorView when that object is deleted
public class ErrorTemplate extends JspView<ErrorRenderer>
{
    public ErrorTemplate(ErrorRenderer renderer)
    {
        super("/org/labkey/api/view/template/errorView.jsp", renderer);
    }
}
