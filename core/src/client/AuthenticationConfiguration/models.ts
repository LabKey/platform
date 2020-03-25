export interface AuthConfig {
    enabled: boolean;
    description: string;
    provider: string;
    details?: string;
    configuration?: number;
    headerLogoUrl?: string;
    loginLogoUrl?: string;
}

export interface SSOFields {
    auth_header_logo: string,
    auth_login_page_logo: string,
    deletedLogos: String[],
    changedFiles: String[],
    serverUrl: string,
    autoRedirect: boolean,
}

export interface CASFields extends SSOFields {
    provider: string;
    enabled: boolean,
    description: string,
}

export interface LDAPFields {
    provider: string;
    enabled: boolean,
    description: string,
    servers: string,
    domain: string,
    principalTemplate: string,
    SASL: boolean,
    search: boolean,
    username: string,
    password: string,
    searchBase: string,
    lookupField: string,
    searchTemplate: string,
}

export interface SAMLFields extends SSOFields{
    provider: string;
    enabled: boolean,
    description: string,
    IdPSigningCertificate: string,
    SpEncryptCert: string,
    SpPrivateKey: string,
    IdPSsoUrl: string,
    IssuerUrl: string,
    NameIdFormat: string,
    ForceAuth: boolean,
}

export interface DuoFields {
    provider: string;
    enabled: boolean,
    description: boolean,
    integrationKey: string,
    secretKey: string,
    apiHostname: string,
    userIdentifier: string
}

// Specifies all possible well-formed auth config structures. Used when auth config handled is dynamic
export type AuthConfigFields = CASFields & LDAPFields & SAMLFields & DuoFields;

// Specifies possible attributes of one field
export interface AuthConfigField {
    defaultValue?: any;
    name?: string;
    caption: string;
    description?: string;
    type: string;
    required?: boolean;
    options?: Record<string, string>;
    html?: string;
}

export interface AuthConfigProvider {
    helpLink: string;
    saveLink: string;
    settingsFields: AuthConfigField[];
    description: string;
    sso?: boolean;
    testLink?: string;
}

export interface GlobalSettingsOptions {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean;
}

export interface InputFieldProps extends AuthConfigField{
    canEdit: boolean;
    value?: string;
    onChange?: Function;
    key?: number;
}

export interface DatabasePasswordRules {
    Weak: string;
    Strong: string;
}

export interface DatabasePasswordSettings {
    strength: string;
    expiration: string;
}

export interface Actions {
    onDragEnd: (result: {[key:string]: any}) => void;
    onDelete: (configuration: number, configType: string) => void;
    updateAuthRowsAfterSave: (config: string, configType: string) => void;
    toggleModalOpen?: (modalOpen: boolean) => void;
}
