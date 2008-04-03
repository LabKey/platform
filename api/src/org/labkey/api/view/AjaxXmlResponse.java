package org.labkey.api.view;

import org.apache.xmlbeans.XmlObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: kevink
 * Date: Jan 4, 2008 1:19:13 PM
 */
public abstract class AjaxXmlResponse<ModelBean> extends AjaxResponse<ModelBean>
{
    public String getContentType()
    {
        return "text/xml";
    }

    protected void renderAjaxPayload(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception
    {
        if (model instanceof XmlObject)
        {
            XmlObject xobj = (XmlObject)model;
            xobj.save(response.getWriter());
            return;
        }
        renderXmlPayload(model, request, response);
    }

    protected abstract void renderXmlPayload(ModelBean model, HttpServletRequest request, HttpServletResponse response) throws Exception;
}
