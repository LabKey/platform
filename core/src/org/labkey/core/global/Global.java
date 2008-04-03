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
