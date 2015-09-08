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

import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: jeckels
 * Date: Feb 8, 2008
 */
public abstract class GWTServiceAction extends BaseViewAction<Object>
{
    protected GWTServiceAction()
    {
        super(Object.class);
        setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
    }

    @Override
    public ModelAndView handleRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws Exception
    {
        BaseRemoteService service = createService();
        if (!"POST".equalsIgnoreCase(httpServletRequest.getMethod()))
        {
            // GWT service requests must be POSTs
            httpServletResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            httpServletResponse.setHeader("Allow", "POST");
        }
        else
        {
            service.doPost(httpServletRequest, httpServletResponse);
        }
        return null;
    }

    protected abstract BaseRemoteService createService();


    // methods we ignore, but have to implement since we extends BaseViewAction
    @Override
    protected String getCommandClassMethodName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void validate(Object o, Errors errors)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public ModelAndView handleRequest() throws Exception
    {
        throw new UnsupportedOperationException();
    }
}
