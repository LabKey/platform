/*
 * Copyright (c) 2003-2007 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.global;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.SharedFlowController;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.labkey.api.util.ExceptionUtil;


/**
 * The Global page flow is used to define actions which can be invoked by any other
 * page flow in a webapp. The "jpf:catch" annotation provides a global way to catch
 * unhandled exceptions by forwarding to an error page.
 */
@Jpf.Controller(catches = @Jpf.Catch(type = Throwable.class, method = "handleException"))
public class Global extends SharedFlowController
{
    @Jpf.Action
    public Forward error()
    {
        Exception x = (Exception) getRequest().getAttribute("javax.servlet.error.exception");
        if (null == x)
            x = (Exception) getRequest().getAttribute("org.apache.struts.action.EXCEPTION");
        return handleException(x, "error", null, null);
    }

    @Jpf.ExceptionHandler
    protected Forward handleException(Throwable ex, String actionName, String message, Object form)
    {
        return ExceptionUtil.handleException(getRequest(), getResponse(), ex, message, false);
    }
}
