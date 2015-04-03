/*
 * Copyright (c) 2007-2015 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public interface AuthenticationProvider
{
    @Nullable ActionURL getConfigurationLink();
    String getName();
    String getDescription();
    void logout(HttpServletRequest request);
    void activate() throws Exception;
    void deactivate() throws Exception;
    boolean isPermanent();

    interface SSOAuthenticationProvider extends AuthenticationProvider
    {
        AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws InvalidEmailException;
    }

    interface LoginFormAuthenticationProvider extends AuthenticationProvider
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws InvalidEmailException;
    }

    interface SecondaryAuthenticationProvider extends AuthenticationProvider
    {
        /**
         *  Initiate secondary authentication process for the specified user. candidate has been authenticated via one of the primary providers,
         *  but isn't officially authenticated until user successfully validates with all enabled SecondaryAuthenticationProviders as well.
         */
        ActionURL getRedirectURL(User candidate, Container c);

        /**
         * Bypass authentication from this provider. Might be configured via labkey.xml parameter to
         * temporarily not require secondary authentication if this has been misconfigured or a 3rd
         * party service provider is unavailable.
         *
         */
        boolean bypass();
    }

    class AuthenticationResponse
    {
        private final @Nullable ValidEmail _email;
        private final @Nullable FailureReason _failureReason;
        private final @Nullable ActionURL _redirectURL;

        private AuthenticationResponse(@NotNull ValidEmail email)
        {
            _email = email;
            _failureReason = null;
            _redirectURL = null;
        }

        private AuthenticationResponse(@NotNull FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            _email = null;
            _failureReason = failureReason;
            _redirectURL = redirectURL;
        }

        public static AuthenticationResponse createSuccessResponse(ValidEmail email)
        {
            return new AuthenticationResponse(email);
        }

        public static AuthenticationResponse createFailureResponse(FailureReason failureReason)
        {
            return new AuthenticationResponse(failureReason, null);
        }

        public static AuthenticationResponse createFailureResponse(FailureReason failureReason, @Nullable ActionURL redirectURL)
        {
            return new AuthenticationResponse(failureReason, redirectURL);
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

        public @Nullable ActionURL getRedirectURL()
        {
            return _redirectURL;
        }
    }

    // FailureReasons are only reported to administrators (in the audit log and/or server log), NOT to users (and potential
    // hackers).  We try to be as specific as possible.
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
