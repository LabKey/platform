/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Base class for API Actions that will received data posted from an Ext form.
 * This class ensures that the validation errors are reported back to the form
 * in the way that Ext forms require.
 * User: Dave
 * Date: Sep 3, 2008
 */
public abstract class ExtFormAction<FORM> extends BaseApiAction<FORM>
{
    // ExtFormAction previously extended MutatingApiAction... this override mimics the previous behavior while making for a saner hierarchy.
    // TODO: Migrate the three concrete children of this class to MutatingApiAction (??), and fold this class into FormApiAction
    @Override
    protected ModelAndView handleGet() throws Exception
    {
        int status = HttpServletResponse.SC_METHOD_NOT_ALLOWED;
        String message = "You must use the POST method when calling this action.";
        createResponseWriter().writeAndCloseError(status, message);
        return null;
    }

    @Override
    protected ApiResponseWriter createResponseWriter() throws IOException
    {
        return new ExtFormResponseWriter(getViewContext().getRequest(), getViewContext().getResponse(), getContentTypeOverride());
    }
}