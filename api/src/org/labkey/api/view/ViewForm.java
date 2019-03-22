/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import javax.servlet.http.HttpServletRequest;

/**
 * Utility base class for the form bean that actions use to bind their HTTP parameter values.
 */
public class ViewForm extends ReturnUrlForm implements HasViewContext
{
    protected User _user;
    protected Container _c;
    protected ViewContext _context = null;
    protected HttpServletRequest _request;


    @NotNull
    public Container getContainer()
    {
        return _c;
    }


    public void setContainer(Container c)
    {
        _c = c;
    }


    public User getUser()
    {
        return _user;
    }


    public HttpServletRequest getRequest()
    {
        return _request;
    }


    public void setViewContext(@NotNull ViewContext context)
    {
        _context = context;
        _request = context.getRequest();
        _user = context.getUser();
        _c = context.getContainer();
    }


    public ViewContext getViewContext()
    {
        if (null == _context)
            _context = HttpView.currentContext();
        return _context;
    }

    
    public void setUser(User user)
    {
        _user = user;
    }
}
