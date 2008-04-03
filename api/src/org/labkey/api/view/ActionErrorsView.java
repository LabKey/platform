package org.labkey.api.view;

import org.labkey.api.util.PageFlowUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: jeckels
 * Date: Aug 1, 2007
 */
public class ActionErrorsView<ModelBean> extends HttpView<ModelBean>
{
    public void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        response.getWriter().print(PageFlowUtil.getStrutsError(request, null));
    }
}
