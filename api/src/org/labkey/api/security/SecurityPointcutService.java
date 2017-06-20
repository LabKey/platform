package org.labkey.api.security;

import org.labkey.api.module.Module;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by matthew on 6/20/17.
 */
public interface SecurityPointcutService
{

    /**
     * This handler is called before a action request (.api,.view,.post) is dispatched to an module for resolving to an action.
     * Typically called by ViewServlet, but if anyone calls an action w/o dispatching through ViewServlet (e.g WebdavService)
     * it can call this as well.
     *
     * NOTE: this does not replace security checking in the sense that the server is not "aware" of these filters.  It can
     * not update its own UI to reflect that a given action might be blocked by this means.
     *
     * return true means accept action
     * return false means reject action, handler should set response status
     */
    boolean beforeResolveAction(HttpServletRequest req, HttpServletResponse res, Module m, String controller, String action);

    /**
     * This handler is called _very_ early, to validate that the request is well-formed
     */
    boolean beforeProcessRequest(HttpServletRequest req, HttpServletResponse res);
}
