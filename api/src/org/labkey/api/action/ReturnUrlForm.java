package org.labkey.api.action;

import org.labkey.api.view.ActionURL;

/**
* User: adam
* Date: Nov 22, 2007
* Time: 1:27:34 PM
*/
public class ReturnUrlForm
{
    public enum Params
    {
        returnUrl
    }

    private String _returnUrl;

    public String getReturnUrl()
    {
        return _returnUrl;
    }

    public void setReturnUrl(String returnUrl)
    {
        _returnUrl = returnUrl;
    }

    public ActionURL getReturnActionURL()
    {
        return new ActionURL(_returnUrl);
    }
}
