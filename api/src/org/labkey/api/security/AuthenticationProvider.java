/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationConfiguration.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthenticationStatus;
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.StandardStartupPropertyHandler;
import org.labkey.api.settings.StartupProperty;
import org.labkey.api.settings.StartupPropertyEntry;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Returns a list of the field descriptors for the required provider-specific settings. JSON metadata is a small
     * subset of our standard column metadata (e.g., what getQueryDetails.api returns).
     *
     * @return A list of field descriptors or null if this provider doesn't have any custom fields
     */
    default @NotNull List<SettingsField> getSettingsFields()
    {
        return List.of();
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

    interface ConfigurableAuthenticationProvider<AC extends AuthenticationConfiguration<?>> extends AuthenticationProvider
    {
        Class<AC> getConfigurationClass();

        // Translates a single ConfigurationSettings into the appropriate AuthenticationConfiguration
        AC getAuthenticationConfiguration(@NotNull ConfigurationSettings cs);

        // Retrieves all the startup properties in the specified categories, populates them into a form, and saves the form
        default <FORM extends SaveConfigurationForm, T extends Enum<T> & StartupProperty> void saveStartupProperties(String category, Class<FORM> formClass, Class<T> type)
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
                            LOG.info("No description property was provided for {} configuration; using generic description \"{}\".", getName(), description);
                        }

                        List<String> existingDescriptions = AuthenticationConfigurationCache.getConfigurations(getConfigurationClass()).stream()
                            .map(AuthenticationConfiguration::getDescription)
                            .toList();

                        if (!existingDescriptions.isEmpty())
                            LOG.info("Descriptions of existing {} configurations: {}", getName(), existingDescriptions);

                        AuthenticationConfigurationCache.getConfigurations(getConfigurationClass()).stream()
                            .filter(ac -> ac.getDescription().equals(description))
                            .map(AuthenticationConfiguration::getRowId)
                            .findFirst()
                            .ifPresentOrElse(rowId ->
                            {
                                form.setRowId(rowId);
                                LOG.info("Updating existing {} configuration with description \"{}\"", getName(), description);
                            }, () ->
                                LOG.info("Did not find an existing {} configuration with description \"{}\". Creating a new configuration using the specified properties.", getName(), description));

                        AuthenticationConfiguration<?> configuration = SaveConfigurationAction.saveForm(form, null);
                        configuration.handleStartupProperties(map);
                    }
                }
            });
        }
    }

    interface PrimaryAuthenticationProvider<AC extends PrimaryAuthenticationConfiguration<? extends PrimaryAuthenticationProvider<?>>> extends ConfigurableAuthenticationProvider<AC>
    {
    }

    interface LoginFormAuthenticationProvider<AC extends LoginFormAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<AC>
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        @NotNull AuthenticationResponse authenticate(AC configuration, @NotNull String id, @NotNull String password, URLHelper returnUrl) throws InvalidEmailException;
    }

    interface SSOAuthenticationProvider<SSO extends SSOAuthenticationConfiguration<? extends SSOAuthenticationProvider<SSO>>> extends PrimaryAuthenticationProvider<SSO>
    {
        @Override
        default <FORM extends SaveConfigurationForm, T extends Enum<T> & StartupProperty> void saveStartupProperties(String category, Class<FORM> formClass, Class<T> type)
        {
            // SSO authentication provider StartupProperty enums must define AutoRedirect, HeaderLogo, and LoginPageLogo constants
            assert Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals("AutoRedirect") || c.name().equals("HeaderLogo") || c.name().equals("LoginPageLogo")).count() == 3 :
                type.getName() + " does not define required AutoRedirect, HeaderLogo, and LoginPageLogo constants!";

            PrimaryAuthenticationProvider.super.saveStartupProperties(category, formClass, type);
        }

        static String getStartupLogoDescription(String type, String name)
        {
            return "File name of the image to use as the " + type + " logo for this " + name + " configuration. " +
                "Valid values: [a relative path to an image file in the \"startup\" folder, PLACEHOLDER to save a " +
                "placeholder image for this logo, <BLANK> to clear any previously set image]";
        }
    }

    interface RequestAuthenticationProvider<AC extends PrimaryAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<AC>
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

    interface SecondaryAuthenticationProvider<SAC extends SecondaryAuthenticationConfiguration<?>> extends ConfigurableAuthenticationProvider<SAC>
    {
        String REQUIRED_FOR = "requiredFor";

        /**
         * Bypass authentication from this provider. Might be configured via context.bypass2FA=true property in
         * application.properties to temporarily not require secondary authentication if this has been misconfigured, or
         * a 3rd party service provider is unavailable.
         */
        boolean bypass();

        default SettingsField getRequiredForField(String name)
        {
            return OptionsField.of(REQUIRED_FOR, "Require " + name + " for:", "Specifying the role option allows for a progressive roll-out of " + name + " to site users.", true, "all")
                .addOption("all", "All users")
                .addOption("role", "Only users assigned the \"Require Secondary Authentication\" site role");
        }

        @Override
        default <FORM extends SaveConfigurationForm, T extends Enum<T> & StartupProperty> void saveStartupProperties(String category, Class<FORM> formClass, Class<T> type)
        {
            // Secondary authentication provider StartupProperty enums must define RequiredFor constant
            assert Arrays.stream(type.getEnumConstants()).filter(c -> c.name().equals(REQUIRED_FOR)).count() == 1 :
                type.getName() + " does not define requiredFor constant!";

            ConfigurableAuthenticationProvider.super.saveStartupProperties(category, formClass, type);
        }

        @Override
        default boolean allowInsert()
        {
            // For all current secondary providers, it makes no sense to have more than one configuration, so prevent
            // insert if there's already a configuration in place.
            return AuthenticationConfigurationCache.getConfigurations(getConfigurationClass()).isEmpty();
        }
    }

    interface DisableLoginProvider extends AuthenticationProvider
    {
        boolean isEnabledForUser(String id);

        /**
         * @param id user email string
         * @return Login delay in milliseconds
         */
        long getUserDelay(String id) throws LoginDisabledException;

        void addUserDelay(HttpServletRequest request, String id, int addCount);

        void resetUserDelay(String id);
    }

    interface ExpireAccountProvider extends AuthenticationProvider
    {
        boolean isEnabled();
    }

    class AuthenticationResponse
    {
        private final PrimaryAuthenticationConfiguration<?> _configuration;               // The PrimaryAuthenticationConfiguration that was used in this authentication attempt
        // Success means either _user or _email is set, but not both. Check _user first.
        // Failure means both are null
        private final @Nullable ValidEmail _email;                                        // Valid email address of the authenticated user
        private final @Nullable User _user;
        private final @Nullable FailureReason _failureReason;

        private @Nullable AuthenticationValidator _validator = null;
        private @Nullable ActionURL _redirectURL = null;
        private @NotNull Map<String, String> _userAttributeMap = Collections.emptyMap();  // A case-insensitive map of attribute names and values associated with the user
        private @NotNull Map<String, Object> _authenticationProperties = Collections.emptyMap();
        private boolean _requireSecondary = true;                                         // Require secondary authentication
        private boolean _reauth = false;
        private @Nullable String _successDetails = null;                                  // An optional string describing how successful authentication took place, which will
                                                                                          // appear in the audit log. If null, the configuration's description will be used.

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @Nullable ValidEmail email, @Nullable User user)
        {
            _configuration = configuration;
            _user = user;
            _email = email;
            _successDetails = "the \"" + configuration.getDescription() + "\" configuration";
            _failureReason = null;
        }

        private AuthenticationResponse(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull FailureReason failureReason)
        {
            _configuration = configuration;
            _user = null;
            _email = null;
            _failureReason = failureReason;
        }

        public static AuthenticationResponse success(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull ValidEmail email)
        {
            return new AuthenticationResponse(configuration, email, null);
        }

        public static AuthenticationResponse success(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull User user)
        {
            return new AuthenticationResponse(configuration, null, user);
        }

        public static AuthenticationResponse failure(@NotNull PrimaryAuthenticationConfiguration<?> configuration, @NotNull FailureReason failureReason)
        {
            return new AuthenticationResponse(configuration, failureReason);
        }

        public boolean isAuthenticated()
        {
            return null != _email || null != _user;
        }

        public PrimaryAuthenticationConfiguration<?> getConfiguration()
        {
            return _configuration;
        }

        public @NotNull FailureReason getFailureReason()
        {
            assert null != _failureReason && !isAuthenticated();

            return _failureReason;
        }

        public @Nullable User getUser()
        {
            return _user;
        }

        // Call only if isAuthenticated() is true and getUser() is null
        public @NotNull ValidEmail getValidEmail()
        {
            assert null != _email && _user == null;

            return _email;
        }

        public @Nullable AuthenticationValidator getValidator()
        {
            return _validator;
        }

        public AuthenticationResponse setValidator(@Nullable AuthenticationValidator validator)
        {
            _validator = validator;
            return this;
        }

        public @Nullable ActionURL getRedirectURL()
        {
            return _redirectURL;
        }

        public AuthenticationResponse setRedirectURL(@Nullable ActionURL redirectURL)
        {
            _redirectURL = redirectURL;
            return this;
        }

        /**
         * @return A case-insensitive map of attribute names and values associated with the authenticated user. This
         * will often be empty but will never be null.
         */
        public @NotNull Map<String, String> getUserAttributeMap()
        {
            return _userAttributeMap;
        }

        public AuthenticationResponse setUserAttributeMap(@NotNull Map<String, String> userAttributeMap)
        {
            _userAttributeMap = userAttributeMap;
            return this;
        }

        /**
         * @return A case-insensitive map of properties about the authentication process
         */
        public @NotNull Map<String, Object> getAuthenticationProperties()
        {
            return _authenticationProperties;
        }

        /**
         * Set a case-insensitive map of properties about the authentication process
         */
        public AuthenticationResponse setAuthenticationProperties(Map<String, Object> map)
        {
            _authenticationProperties = map;
            return this;
        }

        public @Nullable String getSuccessDetails()
        {
            return _successDetails;
        }

        public AuthenticationResponse setSuccessDetails(@Nullable String successDetails)
        {
            _successDetails = successDetails;
            return this;
        }

        public boolean requireSecondary()
        {
            return _requireSecondary;
        }

        public AuthenticationResponse setRequireSecondary(boolean requireSecondary)
        {
            _requireSecondary = requireSecondary;
            return this;
        }

        public boolean isReauth()
        {
            return _reauth;
        }

        public AuthenticationResponse setReauth(boolean reauth)
        {
            _reauth = reauth;
            return this;
        }
    }

    // FailureReasons are only reported to administrators (in the audit log and/or server log), NOT to users (and potential
    // hackers). We try to be as specific as possible.
    enum FailureReason
    {
        userDoesNotExist(ReportType.onFailure, "user does not exist", null),
        badPassword(ReportType.onFailure, "incorrect password", null),
        badCredentials(ReportType.onFailure, "invalid credentials", null),  // Use for cases where we can't distinguish between userDoesNotExist and badPassword
        complexity(ReportType.onFailure, "password does not meet the complexity requirements", AuthenticationStatus.Complexity),
        expired(ReportType.onFailure, "password has expired", AuthenticationStatus.PasswordExpired),
        configurationError(ReportType.always, "configuration problem", null),
        notApplicable(ReportType.never, "not applicable", null),
        badApiKey(ReportType.onFailure, "invalid API key", AuthenticationStatus.BadApiKey) {
            @Override
            public @Nullable String getEmailAddress(ValidEmail email)
            {
                // This override prevents logging a strange "apikey@domain.com"-type failure message. Invalid API key
                // means email is unknown, so always return null.
                return null;
            }
        };

        private final ReportType _type;
        private final String _message;
        private final @Nullable AuthenticationStatus _associatedStatus;

        FailureReason(ReportType type, String message, @Nullable AuthenticationStatus associatedStatus)
        {
            _type = type;
            _message = message;
            _associatedStatus = associatedStatus;
        }

        public ReportType getReportType()
        {
            return _type;
        }

        public String getMessage()
        {
            return _message;
        }

        public @Nullable String getEmailAddress(ValidEmail email)
        {
            return email.getEmailAddress();
        }

        public @Nullable AuthenticationStatus getAssociatedStatus()
        {
            return _associatedStatus;
        }
    }

    enum ReportType
    {
        always, onFailure, never
    }
}
