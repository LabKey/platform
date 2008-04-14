package org.labkey.api.action;

import org.labkey.api.view.TermsOfUseException;
import org.labkey.api.view.UnauthorizedException;

import javax.servlet.ServletException;

/**
 * User: jeckels
 * Date: Apr 9, 2008
 */
public interface PermissionCheckable
{
    public void checkPermissions() throws TermsOfUseException, UnauthorizedException;
}
