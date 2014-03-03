/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * User: adam
 * Date: Oct 10, 2007
 * Time: 6:49:05 PM
 */
public abstract interface AuthenticationProvider
{
    public @Nullable ActionURL getConfigurationLink();
    public String getName();
    public String getDescription();
    public void logout(HttpServletRequest request);
    public void activate() throws Exception;
    public void deactivate() throws Exception;
    public boolean isPermanent();

    public static interface RequestAuthenticationProvider extends AuthenticationProvider
    {
        public AuthenticationResponse authenticate(HttpServletRequest request, HttpServletResponse response, URLHelper returnURL) throws ValidEmail.InvalidEmailException, RedirectException;
    }

    public static interface LoginFormAuthenticationProvider extends AuthenticationProvider
    {
        // id and password will not be blank (not null, not empty, not whitespace only)
        public AuthenticationResponse authenticate(@NotNull String id, @NotNull String password, URLHelper returnURL) throws ValidEmail.InvalidEmailException, RedirectException;
    }


    public static class AuthenticationResponse
    {
        private final @Nullable ValidEmail _email;
        private final @Nullable FailureReason _failureReason;

        private AuthenticationResponse(ValidEmail email)
        {
            _email = email;
            _failureReason = null;
        }

        private AuthenticationResponse(FailureReason failureReason)
        {
            _email = null;
            _failureReason = failureReason;
        }

        public static AuthenticationResponse createSuccessResponse(ValidEmail email)
        {
            return new AuthenticationResponse(email);
        }

        public static AuthenticationResponse createFailureResponse(FailureReason failureReason)
        {
            return new AuthenticationResponse(failureReason);
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
    }

    // FailureReasons are only reported to administrators (in the audit log and/or server log), NOT to users (and potential
    // hackers).  We try to be as specific as possible.
    public enum FailureReason
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

    public enum ReportType
    {
        always, onFailure, never
    }
}
