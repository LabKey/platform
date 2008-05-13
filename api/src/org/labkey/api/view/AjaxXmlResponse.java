/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
