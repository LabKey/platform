/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.authentication.saml;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.view.ActionURL;
import org.labkey.authentication.AuthenticationModule;
import org.springframework.validation.BindException;

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

    public static ActionURL getValidateURL()
    {
        return new ActionURL(ValidateAction.class, ContainerManager.getRoot());
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class ValidateAction extends AuthenticationManager.BaseSsoValidateAction<ReturnUrlForm>
    {

        @NotNull
        @Override
        public String getProviderName()
        {
            return SamlProvider.NAME;
        }

        @Nullable
        @Override
        public ValidEmail validateAuthentication(ReturnUrlForm form, BindException errors) throws Exception
        {
            if (!AppProps.getInstance().isExperimentalFeatureEnabled(AuthenticationModule.EXPERIMENTAL_SAML_SERVICE_PROVIDER))
                throw new IllegalStateException();

            String email = SamlManager.getUserFromSamlResponse(getViewContext().getRequest());
            if (StringUtils.isNotBlank(email))
                return new ValidEmail(email);
            else
                return null;

        }
    }

    // TODO: Defering configuration for prototype
}
