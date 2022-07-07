/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationConfiguration.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public interface AuthenticationProvider
{
    // All the AuthenticationProvider interfaces. This list is used by AuthenticationProviderCache to filter collections of providers.
    List<Class<? extends AuthenticationProvider>> ALL_PROVIDER_INTERFACES = Arrays.asList(
        AuthenticationProvider.class,
            PrimaryAuthenticationProvider.class,
                LoginFormAuthenticationProvider.class,
                SSOAuthenticationProvider.class,
                RequestAuthenticationProvider.class,
            SecondaryAuthenticationProvider.class,
            ResetPasswordProvider.class,
            DisableLoginProvider.class,
            ExpireAccountProvider.class
    );

    default @Nullable ActionURL getSaveLink()
    {
        return null;
    }

    // Generic authentication topic -- implementers should provide the wiki name for their configuration doc page
    default @NotNull String getHelpTopic()
    {
        return "authenticationModule";
    }

    // Most providers don't have a test action
    default @Nullable ActionURL getTestLink()
    {
        return null;
    }

    /**
     * Returns a JSONArray of the field descriptors for the required provider-specific settings. JSON metadata is a small
     * subset of our standard column metadata (e.g., what getQueryDetails.api returns).
     *
     * @return A JSONArray of field descriptors or null if this provider doesn't have any custom fields
     */
    default @NotNull JSONArray getSettingsFields()
    {
        return new JSONArray();
    }

    @NotNull String getName();
    @NotNull String getDescription();

    default boolean isPermanent()
    {
        return false;
    }

    default boolean isFicamApproved()
    {
        return false;
    }

    /**
     * Override to retrieve and save startup properties intended for this provider. Invoked after every server startup.
     */
    default void handleStartupProperties()
    {
    }

    // Retrieves all the startup properties in the specified categories, populates them into a form, and saves the form
    default <FORM extends SaveConfigurationForm, AC extends AuthenticationConfiguration, T extends Enum<T> & StartupProperty> void saveStartupProperties(String category, Class<FORM> formClass, Class<AC> configurationClass, Class<T> type)
    {
        assert Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals("Description") || c.name().equals("Enabled")).count() == 2 :
            type.getName() + " does not define required Description and Enabled constants!";

        ModuleLoader.getInstance().handleStartupProperties(new StandardStartupPropertyHandler<>(category, type)
        {
            @Override
            public void handle(Map<T, StartupPropertyEntry> properties)
            {
                if (!properties.isEmpty())
                {
                    Map<String, String> map = properties.entrySet().stream()
                        .collect(Collectors.toMap(e-> e.getKey().getPropertyName(), e->e.getValue().getValue()));

                    ObjectFactory<FORM> factory = ObjectFactory.Registry.getFactory(formClass);
                    FORM form = factory.fromMap(map);

                    // If description is provided in the startup properties file and an existing configuration for this provider
                    // matches that description then update the existing configuration. If not, create a new configuration. #39474
                    if (form.getDescription() != null)
                    {
                        AuthenticationConfigurationCache.getConfigurations(configurationClass).stream()
                            .filter(ac -> ac.getDescription().equals(form.getDescription()))
                            .map(AuthenticationConfiguration::getRowId)
                            .findFirst()
                            .ifPresent(form::setRowId);
                    }
                    else
                    {
                        form.setDescription(form.getProvider() + " Configuration");
                    }

                    SaveConfigurationAction.saveForm(form, null);
                }
            }
        });
    }

    interface PrimaryAuthenticationProvider<AC extends PrimaryAuthenticationConfiguration<?>> extends AuthenticationProvider, AuthenticationConfigurationFactory<AC>
    {
    }

    interface LoginFormAuthenticationProvider<AC extends LoginFormAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<AC>, AuthenticationConfigurationFactory<AC>
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        @NotNull AuthenticationResponse authenticate(AC configuration, @NotNull String id, @NotNull String password, URLHelper returnURL) throws InvalidEmailException;
    }

    interface SSOAuthenticationProvider<SSO extends SSOAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<SSO>, AuthenticationConfigurationFactory<SSO>
    {
        @Override
        default <FORM extends SaveConfigurationForm, AC extends AuthenticationConfiguration, T extends Enum<T> & StartupProperty> void saveStartupProperties(String category, Class<FORM> formClass, Class<AC> configurationClass, Class<T> type)
        {
            // SSO authentication provider StartupProperty enums must define AutoRedirect constant
            assert Arrays.stream(type.getEnumConstants()).anyMatch(c -> c.name().equals("AutoRedirect")) :
                type.getName() + " does not define required AutoRedirect constant!";

            PrimaryAuthenticationProvider.super.saveStartupProperties(category, formClass, configurationClass, type);
        }
    }

    interface RequestAuthenticationProvider extends PrimaryAuthenticationProvider
    {
        @NotNull AuthenticationResponse authenticate(@NotNull HttpServletRequest request);
    }

    interface ResetPasswordProvider extends AuthenticationProvider
    {
        /**
         * Returns the base url (excluding verification, email and extra params) for AddUsersAction or ResetPasswordApiAction
         * @param isAddUser true for adding user, otherwise for resetting pw for existing user
         */
        ActionURL getAPIVerificationURL(Container c, boolean isAddUser);

        /**
         * Allow module to send custom email for ResetPasswordApiAction.
         * @param user the user requesting password reset
         * @param isAdminCopy true for sending admin a copy of reset password email
         */
        @Nullable SecurityMessage getAPIResetPasswordMessage(User user, boolean isAdminCopy);
    }

    interface SecondaryAuthenticationProvider<AC extends SecondaryAuthenticationConfiguration<?>> extends AuthenticationProvider, AuthenticationConfigurationFactory<AC>
    {
        /**
         * Bypass authentication from this provider. Might be configured via labkey.xml parameter to
         * temporarily not require secondary authentication if this has been misconfigured or a 3rd
         * party service provider is unavailable.
         */
        boolean bypass();
    }

    interface DisableLoginProvider extends AuthenticationProvider
    {
        boolean isEnabledForUser(String id);

        /**
         * @param id user email string
         * @return Login delay in milliseconds
         * @throws LoginDisabledException
         */
        long getUserDelay(String id) throws LoginDisabledException;

        /**
         *
         * @param request
         * @param id
         * @param addCount
         */
        void addUserDelay(HttpServletRequest request, String id, int addCount);

        void resetUserDelay(String id);
    }

    interface ExpireAccountProvider extends AuthenticationProvider
    {
        boolean isEnabled();
    }

    class AuthenticationResponse
    {
        private final PrimaryAuthenticationConfiguration<?> _configuration;
        private final @Nullable ValidEmail _email;
        private final @Nullable AuthenticationValidator _validator;
        private final @Nullable FailureReason _failureReason;
        private final @Nullable ActionURL _redirectURL;
        private final @NotNull Map<String, String> _attributeMap;

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull ValidEmail email, @Nullable AuthenticationValidator validator, @NotNull Map<String, String> attributeMap)
        {
            _configuration = configuration;
            _email = email;
            _validator = validator;
            _attributeMap = attributeMap;
            _failureReason = null;
            _redirectURL = null;
        }

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            _configuration = configuration;
            _email = null;
            _validator = null;
            _failureReason = failureReason;
            _redirectURL = redirectURL;
            _attributeMap = Collections.emptyMap();
        }

        /**
         * Creates a standard authentication provider response
         * @param email Valid email address of the authenticated user
         * @return A new successful authentication response containing the email address of the authenticated user
         */
        public static AuthenticationResponse createSuccessResponse(PrimaryAuthenticationConfiguration<?> configuration, ValidEmail email)
        {
            return createSuccessResponse(configuration, email, null, Collections.emptyMap());
        }

        /**
         * Creates an authentication provider response that can include a validator to be called on every request and a
         * map of user attributes
         * @param email Valid email address of the authenticated user
         * @param validator An authentication validator
         * @param attributeMap A <b>case-insensitive</b> map of attribute names and values associated with this authentication
         * @return A new successful authentication response containing the email address of the authenticated user and a validator
         */
        public static AuthenticationResponse createSuccessResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, ValidEmail email, @Nullable AuthenticationValidator validator, @NotNull Map<String, String> attributeMap)
        {
            return new AuthenticationResponse(configuration, email, validator, attributeMap);
        }

        public static AuthenticationResponse createFailureResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, FailureReason failureReason)
        {
            return new AuthenticationResponse(configuration, failureReason, null);
        }

        public static AuthenticationResponse createFailureResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            return new AuthenticationResponse(configuration, failureReason, redirectURL);
        }

        public boolean isAuthenticated()
        {
            return null != _email;
        }

        public @NotNull FailureReason getFailureReason()
        {
            assert null != _failureReason && null == _email;

            return _failureReason;
        }

        public @NotNull ValidEmail getValidEmail()
        {
            assert null != _email;

            return _email;
        }

        public @Nullable AuthenticationValidator getValidator()
        {
            return _validator;
        }

        public PrimaryAuthenticationConfiguration<?> getConfiguration()
        {
            return _configuration;
        }

        public @Nullable ActionURL getRedirectURL()
        {
            return _redirectURL;
        }

        /**
         * @return A case-insensitive map of attribute names and values. This will often be empty but will never be null.
         */
        public @NotNull Map<String, String> getAttributeMap()
        {
            return _attributeMap;
        }
    }

    // FailureReasons are only reported to administrators (in the audit log and/or server log), NOT to users (and potential
    // hackers). We try to be as specific as possible.
    enum FailureReason
    {
        userDoesNotExist(ReportType.onFailure, "user does not exist"),
        badPassword(ReportType.onFailure, "incorrect password"),
        badCredentials(ReportType.onFailure, "invalid credentials"),  // Use for cases where we can't distinguish between userDoesNotExist and badPassword
        complexity(ReportType.onFailure, "password does not meet the complexity requirements"),
        expired(ReportType.onFailure, "password has expired"),
        configurationError(ReportType.always, "configuration problem"),
        notApplicable(ReportType.never, "not applicable");

        private final ReportType _type;
        private final String _message;

        FailureReason(ReportType type, String message)
        {
            _type = type;
            _message = message;
        }

        public ReportType getReportType()
        {
            return _type;
        }

        public String getMessage()
        {
            return _message;
        }
    }

    enum ReportType
    {
        always, onFailure, never
    }
}
