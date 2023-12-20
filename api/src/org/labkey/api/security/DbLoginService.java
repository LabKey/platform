package org.labkey.api.security;

import org.labkey.api.data.Container;
import org.labkey.api.security.AuthenticationManager.AuthenticationResult;
import org.labkey.api.security.ValidEmail.InvalidEmailException;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.URLHelper;
import org.springframework.validation.BindException;

import jakarta.servlet.http.HttpServletRequest;

public interface DbLoginService
{
    static void setInstance(DbLoginService impl)
    {
        ServiceRegistry.get().registerService(DbLoginService.class, impl);
    }

    static DbLoginService get()
    {
        return ServiceRegistry.get().getService(DbLoginService.class);
    }

    @Deprecated // Call the variant that takes a changeOperation parameter
    default AuthenticationResult attemptSetPassword(Container c, User currentUser, String rawPassword, String rawPassword2, HttpServletRequest request, ValidEmail email, URLHelper returnUrlHelper, String auditMessage, boolean clearVerification, BindException errors) throws InvalidEmailException
    {
        return attemptSetPassword(c, currentUser, rawPassword, rawPassword2, request, email, returnUrlHelper, auditMessage, clearVerification, false, errors);
    }

    AuthenticationResult attemptSetPassword(Container c, User currentUser, String rawPassword, String rawPassword2, HttpServletRequest request, ValidEmail email, URLHelper returnUrlHelper, String auditMessage, boolean clearVerification, boolean changeOperation, BindException errors) throws InvalidEmailException;

    PasswordRule getPasswordRule();
}
