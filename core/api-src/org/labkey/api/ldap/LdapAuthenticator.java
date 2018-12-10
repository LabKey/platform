package org.labkey.api.ldap;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.ValidEmail;

import javax.naming.NamingException;
import java.util.Map;

public interface LdapAuthenticator
{
    boolean authenticate(String url, @NotNull ValidEmail email, @NotNull String password, boolean saslAuthentication) throws NamingException;
    default void addMetrics(Map<String, Object> map){}
}
