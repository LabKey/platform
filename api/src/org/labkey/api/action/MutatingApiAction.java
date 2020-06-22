/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.labkey.api.security.MethodsAllowed;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;

import static org.labkey.api.util.HttpUtil.Method.POST;

/**
 * Base class for all API actions that change the server's state in some way. This class enforces
 * that clients use the HTTP POST method.
 * @param <FORM> The form class
 * User: Dave
 * Date: May 25, 2009
 */
@MethodsAllowed(POST)
public abstract class MutatingApiAction<FORM> extends BaseApiAction<FORM>
{
    @Override
    protected final ModelAndView handleGet() throws Exception
    {
        // leave this assert because subclasses may change the MethodsAllowed annotation
        assert false : "Should not get here";
        int status = HttpServletResponse.SC_METHOD_NOT_ALLOWED;
        String message = "You must use the POST method when calling this action.";
        createResponseWriter().writeAndCloseError(status, message);
        return null;
    }
}
