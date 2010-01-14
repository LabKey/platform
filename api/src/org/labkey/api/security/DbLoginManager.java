package org.labkey.api.security;

/**
 * User: adam
 * Date: Jan 13, 2010
 * Time: 5:04:48 PM
 */
public class DbLoginManager
{
    // TODO: Move Logins table operations here
    // TODO: Store and retrieve password rule as a property
    public static PasswordRule getPasswordRule()
    {
        return PasswordRule.weak;
    }
}
