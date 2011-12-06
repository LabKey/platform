package org.labkey.api.security;

/**
 * User: adam
 * Date: 12/5/11
 * Time: 10:35 PM
 */
public class InvalidGroupMembershipException extends Exception
{
    public InvalidGroupMembershipException(String errorMessage)
    {
        super(errorMessage);
    }
}
