package org.labkey.api.action;

import org.labkey.api.view.JspView;
import org.springframework.validation.BindException;

/**
 * User: adam
 * Date: Sep 26, 2007
 * Time: 9:24:09 AM
 */
public class SimpleErrorView extends JspView
{
    public SimpleErrorView(BindException errors)
    {
        super("/org/labkey/api/action/simpleErrorView.jsp", null, errors);
    }
}
