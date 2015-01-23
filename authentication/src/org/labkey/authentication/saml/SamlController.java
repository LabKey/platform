package org.labkey.authentication.saml;

import org.labkey.api.action.SpringActionController;

/**
 * User: tgaluhn
 * Date: 1/19/2015
 */
public class SamlController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SamlController.class);

    public SamlController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    // TODO: Defering configuration for prototype

    // This controller isn't being registered yet, as it doesn't do anything.

}
