/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.authentication.ldap;

import org.apache.log4j.Logger;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpressionFactory;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Map;

/**
 * User: adam
 * Date: May 14, 2008
 * Time: 10:51:27 AM
 */
public class LdapAuthenticationManager
{
    private static final Logger _log = Logger.getLogger(LdapAuthenticationManager.class);

    //
    // Attempt to authenticate by iterating through all the LDAP servers.
    // List of servers is stored as a site property in the database
    //
    public static boolean authenticate(ValidEmail email, String password)
    {
        String[] ldapServers = getServers();
        boolean saslAuthentication = useSASL();
        if (null == ldapServers)
            return false;

        for (String server : ldapServers)
        {
            if (null == server || 0 == server.length())
                continue;

            try
            {
                return authenticate(server, email, password, saslAuthentication);
            }
            catch (NamingException e)
            {
                // Can't find the server... log the exception and try the next one
                _log.error("LDAPLogin: " + server + " failed.", e);
            }
        }

        return false;   // Can't connect to any of the servers
    }


    //
    // Attempt LDAP authentication on a single server
    //
    static boolean authenticate(String url, ValidEmail email, String password, boolean saslAuthentication)
            throws NamingException
    {
        // Don't pass blank or null password or email to connect -- it will ALWAYS SUCCEED
        // We've already verfied above that password and email are not blank

        return connect(url, emailToLdapPrincipal(email), password, saslAuthentication);
    }


    public static String emailToLdapPrincipal(ValidEmail email)
    {
        String emailAddress = email.getEmailAddress();
        int index = emailAddress.indexOf('@');
        String uid = emailAddress.substring(0, index);     // Note: ValidEmail guarantees an @ will be present
        StringExpressionFactory.StringExpression se = StringExpressionFactory.create(getPrincipalTemplate());
        return se.eval(PageFlowUtil.map("email", email, "uid", uid));
    }


    // Careful... blank principal or password will switch to "anonymous bind", meaning it will always succeed
    public static boolean connect(String url, String principal, String password, boolean saslAuthentication) throws NamingException
    {
        boolean authenticated = false;

        try
        {
            Hashtable<String, String> env = new Hashtable<String, String>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
            env.put(Context.PROVIDER_URL, url);
            env.put(Context.SECURITY_AUTHENTICATION, saslAuthentication ? "DIGEST-MD5 CRAM-MD5 GSSAPI" : "simple");
            env.put(Context.SECURITY_PRINCIPAL, principal);
            env.put(Context.SECURITY_CREDENTIALS, password);
            DirContext ctx = new InitialDirContext(env);
            authenticated = true;
            ctx.close();
        }
        catch (AuthenticationException e)
        {
            _log.debug("LDAPAuthenticate: Invalid login. server=" + url + ", security principal=" + principal);
        }

        return authenticated;
    }

    private static final String LDAP_AUTHENTICATION_CATEGORY_KEY = "LDAPAuthentication";

    public static void activate()
    {
        AuthenticationManager.setLdapDomain(LdapAuthenticationManager.getDomain());
    }

    public static void deactivate()
    {
        AuthenticationManager.setLdapDomain("");
    }

    private enum Key {Servers, Domain, PrincipalTemplate, SASL}

    public static void saveProperties(LdapController.Config config)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(LDAP_AUTHENTICATION_CATEGORY_KEY, true);
        map.clear();
        map.put(Key.Servers.toString(), config.getServers());
        map.put(Key.Domain.toString(), config.getDomain());
        map.put(Key.PrincipalTemplate.toString(), config.getPrincipalTemplate());
        map.put(Key.SASL.toString(), config.getSASL() ? "TRUE" : "FALSE");
        PropertyManager.saveProperties(map);
        activate();
    }

    private static Map<String, String> getProperties()
    {
        return PropertyManager.getProperties(LDAP_AUTHENTICATION_CATEGORY_KEY, true);
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
}
