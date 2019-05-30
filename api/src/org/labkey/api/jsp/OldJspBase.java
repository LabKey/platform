package org.labkey.api.jsp;

import org.labkey.api.annotations.RemoveIn19_3;
import org.labkey.api.util.PageFlowUtil;

/**
 * Restores the previous JspBase behavior where {@code h(String)} returned a String. Use this on a <b>temporary</b> basis to keep JSPs
 * compiling, without requiring immediate updates due to this change. Switch JSPs to extend this class as follows:
 * <p>
 * {@code     <%@ page extends="org.labkey.api.jsp.OldJspBase" %>}
 * </p>
 * This class will be removed early in the 19.3 development cycle, so JSPs will need to be updated before compiling against 19.3.
 */
@Deprecated
@RemoveIn19_3
public abstract class OldJspBase extends AbstractJspBase
{
    /**
     * Html escape a string.
     * The name comes from Embedded Ruby.
     */
    public String h(String str)
    {
        return PageFlowUtil.filter(str);
    }
}
