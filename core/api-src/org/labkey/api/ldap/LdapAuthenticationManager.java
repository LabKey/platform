/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.ldap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.UsageReportingLevel;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * User: adam
 * Date: May 14, 2008
 * Time: 10:51:27 AM
 */
public class LdapAuthenticationManager
{
    private static final Logger _log = Logger.getLogger(LdapAuthenticationManager.class);

    private static volatile LdapAuthenticator _authenticator = (url, email, password, saslAuthentication) -> connect(url, substituteEmailTemplate(getPrincipalTemplate(), email), password, saslAuthentication);

    //
    // Attempt LDAP authentication on a single server
    //
    public static boolean authenticate(String url, @NotNull ValidEmail email, @NotNull String password, boolean saslAuthentication) throws NamingException
    {
        return _authenticator.authenticate(url, email, password, saslAuthentication);
    }


    public static void setAuthenticator(LdapAuthenticator authenticator)
    {
        _authenticator = authenticator;
    }


    public static String substituteEmailTemplate(String template, ValidEmail email)
    {
        String emailAddress = email.getEmailAddress();
        int index = emailAddress.indexOf('@');
        String uid = emailAddress.substring(0, index);     // Note: ValidEmail guarantees an @ will be present
        StringExpression se = StringExpressionFactory.create(template);
        return se.eval(PageFlowUtil.map("email", email, "uid", uid));
    }


    // Principal and password shouldn't be empty
    public static boolean connect(String url, @NotNull String principal, @NotNull String password, boolean saslAuthentication) throws NamingException
    {
        boolean authenticated = false;
        DirContext ctx = null;

        try
        {
            ctx = connectToLdap(url, principal, password, saslAuthentication);
            authenticated = true;
        }
        catch (AuthenticationException e)
        {
            _log.debug("LDAPAuthenticate: Invalid login. server=" + url + ", security principal=" + principal);
        }
        finally
        {
            if (ctx != null)
            {
                ctx.close();
            }
        }

        return authenticated;
    }


    // Blank principal or password will throw to prevent anonymous bind
    public static DirContext connectToLdap(String url, @NotNull String principal, @NotNull String password, boolean saslAuthentication) throws NamingException
    {
        if (StringUtils.isBlank(password))
            throw new IllegalStateException("Blank password is not allowed in connectToLdap");

        if (StringUtils.isBlank(principal))
            throw new ConfigurationException("Blank principal is not allowed in connectToLdap: LDAP is likely misconfigured");

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, saslAuthentication ? "DIGEST-MD5 CRAM-MD5 GSSAPI" : "simple");
        env.put(Context.SECURITY_PRINCIPAL, principal);
        env.put(Context.SECURITY_CREDENTIALS, password);

        return new InitialDirContext(env);
    }

    public static final String LDAP_AUTHENTICATION_CATEGORY_KEY = "LDAPAuthentication";

    public static void activate()
    {
        AuthenticationManager.setLdapDomain(LdapAuthenticationManager.getDomain());
    }

    public static void deactivate()
    {
        AuthenticationManager.setLdapDomain("");
    }
    
    private enum Key {Servers, Domain, PrincipalTemplate, SASL}

    public static void saveProperties(Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(LDAP_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Servers.toString(), config.getServers());
        map.put(Key.Domain.toString(), config.getDomain());
        map.put(Key.PrincipalTemplate.toString(), config.getPrincipalTemplate());
        map.put(Key.SASL.toString(), config.getSASL() ? "TRUE" : "FALSE");
        map.save();
        activate();
    }

    private static Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(LDAP_AUTHENTICATION_CATEGORY_KEY);
    }

    private static String getProperty(Key key, String defaultValue)
    {
        Map<String, String> props = getProperties();

        String value = props.get(key.toString());

        if (null != value)
            return value;
        else
            return defaultValue;
    }

    @NotNull
    public static String[] getServers()
    {
        return getProperty(Key.Servers, "").split(";");
    }

    public static String getDomain()
    {
        return getProperty(Key.Domain, "");
    }

    public static String getPrincipalTemplate()
    {
        return getProperty(Key.PrincipalTemplate, "${email}");
    }

    public static boolean useSASL()
    {
        return "TRUE".equalsIgnoreCase(getProperty(Key.SASL, "FALSE"));
    }

    public static void registerMetricsProvider()
    {
        UsageMetricsService.get().registerUsageMetrics(UsageReportingLevel.MEDIUM, ModuleLoader.getInstance().getCoreModule().getName(), () -> {
            Map<String, Object> results = new LinkedHashMap<>();
            results.put("enabled", AuthenticationManager.getActiveProviders().stream().anyMatch(p->"LDAP".equals(p.getName())));
            _authenticator.addMetrics(results);
            return Collections.singletonMap("ldap", results);
        });
    }
}
