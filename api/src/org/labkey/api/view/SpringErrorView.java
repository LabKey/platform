package org.labkey.api.view;

import org.springframework.validation.BindException;

/**
 * Simple view that just shows all of the Spring errors
 * User: jeckels
 * Date: Mar 5, 2012
 */
public class SpringErrorView extends JspView<Object>
{
    public SpringErrorView(BindException errors)
    {
        super("/org/labkey/api/view/springError.jsp", null, errors);
    }
}
