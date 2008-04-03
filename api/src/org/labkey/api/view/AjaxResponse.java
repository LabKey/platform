package org.labkey.api.view;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: kevink
 * Date: Jan 4, 2008 12:22:29 PM
 */
public abstract class AjaxResponse<ModelBean> extends HttpView<ModelBean>
{
    protected void setHeaders(HttpServletResponse response)
    {
        response.setContentType(getContentType());
        response.setHeader("Cache-Control", "no-cache");
    }

    protected void renderInternal(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        setHeaders(response);
        renderAjaxPayload(model, request, response);
    }

    protected void renderAjaxPayload(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
    }

    public NavTree appendNavTrail(NavTree root)
     {
         return null;
     }
}
