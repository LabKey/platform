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
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationConfiguration.LoginFormAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.PrimaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationConfiguration.SecondaryAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.security.AuthenticationManager.AuthenticationValidator;
import org.labkey.api.security.SsoSaveConfigurationAction.SsoSaveConfigurationForm;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.settings.ConfigProperty;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * Override to retrieve and save startup properties intended for this provider. Invoked after new install only.
     */
    default void handleStartupProperties()
    {
    }

    // Helper that retrieves all the configuration properties in the specified categories, populates them into a form, and saves the form
    default <FORM extends SaveConfigurationForm, AC extends AuthenticationConfiguration> void saveStartupProperties(Collection<String> categories, Class<FORM> formClass, Class<AC> configurationClass)
    {
        Map<String, String> map = getPropertyMap(categories);

        if (!map.isEmpty())
        {
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

    // Helper that retrieves all the configuration properties in the specified category and saves them into a property map with the same name
    default void saveStartupProperties(String category)
    {
        Map<String, String> map = getPropertyMap(Collections.singleton(category));

        if (!map.isEmpty())
        {
            PropertyMap propertyMap = PropertyManager.getWritableProperties(category, true);
            propertyMap.clear();
            propertyMap.putAll(map);
            propertyMap.save();
        }
    }

    private Map<String, String> getPropertyMap(Collection<String> categories)
    {
        return categories.stream()
            .flatMap(category-> ModuleLoader.getInstance().getConfigProperties(category).stream())
            .collect(Collectors.toMap(ConfigProperty::getName, ConfigProperty::getValue));
    }

    interface PrimaryAuthenticationProvider<AC extends PrimaryAuthenticationConfiguration<?>> extends AuthenticationProvider, AuthenticationConfigurationFactory<AC>
    {
        default void logout(HttpServletRequest request)
        {
        }

        void migrateOldConfiguration(boolean active, User user) throws Throwable;
    }

    interface LoginFormAuthenticationProvider<AC extends LoginFormAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<AC>, AuthenticationConfigurationFactory<AC>
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        @NotNull AuthenticationResponse authenticate(AC configuration, @NotNull String id, @NotNull String password, URLHelper returnURL) throws InvalidEmailException;

        @Nullable SaveConfigurationForm getFormFromOldConfiguration(boolean active);

        @Override
        default void migrateOldConfiguration(boolean active, User user)
        {
            SaveConfigurationAction.saveOldProperties(getFormFromOldConfiguration(active), user);
        }
    }

    interface SSOAuthenticationProvider<AC extends SSOAuthenticationConfiguration<?>> extends PrimaryAuthenticationProvider<AC>, AuthenticationConfigurationFactory<AC>
    {
        @Nullable SsoSaveConfigurationForm getFormFromOldConfiguration(boolean active, boolean hasLogos);

        @Override
        default void migrateOldConfiguration(boolean active, User user) throws Throwable
        {
            AttachmentService svc = AttachmentService.get();
            AttachmentParent oldParent = AuthenticationLogoAttachmentParent.get();

            List<Attachment> logos = Arrays.stream(AuthLogoType.values())
                .map(alt->svc.getAttachment(oldParent, alt.getOldPrefix() + getName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            SsoSaveConfigurationForm form = getFormFromOldConfiguration(active, !logos.isEmpty());
            SaveConfigurationAction.saveOldProperties(form, user);

            if (null != form && !logos.isEmpty())
            {
                SSOAuthenticationConfiguration<?> configuration = AuthenticationConfigurationCache.getConfiguration(SSOAuthenticationConfiguration.class, form.getRowId());

                List<AttachmentFile> attachmentFiles = svc.getAttachmentFiles(configuration, logos);
                svc.addAttachments(configuration, attachmentFiles, user);
                for (AuthLogoType alt : AuthLogoType.values())
                    svc.renameAttachment(configuration, alt.getOldPrefix() + getName(), alt.getFileName(), user);

                AttachmentCache.clearAuthLogoCache();
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            }
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
        @Nullable SaveConfigurationForm getFormFromOldConfiguration(boolean active);

        default void migrateOldConfiguration(boolean active, User user)
        {
            SaveConfigurationAction.saveOldProperties(getFormFromOldConfiguration(active), user);
        }

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
        private final PrimaryAuthenticationProvider _provider;
        private final @Nullable ValidEmail _email;
        private final @Nullable AuthenticationValidator _validator;
        private final @Nullable FailureReason _failureReason;
        private final @Nullable ActionURL _redirectURL;

        private AuthenticationResponse(@NotNull PrimaryAuthenticationProvider provider, @NotNull ValidEmail email, @Nullable AuthenticationValidator validator)
        {
            _provider = provider;
            _email = email;
            _validator = validator;
            _failureReason = null;
            _redirectURL = null;
        }

        private AuthenticationResponse(@NotNull PrimaryAuthenticationProvider provider, @NotNull FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            _provider = provider;
            _email = null;
            _validator = null;
            _failureReason = failureReason;
            _redirectURL = redirectURL;
        }

        /**
         * Creates a standard authentication provider response
         * @param email Valid email address of the authenticated user
         * @return A new successful authentication response containing the email address of the authenticated user
         */
        public static AuthenticationResponse createSuccessResponse(PrimaryAuthenticationProvider provider, ValidEmail email)
        {
            return createSuccessResponse(provider, email, null);
        }

        /**
         * Creates an authentication provider response that includes a validator to be called on every request
         * @param email Valid email address of the authenticated user
         * @param validator An authentication validator
         * @return A new successful authentication response containing the email address of the authenticated user and a validator
         */
        public static AuthenticationResponse createSuccessResponse(@NotNull PrimaryAuthenticationProvider provider, ValidEmail email, @Nullable AuthenticationValidator validator)
        {
            return new AuthenticationResponse(provider, email, validator);
        }

        public static AuthenticationResponse createFailureResponse(@NotNull PrimaryAuthenticationProvider provider, FailureReason failureReason)
        {
            return new AuthenticationResponse(provider, failureReason, null);
        }

        public static AuthenticationResponse createFailureResponse(@NotNull PrimaryAuthenticationProvider provider, FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            return new AuthenticationResponse(provider, failureReason, redirectURL);
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

        public PrimaryAuthenticationProvider getProvider()
        {
            return _provider;
        }

        public @Nullable ActionURL getRedirectURL()
        {
            return _redirectURL;
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
