/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * <code>SimpleStreamAction</code> for streaming to the response writer directly,
 * with no need for a nav trail or even HTML.  The render method of this action
 * writes the entire contents of the response stream.  No further rendering will
 * occur.
 */
public abstract class SimpleStreamAction<FORM> extends SimpleViewAction<FORM>
{
    protected SimpleStreamAction()
    {
    }

    protected SimpleStreamAction(Class<? extends FORM> formClass)
    {
        super(formClass);
    }

    /**
     * Make sure input form type is connected.
     */
    protected String getCommandClassMethodName()
    {
        return "render";
    }

    /**
     * Returns the mime type of the output.  The default is "text/plain".
     * Override to use a different mime type.
     *
     * @return mime type of the streamed response
     */
    public String getMimeType()
    {
        return "text/plain";
    }

    /**
     * Override to set extra properties on the response object.
     *
     * @param response response for the HTTP request
     */
    public void setResponseProperties(HttpServletResponse response)
    {
        // Default does nothing
    }

    /**
     * Responsible for writing the streamed output to the output writer
     * object.
     *
     * @param form request parameters, if any
     * @param errors place to put errors
     * @param out the output writer
     * @throws Exception allow override to throw exceptions
     */
    abstract public void render(FORM form, BindException errors, PrintWriter out) throws Exception;

    public ModelAndView getView(FORM form, BindException errors) throws Exception
    {
        // Set up the response
        HttpServletResponse response = getViewContext().getResponse();
        PrintWriter out = response.getWriter();
        response.setContentType(getMimeType());
        setResponseProperties(response);

        // Let the implementation write to the output stream
        render(form, errors, out);

        response.flushBuffer();
        return null;
    }

    /**
     * Override base class to do nothing for nave trail, since this action
     * type takes care of all of its own rendering.
     */
    public NavTree appendNavTrail(NavTree root)
    {
        return null;  // No nav trail.
    }
}
