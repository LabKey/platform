package org.labkey.api.exp;

/**
 * User: jeckels
* Date: Mar 10, 2008
*/
public class DomainNotFoundException extends Exception
{
    public DomainNotFoundException(String uri)
    {
        super("Domain Not Found: " + uri);
    }
}
