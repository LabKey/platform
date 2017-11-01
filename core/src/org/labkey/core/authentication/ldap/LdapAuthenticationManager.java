/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.core.authentication.ldap;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

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
    // Attempt LDAP authentication on a single server
    //
    static boolean authenticate(String url, @NotNull ValidEmail email, @NotNull String password, boolean saslAuthentication) throws NamingException
    {
        if (useEmailAsPrincipal())
        {
            return connect(url, substituteEmailTemplate(getPrincipalTemplate(), email), password, saslAuthentication);
        }
        else
        {
            return searchAndConnect(url, email, password, saslAuthentication);
        }
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

    static final String LDAP_AUTHENTICATION_CATEGORY_KEY = "LDAPAuthentication";

    public static void activate()
    {
        AuthenticationManager.setLdapDomain(LdapAuthenticationManager.getDomain());
        ldapSearchConfig = lookupLdapSearchConfig();
        _log.debug("activate(): LDAP search config set to: " + ldapSearchConfig);
    }

    public static void deactivate()
    {
        AuthenticationManager.setLdapDomain("");
        ldapSearchConfig = null;
        _log.debug("deactivate(): LDAP search config set to null");
    }
    
    // Holds configuration info for searching LDAP for case where neither full email address (email)
    // nor first part of it (uid) matches the LDAP account name.
    // An instance is initialized in activate() and cleared in deactivate().
    // The username and password are used by searchAndConnect(); searchBase is used by findLdapAccountNameFromEmail(). 
    private static class LdapSearchConfig
    {
        public final String username;
        public final String password;
        public final String searchBase;
        public final String lookupField;
        public final String searchTemplate;
        
        public LdapSearchConfig(String username, String password, String searchBase, String lookupField, String searchTemplate)
        {
            this.username = username;
            this.password = password;
            this.searchBase = searchBase;
            this.lookupField = lookupField;
            this.searchTemplate = searchTemplate;
        }
        
        @Override
        public String toString()
        {
            return "username: " + username + " searchBase: " + searchBase + " lookupField: " + lookupField + " searchTemplate: " + searchTemplate; // Omit password - don't want it showing up in logs.
        }
    }
    
    private static LdapSearchConfig ldapSearchConfig = null;
    
    // Get configuration for searching LDAP, if so configured. If not so configured, silently return null.
    private static LdapSearchConfig lookupLdapSearchConfig()
    {
        Context jndiCtx;
        String username, password, searchBase;
        try
        {
            jndiCtx = (Context) new InitialContext().lookup("java:comp/env");
            username = (String) jndiCtx.lookup("ldapSearch_username");
            password = (String) jndiCtx.lookup("ldapSearch_password");
            searchBase = (String) jndiCtx.lookup("ldapSearch_searchBase");
        }
        catch (NamingException ne)
        {
            // Not configured (or configured incorrectly) for searching LDAP.
            _log.debug("ldapSearch_* values not configured in Environment of context xml.", ne);
            return null;
        }

        String lookupField;
        try
        {
            lookupField = (String) jndiCtx.lookup("ldapSearch_lookupField");
        }
        catch (NamingException ne)
        {
            lookupField = LDAP_ACCOUNT_NAME_ATTR;
        }

        String searchTemplate;
        try
        {
            searchTemplate = (String) jndiCtx.lookup("ldapSearch_searchTemplate");
        }
        catch (NamingException ne)
        {
            searchTemplate = "(&(objectClass=user)(mail=${email}))";
        }

        return new LdapSearchConfig(username, password, searchBase, lookupField, searchTemplate);
        // No explicit close() here because Tomcat 7 changed to throw on every call to close(). For more information,
        // see #18777 and https://issues.apache.org/bugzilla/show_bug.cgi?id=51744
    }
    
    // Returns true if we're using the email as the LDAP principal.
    // Returns false if we search for the LDAP principal using the email.
    private static boolean useEmailAsPrincipal()
    {
        return(ldapSearchConfig == null);
    }

    // Handle case where given principal is an email address, but that doesn't match the account name in LDAP -
    // we want to use sAMAccountName instead. We connect to LDAP using a specially-configured set of credentials,
    // search for an entry with the given principal as its email address,
    // and if we find one, we try to authenticate using the entry's sAMAccountName as the principal.
    private static boolean searchAndConnect(String url, @NotNull ValidEmail email, @NotNull String password, boolean saslAuthentication) throws NamingException
    {
        if (ldapSearchConfig == null)
        {
            // Not configured (or configured incorrectly) for LDAP search.
            return false;
        }
        
        DirContext ldapCtx = null;
        try
        {
            // Connect to LDAP using configured credentials.
            // If that fails, log a warning, and return false.
            try
            {
                ldapCtx = connectToLdap(url, ldapSearchConfig.username, ldapSearchConfig.password, saslAuthentication);
            }
            catch (NamingException e)
            {
                _log.warn("Failed to connect to LDAP for search: " + ldapSearchConfig.username + "@" + url, e);
                throw e;
            }
            
            String accountName = findLdapAccountNameFromEmail(ldapCtx, email, ldapSearchConfig.lookupField, ldapSearchConfig.searchTemplate);
            if (accountName == null)
            {
                // Couldn't find LDAP user with that email.
                _log.warn("LDAP search could not find the user with email: " + email);
                return false;
            }
            
            // Found user in LDAP.  Re-try using their accountName rather than their email.
            return connect(url, accountName, password, saslAuthentication);
        }
        finally
        {
            if (ldapCtx != null)
            {
                ldapCtx.close();
            }
        }
    }
    
    private static final String LDAP_ACCOUNT_NAME_ATTR = "sAMAccountName";

    // Returns SAMAccountName of user in LDAP that has the given email,
    // or null, if there is no such user, or any unexpected exception is thrown.
    private static String findLdapAccountNameFromEmail(DirContext ldapCtx, ValidEmail email, String ldapAccountNameAttr, String searchTemplate)
    {
        // Search subtrees starting at searchBase.
        SearchControls searchCtls = new SearchControls();
        searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        
        // searchCtls.setReturningAttributes(returnedAtts);
        
        // We're looking for an LDAP user with given email.
        // an optional searchTemplate can be provided in labkey.xml
        String searchFilter = substituteEmailTemplate(searchTemplate, email);

        NamingEnumeration<SearchResult> results;
        try
        {
            results = ldapCtx.search(ldapSearchConfig.searchBase, searchFilter, searchCtls);
        }
        catch (NamingException ne)
        {
            // Something wrong with config or searchFilter.
            _log.warn("Exception while searching LDAP: searchConfig: " + ldapSearchConfig + " searchFilter: " + searchFilter, ne);
            return null;
        }

        // If principal/email is not valid, search() does NOT throw an exception.
        // Instead, when you try to do anything with results, that throws a NamingException.
        Attributes attributes;
        try
        {
            if (!results.hasMoreElements())
            {
                _log.debug("No LDAP user found with email: " + email);
                return null;
            }
            attributes = results.next().getAttributes();
        }
        catch (NamingException ne)
        {
            // search() did not find LDAP user with given email.
            _log.debug("No LDAP user found with email: " + email);
            return null;
        }

        if (attributes.get(ldapAccountNameAttr) == null)
        {
            _log.warn("LDAP user with email: " + email + " does not have " + ldapAccountNameAttr + " attribute, so can't authenticate.");
            return null;
        }
        
        try
        {
            String accountName = attributes.get(ldapAccountNameAttr).get().toString();
            _log.debug("Converted LDAP principal from email \"" + email + "\" to " + ldapAccountNameAttr + "\"" + accountName + "\"");
            return accountName;
        }
        catch (NamingException ne)
        {
            _log.warn("Exception while getting value of " + ldapAccountNameAttr + " attribute of LDAP user with email: " + email, ne);
            return null;
        }
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
}
