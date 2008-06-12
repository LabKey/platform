/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.beehive.netui.pageflow.FormData;
import org.apache.beehive.netui.pageflow.Forward;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.StrutsAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.common.util.Pair;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import java.util.List;


public class ViewForm extends FormData implements HasViewContext
{
    protected User _user;
    protected Container _c;
    protected ViewContext _context = null;
    protected HttpServletRequest _request;
    protected ActionErrors _errors = null;
    protected static Logger _formLog = Logger.getLogger(ViewForm.class);


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


    public void setViewContext(ViewContext context)
    {
        _context = context;
        _request = context.getRequest();
        _user = context.getUser();
        _c = context.getContainer();
    }


    public ViewContext getViewContext()
    {
        return getContext();
    }


    @Deprecated
    public ViewContext getContext()
    {
        if (null == _context)
            _context = HttpView.currentContext();
        return _context;
    }


    @Deprecated
    public void setContext(ViewContext context)
    {
        _context = context;
    }


    public void setUser(User user)
    {
        _user = user;
    }


    private String _toString(Object o)
    {
        return null == o ? "" : String.valueOf(o);
    }

    public Forward getForward(String action, boolean redirect) throws java.net.URISyntaxException
    {
        return getForward(action, (Pair[])null, redirect);
    }

    public Forward getForward(String action, Pair[] params, boolean redirect) throws java.net.URISyntaxException
    {
        ActionURL urlhelp = getContext().cloneActionURL();
        urlhelp.setAction(action);
        urlhelp.deleteParameters();

        if (null != params)
        {
            for (Pair p : params)
            {
                if (null == p || null == p.getKey())
                    continue;
                urlhelp.replaceParameter(_toString(p.getKey()), _toString(p.getValue()));
            }
        }

        Forward fwd = new ViewForward(urlhelp);
        fwd.setRedirect(redirect);
        return fwd;
    }


    public Forward getForward(String action, Pair param, boolean redirect) throws java.net.URISyntaxException
    {
        return getForward(action, new Pair[]{param}, redirect);
    }


    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        _errors = null;
        _request = request;
        _user = (User) request.getUserPrincipal();
        try
        {
            _c = getViewContext().getContainer(0);
        }
        catch (ServletException x)
        {
        }
        catch (NotFoundException x)
        {
        }
    }


    // validate() helper, see PageFlowUtil.getActionErrors() for use outside form.validate()
    public void addActionError(String error)
    {
        addActionError("main", error);
    }

    public void addActionError(String field, String error)
    {
        if (_errors == null)
            _errors = new ActionErrors();
        _errors.add(field, new ActionMessage("Error", error));
    }

    public ActionErrors getActionErrors()
    {
        return _errors;
    }

    // Unused method-- override it and make it final to prevent derived classes from
    // accidentally overriding it, instead of the other one.
    final public void reset(ActionMapping actionMapping, ServletRequest request)
    {
        super.reset(actionMapping, request);
    }

    public List<AttachmentFile> getAttachmentFileList()
    {
        return StrutsAttachmentFile.createList(getMultipartRequestHandler().getFileElements());
    }
}
