package org.labkey.api.view;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Jun 30, 2006
 * Time: 5:41:25 PM
 */
public class TermsOfUseException extends UnauthorizedException
{
    public TermsOfUseException(ActionURL url)
    {
        super(null, url.getLocalURIString());
    }
}
