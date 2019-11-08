package org.labkey.api.security;

import org.labkey.api.security.AuthenticationProvider.LoginFormAuthenticationProvider;

public interface LoginFormAuthenticationConfiguration<AP extends LoginFormAuthenticationProvider> extends AuthenticationConfiguration<AP>
{
}
