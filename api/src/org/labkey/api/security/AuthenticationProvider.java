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

import org.apache.logging.log4j.Logger;
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
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;

import jakarta.servlet.http.HttpServletRequest;
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
    Logger LOG = LogHelper.getLogger(AuthenticationProvider.class, "Authentication startup property actions");

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

    default boolean allowInsert()
    {
        return true;
    }

    /**
     * Override to retrieve and save startup properties intended for this provider. Invoked after every server startup.
     */
    default void handleStartupProperties()
    {
    }

    static String getDescriptionDocumentation(String name)
    {
        return "Description of this " + name + " configuration. Providing a description is required to update an existing configuration and strongly recommended when creating a new configuration.";
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
                    final String description;

                    if (null != form.getDescription())
                    {
                        description = form.getDescription();
                    }
                    else
                    {
                        description = getName() + " Configuration";
                        form.setDescription(description);
                        LOG.info("No description property was provided for " + getName() + " configuration; using generic description \"" + description + "\".");
                    }

                    List<String> existingDescriptions = AuthenticationConfigurationCache.getConfigurations(configurationClass).stream()
                        .map(AuthenticationConfiguration::getDescription)
                        .toList();

                    if (!existingDescriptions.isEmpty())
                        LOG.info("Descriptions of existing " + getName() + " configurations: " + existingDescriptions);

                    AuthenticationConfigurationCache.getConfigurations(configurationClass).stream()
                        .filter(ac -> ac.getDescription().equals(description))
                        .map(AuthenticationConfiguration::getRowId)
                        .findFirst()
                        .ifPresentOrElse(rowId ->
                        {
                            form.setRowId(rowId);
                            LOG.info("Updating existing " + getName() + " configuration with description \"" + description + "\"");
                        }, () ->
                            LOG.info("Did not find an existing " + getName() + " configuration with description \"" + description + "\". Creating a new configuration using the specified properties."));

                    AuthenticationConfiguration<?> configuration = SaveConfigurationAction.saveForm(form, null);
                    configuration.handleStartupProperties(map);
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
            // SSO authentication provider StartupProperty enums must define AutoRedirect, HeaderLogo, and LoginPageLogo constants
            assert Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals("AutoRedirect") || c.name().equals("HeaderLogo") || c.name().equals("LoginPageLogo")).count() == 3 :
                type.getName() + " does not define required AutoRedirect, HeaderLogo, and LoginPageLogo constants!";

            PrimaryAuthenticationProvider.super.saveStartupProperties(category, formClass, configurationClass, type);
        }

        static String getStartupLogoDescription(String type, String name)
        {
            return "File name of the image to use as the " + type + " logo for this " + name + " configuration. " +
                "Valid values: [a relative path to an image file in the \"startup\" folder, PLACEHOLDER to save a " +
                "placeholder image for this logo, <BLANK> to clear any previously set image]";
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
         * Bypass authentication from this provider. Might be configured via context.bypass2FA=true property in
         * application.properties to temporarily not require secondary authentication if this has been misconfigured or
         * a 3rd party service provider is unavailable.
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
        private final @Nullable String _successDetails;
        private final boolean _requireSecondary;

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull ValidEmail email, @Nullable AuthenticationValidator validator, @NotNull Map<String, String> attributeMap, @Nullable String successDetails, boolean requireSecondary)
        {
            _configuration = configuration;
            _email = email;
            _validator = validator;
            _attributeMap = attributeMap;
            _failureReason = null;
            _redirectURL = null;
            _successDetails = null != successDetails ? successDetails : "the \"" + configuration.getDescription() + "\" configuration";
            _requireSecondary = requireSecondary;
        }

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            _configuration = configuration;
            _email = null;
            _validator = null;
            _failureReason = failureReason;
            _redirectURL = redirectURL;
            _attributeMap = Collections.emptyMap();
            _successDetails = null;
            _requireSecondary = true;
        }

        /**
         * Creates a standard authentication provider response
         * @param email Valid email address of the authenticated user
         * @return A new successful authentication response containing the email address of the authenticated user
         */
        public static AuthenticationResponse createSuccessResponse(PrimaryAuthenticationConfiguration<?> configuration, ValidEmail email)
        {
            return createSuccessResponse(configuration, email, null, Collections.emptyMap(), null, true);
        }

        /**
         * Creates an authentication provider response that can include a validator to be called on every request and a
         * map of user attributes
         *
         * @param configuration     The PrimaryAuthenticationConfiguration that was used in this authentication attempt
         * @param email             Valid email address of the authenticated user
         * @param validator         An authentication validator
         * @param attributeMap      A <b>case-insensitive</b> map of attribute names and values associated with this authentication
         * @param successDetails    An optional string describing how successful authentication took place, which will appear in
         *                          the audit log. If null, the configuration's description will be used.
         * @param requireSecondary  Require secondary authentication
         * @return A new successful authentication response containing the email address of the authenticated user and a validator
         */
        public static AuthenticationResponse createSuccessResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, ValidEmail email, @Nullable AuthenticationValidator validator, @NotNull Map<String, String> attributeMap, @Nullable String successDetails, boolean requireSecondary)
        {
            return new AuthenticationResponse(configuration, email, validator, attributeMap, successDetails, requireSecondary);
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

        public String getSuccessDetails()
        {
            return _successDetails;
        }

        public boolean requireSecondary()
        {
            return _requireSecondary;
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
        notApplicable(ReportType.never, "not applicable"),
        badApiKey(ReportType.onFailure, "invalid API key") {
            @Override
            public @Nullable String getEmailAddress(ValidEmail email) throws InvalidEmailException
            {
                // This override prevents logging a strange "apikey@domain.com"-type failure message. Invalid API key
                // means email is unknown, so always return null.
                return null;
            }
        };

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

        public @Nullable String getEmailAddress(ValidEmail email) throws InvalidEmailException
        {
            return email.getEmailAddress();
        }
    }

    enum ReportType
    {
        always, onFailure, never
    }
}
