export const CAS_MODAL_TYPE = {
    "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configureCas",
    "saveLink" : "/labkey/casclient-casSaveConfiguration.view?",
    "settingsFields" : [ {
        "defaultValue" : "",
        "name" : "serverUrl",
        "caption" : "CAS Server URL",
        "description" : "Enter a valid HTTPS URL to your CAS server. The URL should start with https:// and end with /cas, for example: https://test.org/cas.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : false,
        "name" : "autoRedirect",
        "caption" : "Default to CAS Login",
        "description" : "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
        "type" : "checkbox",
        "required" : false
    } ],
    "description" : "Central Authentication Service (CAS)",
    "sso" : true
};

export const LDAP_MODAL_TYPE = {
    "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configLdap",
    "saveLink" : "/labkey/ldap-ldapSaveConfiguration.view?",
    "settingsFields" : [ {
        "defaultValue" : "",
        "name" : "servers",
        "caption" : "LDAP server URLs",
        "description" : "The address(es) of your organization's LDAP server or servers. You can provide a list of multiple servers separated by semicolons. The general form for an LDAP server address is ldap://servername.domain.org",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "labkey.com",
        "name" : "domain",
        "caption" : "LDAP domain",
        "description" : "Email domain that determines which users will authenticate using this LDAP configuration. For example, if set to 'labkey.org', all users who enter xxxxxx@labkey.org will attempt authentication using this configuration. Specify '*' to attempt LDAP authentication on all email addresses entered, regardless of domain.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "${email}",
        "name" : "principalTemplate",
        "caption" : "LDAP principal template",
        "description" : "LDAP principal template that matches the DN requirements of the configured LDAP server(s). The template supports substitution syntax; include ${email} to substitute the user's full email address and ${uid} to substitute the left part of the user's email address.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : false,
        "name" : "SASL",
        "caption" : "Use SASL authentication",
        "type" : "checkbox",
        "required" : false
    }, {
        "defaultValue" : false,
        "dictateFieldVisibility" : true,
        "name" : "search",
        "caption" : "Use LDAP Search",
        "description" : "The LDAP Search option is rarely needed. It is useful when the LDAP server is configured to authenticate with a user name that is unrelated to the user's email address.",
        "type" : "checkbox",
        "required" : false
    }, {
        "defaultValue" : "",
        "name" : "username",
        "caption" : "Search DN",
        "description" : "Distinguished name of the LDAP user that will be used to search the LDAP directory.",
        "type" : "input",
        "required" : false
    }, {
        "defaultValue" : "",
        "name" : "password",
        "caption" : "Password",
        "description" : "Password for the LDAP user specified above.",
        "type" : "password",
        "required" : false
    }, {
        "defaultValue" : "",
        "name" : "searchBase",
        "caption" : "Search base",
        "description" : "Search base to use. This could be the root of your directory or the base that contains all of your user accounts.",
        "type" : "input",
        "required" : false
    }, {
        "defaultValue" : "sAMAccountName",
        "name" : "lookupField",
        "caption" : "Lookup field",
        "description" : "User record field name to use for authenticating via LDAP. The value of this field will be substituted into the principal template to generate a DN for authenticating.",
        "type" : "input",
        "required" : false
    }, {
        "defaultValue" : "(&(objectClass=user)(mail=${email}))",
        "name" : "searchTemplate",
        "caption" : "Search template",
        "description" : "Filter to apply during the LDAP search. Valid substitution patterns are ${email} and ${uid}.",
        "type" : "input",
        "required" : false
    } ],
    "description" : "Uses the LDAP protocol to authenticate against an institution's directory server",
    "testLink" : "/labkey/ldap-testLdap.view?",
    "sso" : false
};

export const SAML_MODAL_TYPE = {
    "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=saml",
    "saveLink" : "/labkey/saml-samlSaveConfiguration.view?",
    "settingsFields" : [ {
        "defaultValue" : "",
        "name" : "IdPSigningCertificate",
        "caption" : "IdP Signing Certificate",
        "type" : "pem",
        "required" : true
    }, {
        "defaultValue" : "",
        "name" : "SpEncryptCert",
        "caption" : "Encryption Certificate",
        "description" : "Encryption Certificate for Encrypted Assertion",
        "type" : "pem",
        "required" : false
    }, {
        "defaultValue" : "",
        "name" : "SpPrivateKey",
        "caption" : "SP Private Key",
        "description" : "Private Key for the Encryption Certificate. Required if encryption cert is used.",
        "type" : "pem",
        "required" : false
    }, {
        "defaultValue" : "",
        "name" : "IdPSsoUrl",
        "caption" : "IdP SSO URL",
        "description" : "Target IdP URL for SSO login",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "",
        "name" : "IssuerUrl",
        "caption" : "Issuer URL",
        "description" : "Issuer of SP SAML metadata",
        "type" : "input",
        "required" : false
    }, {
        "defaultValue" : "emailAddress",
        "name" : "NameIdFormat",
        "options" : {
            "emailAddress" : "Email Address",
            "transient" : "Transient",
            "unspecified" : "Unspecified"
        },
        "caption" : "NameID Format",
        "description" : "If IdP does not support 'Email Address' then select 'Transient'",
        "type" : "options",
        "required" : false
    }, {
        "defaultValue" : false,
        "name" : "ForceAuth",
        "caption" : "Force Authorization",
        "description" : "Require user to login to IdP even if they already have an active SSO session.",
        "type" : "checkbox",
        "required" : false
    }, {
        "caption" : "EntityId",
        "html" : "The entityId for this server is the base server URL: http://localhost:8080<br>You can configure the base server URL on the <a href=\"/labkey/admin-customizeSite.view?\" target=\"_customize\">Customize Site page</a>.",
        "type" : "fixedHtml"
    }, {
        "caption" : "Assertion Consumer Service (ACS) URL",
        "html" : "This is relative to the base server URL. The ACS URL for this server is: http://localhost:8080/labkey/saml-validate.view",
        "type" : "fixedHtml"
    } ],
    "description" : "Acts as a service provider (SP) to authenticate against a SAML 2.0 Identity Provider (IdP)",
    "sso" : true
};

export const DUO_MODAL_TYPE = {
    "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configureDuoTwoFactor",
    "saveLink" : "/labkey/duo-duoSaveConfiguration.view?",
    "settingsFields" : [ {
        "defaultValue" : "",
        "name" : "integrationKey",
        "caption" : "Integration Key",
        "description" : "Your Labkey Admin with a Duo administrative account should have generated this key.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "",
        "name" : "secretKey",
        "caption" : "Secret Key",
        "description" : "Your Labkey Admin with a Duo administrative account should have generated this key.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "",
        "name" : "apiHostname",
        "caption" : "API Hostname",
        "description" : "Your Labkey Admin with a Duo administrative account should have the hostname along with Integration and Secret Key.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : "UserID",
        "name" : "userIdentifier",
        "options" : {
            "UserID" : "User ID",
            "UserName" : "User Name",
            "FullEmailAddress" : "Full Email Address"
        },
        "caption" : "User Identifier",
        "description" : "Choose the way LabKey will match with Duo accounts.",
        "type" : "options",
        "required" : true
    } ],
    "description" : "Require two-factor authentication via Duo"
};

export const SSO_CONFIGURATIONS = [ {
    "IdPSsoUrl" : "IdP SSO URL",
    "configuration" : 95,
    "headerLogoUrl" : null,
    "loginLogoUrl" : null,
    "description" : "SAML Configuration",
    "NameIdFormat" : "transient",
    "IdPSigningCertificate" : "certificateText",
    "enabled" : true,
    "ForceAuth" : true,
    "IssuerUrl" : "",
    "provider" : "SAML",
    "details" : "IdP SSO URL",
    "SpEncryptCert" : ""
}, {
    "provider" : "CAS",
    "configuration" : 106,
    "headerLogoUrl" : "/labkey/auth_header_logo.image?configuration=106&revision=69",
    "loginLogoUrl" : "/labkey/auth_login_page_logo.image?configuration=106&revision=69",
    "serverUrl" : "https://www.labkey.org/cas",
    "description" : "CAS Configurations",
    "details" : "https://www.labkey.org/cas",
    "autoRedirect" : false,
    "enabled" : true
}, {
    "provider" : "CAS",
    "configuration" : 108,
    "headerLogoUrl" : null,
    "loginLogoUrl" : null,
    "serverUrl" : "https://www.labkey.org/cas",
    "description" : "CAS Configuration 2",
    "details" : "https://www.labkey.org/cas",
    "autoRedirect" : false,
    "enabled" : true
} ];

export const UPDATED_CONFIG = {
    "provider" : "CAS",
    "configuration" : 108,
    "headerLogoUrl" : null,
    "loginLogoUrl" : null,
    "serverUrl" : "https://www.labkey.org/cas",
    "description" : "CAS Configuration X",
    "details" : "https://www.labkey.org/cas",
    "autoRedirect" : false,
    "enabled" : true
};

export const FORM_CONFIGURATIONS = [ {
    "sasl" : false,
    "search" : false,
    "servers" : "ldap://ldap.forumsys.com",
    "provider" : "LDAP",
    "configuration" : 107,
    "principalTemplate" : "${email}",
    "domain" : "labkey.com",
    "description" : "LDAP Configurations",
    "details" : "ldap://ldap.forumsys.com",
    "enabled" : true
}, {
    "provider" : "Database",
    "configuration" : 0,
    "description" : "Standard database authentication",
    "details" : null,
    "enabled" : true
}];

export const SECONDARY_CONFIGURATIONS = [ {
    "provider" : "Duo 2 Factor",
    "configuration" : 87,
    "userIdentifier" : "UserID",
    "description" : "Duo 2 Factor Configuration",
    "details" : "Key1",
    "apiHostname" : "Key1",
    "enabled" : false,
    "integrationKey" : "Key1"
}, {
    "provider" : "Duo 2 Factor",
    "configuration" : 88,
    "userIdentifier" : "UserID",
    "description" : "Duo 2 Factor Configuration 2",
    "details" : "Key2",
    "apiHostname" : "Key2",
    "enabled" : false,
    "integrationKey" : "fdas"
}];

export const PROVIDERS = [{
        "helpLink" : "https://www.labkey.org/Documentation/20.0/wiki-page.view?name=configureCas",
        "saveLink" : "/labkey/casclient-casSaveConfiguration.view?",
        "settingsFields" : [ {
        "defaultValue" : "",
        "name" : "serverUrl",
        "caption" : "CAS Server URL",
        "description" : "Enter a valid HTTPS URL to your CAS server. The URL should start with https:// and end with /cas, for example: https://test.org/cas.",
        "type" : "input",
        "required" : true
    }, {
        "defaultValue" : false,
        "name" : "autoRedirect",
        "caption" : "Default to CAS Login",
        "description" : "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
        "type" : "checkbox",
        "required" : false
    } ],
        "description" : "Central Authentication Service (CAS)",
        "sso" : true
}];

export const PRIMARY_PROVIDERS = [{
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configureCas",
        "saveLink" : "/labkey/casclient-casSaveConfiguration.view?",
        "settingsFields" : [ {
            "defaultValue" : "",
            "name" : "serverUrl",
            "caption" : "CAS Server URL",
            "description" : "Enter a valid HTTPS URL to your CAS server. The URL should start with https:// and end with /cas, for example: https://test.org/cas.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : false,
            "name" : "autoRedirect",
            "caption" : "Default to CAS Login",
            "description" : "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
            "type" : "checkbox",
            "required" : false
        } ],
        "description" : "Central Authentication Service (CAS)",
        "sso" : true
    }, {
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configLdap",
        "saveLink" : "/labkey/ldap-ldapSaveConfiguration.view?",
        "settingsFields" : [ {
            "defaultValue" : "",
            "name" : "servers",
            "caption" : "LDAP server URLs",
            "description" : "The address(es) of your organization's LDAP server or servers. You can provide a list of multiple servers separated by semicolons. The general form for an LDAP server address is ldap://servername.domain.org",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "labkey.com",
            "name" : "domain",
            "caption" : "LDAP domain",
            "description" : "Email domain that determines which users will authenticate using this LDAP configuration. For example, if set to 'labkey.org', all users who enter xxxxxx@labkey.org will attempt authentication using this configuration. Specify '*' to attempt LDAP authentication on all email addresses entered, regardless of domain.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "${email}",
            "name" : "principalTemplate",
            "caption" : "LDAP principal template",
            "description" : "LDAP principal template that matches the DN requirements of the configured LDAP server(s). The template supports substitution syntax; include ${email} to substitute the user's full email address and ${uid} to substitute the left part of the user's email address.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : false,
            "name" : "SASL",
            "caption" : "Use SASL authentication",
            "type" : "checkbox",
            "required" : false
        }, {
            "defaultValue" : false,
            "dictateFieldVisibility" : true,
            "name" : "search",
            "caption" : "Use LDAP Search",
            "description" : "The LDAP Search option is rarely needed. It is useful when the LDAP server is configured to authenticate with a user name that is unrelated to the user's email address.",
            "type" : "checkbox",
            "required" : false
        }, {
            "defaultValue" : "",
            "name" : "username",
            "caption" : "Search DN",
            "description" : "Distinguished name of the LDAP user that will be used to search the LDAP directory.",
            "type" : "input",
            "required" : false
        }, {
            "defaultValue" : "",
            "name" : "password",
            "caption" : "Password",
            "description" : "Password for the LDAP user specified above.",
            "type" : "password",
            "required" : false
        }, {
            "defaultValue" : "",
            "name" : "searchBase",
            "caption" : "Search base",
            "description" : "Search base to use. This could be the root of your directory or the base that contains all of your user accounts.",
            "type" : "input",
            "required" : false
        }, {
            "defaultValue" : "sAMAccountName",
            "name" : "lookupField",
            "caption" : "Lookup field",
            "description" : "User record field name to use for authenticating via LDAP. The value of this field will be substituted into the principal template to generate a DN for authenticating.",
            "type" : "input",
            "required" : false
        }, {
            "defaultValue" : "(&(objectClass=user)(mail=${email}))",
            "name" : "searchTemplate",
            "caption" : "Search template",
            "description" : "Filter to apply during the LDAP search. Valid substitution patterns are ${email} and ${uid}.",
            "type" : "input",
            "required" : false
        } ],
        "description" : "Uses the LDAP protocol to authenticate against an institution's directory server",
        "testLink" : "/labkey/ldap-testLdap.view?",
        "sso" : false
    }, {
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=saml",
        "saveLink" : "/labkey/saml-samlSaveConfiguration.view?",
        "settingsFields" : [ {
            "defaultValue" : "",
            "name" : "IdPSigningCertificate",
            "caption" : "IdP Signing Certificate",
            "type" : "pem",
            "required" : true
        }, {
            "defaultValue" : "",
            "name" : "SpEncryptCert",
            "caption" : "Encryption Certificate",
            "description" : "Encryption Certificate for Encrypted Assertion",
            "type" : "pem",
            "required" : false
        }, {
            "defaultValue" : "",
            "name" : "SpPrivateKey",
            "caption" : "SP Private Key",
            "description" : "Private Key for the Encryption Certificate. Required if encryption cert is used.",
            "type" : "pem",
            "required" : false
        }, {
            "defaultValue" : "",
            "name" : "IdPSsoUrl",
            "caption" : "IdP SSO URL",
            "description" : "Target IdP URL for SSO login",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "",
            "name" : "IssuerUrl",
            "caption" : "Issuer URL",
            "description" : "Issuer of SP SAML metadata",
            "type" : "input",
            "required" : false
        }, {
            "defaultValue" : "emailAddress",
            "name" : "NameIdFormat",
            "options" : {
                "emailAddress" : "Email Address",
                "transient" : "Transient",
                "unspecified" : "Unspecified"
            },
            "caption" : "NameID Format",
            "description" : "If IdP does not support 'Email Address' then select 'Transient'",
            "type" : "options",
            "required" : false
        }, {
            "defaultValue" : false,
            "name" : "ForceAuth",
            "caption" : "Force Authorization",
            "description" : "Require user to login to IdP even if they already have an active SSO session.",
            "type" : "checkbox",
            "required" : false
        }, {
            "caption" : "EntityId",
            "html" : "The entityId for this server is the base server URL: http://localhost:8080<br>You can configure the base server URL on the <a href=\"/labkey/admin-customizeSite.view?\" target=\"_customize\">Customize Site page</a>.",
            "type" : "fixedHtml"
        }, {
            "caption" : "Assertion Consumer Service (ACS) URL",
            "html" : "This is relative to the base server URL. The ACS URL for this server is: http://localhost:8080/labkey/saml-validate.view",
            "type" : "fixedHtml"
        } ],
        "description" : "Acts as a service provider (SP) to authenticate against a SAML 2.0 Identity Provider (IdP)",
        "sso" : true
    }, {
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=authenticationModule",
        "saveLink" : "/labkey/testsso-testSsoSaveConfiguration.view?",
        "settingsFields" : [ ],
        "description" : "A trivial, insecure SSO authentication provider (for test purposes only)",
        "sso" : true
    }
];

export const SECONDARY_PROVIDERS = [{
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=configureDuoTwoFactor",
        "saveLink" : "/labkey/duo-duoSaveConfiguration.view?",
        "settingsFields" : [ {
            "defaultValue" : "",
            "name" : "integrationKey",
            "caption" : "Integration Key",
            "description" : "Your Labkey Admin with a Duo administrative account should have generated this key.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "",
            "name" : "secretKey",
            "caption" : "Secret Key",
            "description" : "Your Labkey Admin with a Duo administrative account should have generated this key.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "",
            "name" : "apiHostname",
            "caption" : "API Hostname",
            "description" : "Your Labkey Admin with a Duo administrative account should have the hostname along with Integration and Secret Key.",
            "type" : "input",
            "required" : true
        }, {
            "defaultValue" : "UserID",
            "name" : "userIdentifier",
            "options" : {
                "UserID" : "User ID",
                "UserName" : "User Name",
                "FullEmailAddress" : "Full Email Address"
            },
            "caption" : "User Identifier",
            "description" : "Choose the way LabKey will match with Duo accounts.",
            "type" : "options",
            "required" : true
        } ],
        "description" : "Require two-factor authentication via Duo"
    }, {
        "helpLink" : "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=authenticationModule",
        "saveLink" : "/labkey/testsecondary-testSecondarySaveConfiguration.view?",
        "settingsFields" : [ ],
        "description" : "Adds a trivial, insecure secondary authentication requirement (for test purposes only)"
    }
];

export const HELP_LINK = "https://www.labkey.org/Documentation/19.3/wiki-page.view?name=authenticationModule";

export const GLOBAL_SETTINGS = {
    "SelfRegistration" : true,
    "SelfServiceEmailChanges" : false,
    "AutoCreateAccounts" : true
};

export const CAS_CONFIG = {
    provider: "CAS",
    configuration: 143,
    headerLogoUrl: null,
    loginLogoUrl: null,
    serverUrl: "https://www.labkey.org/cas",
    description: "CAS Configuration1",
    details: "https://www.labkey.org/cas",
    autoRedirect: false,
    enabled: true
};

export const DUO_CONFIG = {
    provider: "Duo 2 Factor",
    configuration: 127,
    userIdentifier: "UserID",
    description: "Duo 2 Factor Configuration",
    details: "Api HostName",
    apiHostname: "Api HostName",
    enabled: false,
    integrationKey: "Key1",
};