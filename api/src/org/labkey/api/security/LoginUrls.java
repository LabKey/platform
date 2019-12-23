/*
 * Copyright (c) 2008-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

/**
 * User: adam
 * Date: May 15, 2008
 * Time: 9:40:56 AM
 */
public interface LoginUrls extends UrlProvider
{
    ActionURL getConfigureURL();
    ActionURL getConfigureDbLoginURL();
    ActionURL getVerificationURL(Container c, ValidEmail email, String verification, @Nullable List<Pair<String, String>> extraParameters);
    ActionURL getChangePasswordURL(Container c, User user, URLHelper returnURL, @Nullable String message);
    ActionURL getInitialUserURL();
    ActionURL getLoginURL();
    ActionURL getLoginURL(URLHelper returnURL);
    ActionURL getLoginURL(Container c, @Nullable URLHelper returnURL);
    ActionURL getLogoutURL(Container c);
    ActionURL getLogoutURL(Container c, URLHelper returnURL);
    ActionURL getStopImpersonatingURL(Container c, @Nullable URLHelper returnURL);
    ActionURL getAgreeToTermsURL(Container c, URLHelper returnURL);
    ActionURL getSSORedirectURL(SSOAuthenticationConfiguration configuration, URLHelper returnURL, boolean skipProfile);

    NavTree appendAuthenticationNavTrail(NavTree root);


    @Deprecated
    ActionURL getEnableConfigParameterURL(String name);
    @Deprecated
    ActionURL getDisableConfigParameterURL(String name);
    @Deprecated
    ActionURL getEnableProviderURL(AuthenticationProvider provider);
    @Deprecated
    ActionURL getDisableProviderURL(AuthenticationProvider provider);
    @Deprecated
    ModelAndView getPickLogosView(@Nullable Integer rowId, boolean reshow, boolean formatInTable, BindException errors);
    @Deprecated
    ActionURL getOldConfigureURL();
    @Deprecated
    NavTree appendOldAuthenticationNavTrail(NavTree root);
}
