package org.labkey.api.security.permissions;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-09-14
 * Time: 1:35 PM
 */
public class SeeUserEmailAddressesPermission extends AbstractPermission
{
    public SeeUserEmailAddressesPermission()
    {
        super("See E-Mail Addresses", "Allows non-administrators to see e-mail addresses of other users.");
    }
}
