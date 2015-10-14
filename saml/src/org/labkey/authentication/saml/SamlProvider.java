/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.authentication.saml;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.AuthenticationManager.LinkFactory;
import org.labkey.api.security.AuthenticationProvider.SSOAuthenticationProvider;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;

/**
 * User: tgaluhn
 * Date: 1/19/2015
 */
public class SamlProvider implements SSOAuthenticationProvider
{
    public static final String NAME = "SAML";
    private final LinkFactory _linkFactory = new LinkFactory(this);
    
    @Nullable
    @Override
    public ActionURL getConfigurationLink()
    {
        return SamlController.getConfigureURL();
    }

    @Override
    public URLHelper getURL(String secret)
    {
        return SamlManager.getLoginURL();
    }

    @Override
    public LinkFactory getLinkFactory()
    {
        return _linkFactory;
    }
    
    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public String getDescription()
    {
        return "Acts as a service provider (SP) to authenticate against a SAML 2.0 Identity Provider (IdP)";
    }

    @Override
    public void logout(HttpServletRequest request)
    {
    }

    @Override
    public void activate()
    {
    }

    @Override
    public void deactivate()
    {
    }

    @Override
    public boolean isPermanent()
    {
        return false;
    }
}
