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

import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.util.List;

public interface LoginUrls extends UrlProvider
{
    ActionURL getConfigureURL();
    ActionURL getVerificationURL(Container c, User user, String verification, @Nullable List<Pair<String, String>> extraParameters);
    ActionURL getChangePasswordURL(Container c, User user, URLHelper returnUrl, @Nullable String message);
    ActionURL getInitialUserURL();
    ActionURL getLoginURL();
    ActionURL getLoginURL(URLHelper returnUrl);
    ActionURL getRegisterURL(Container c, @Nullable URLHelper returnUrl);
    ActionURL getLoginURL(Container c, @Nullable URLHelper returnUrl);
    ActionURL getLogoutURL(Container c);
    ActionURL getLogoutURL(Container c, URLHelper returnUrl);
    ActionURL getStopImpersonatingURL(Container c, @Nullable URLHelper returnUrl);
    ActionURL getAgreeToTermsURL(Container c, URLHelper returnUrl);
    ActionURL getSSORedirectURL(SSOAuthenticationConfiguration<?> configuration, URLHelper returnUrl, boolean skipProfile);

    void forceReauth(HttpServletResponse response, Container c, @Nullable URLHelper returnUrl);
}
