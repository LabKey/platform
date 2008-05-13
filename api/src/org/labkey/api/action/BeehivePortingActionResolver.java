/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 3, 2007
 * Time: 9:52:45 AM
 *
 * For use ONLY when porting old style beehive based controllers
 * @Deprecated
 */
public class BeehivePortingActionResolver extends SpringActionController.DefaultActionResolver
{
    Class _beehiveController;
    BeehiveForwardingAction _beehiveAction = new BeehiveForwardingAction();
    
    public BeehivePortingActionResolver(Class oldDecrepitController, Class outerClass, Class<? extends Controller>... otherClasses)
    {
        super(outerClass, otherClasses);
        _beehiveController = oldDecrepitController;
    }

    public Controller resolveActionName(Controller actionController, String name)
    {
        Controller c = super.resolveActionName(actionController, name);
        if (null != c)
            return c;
        return _beehiveAction;
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class BeehiveForwardingAction implements Controller
    {
        public BeehiveForwardingAction()
        {

        }

        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            ViewContext rootContext = HttpView.currentContext();
            ActionURL url = rootContext.cloneActionURL();
            String pageFlow = _beehiveController.getPackage().getName().replace('.', '/');
            String dispatchUrl = "/" + pageFlow + "/" + url.getAction() + ".do";
            RequestDispatcher r = request.getRequestDispatcher(dispatchUrl);
            r.forward(request, response);
            return null;
        }
    }
}

