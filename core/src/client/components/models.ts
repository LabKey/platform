export interface AuthConfig {
    enabled: boolean;
    description: string;
    provider: string;
    details?: string;
    configuration?: number;
    headerLogoUrl?: string;
    loginLogoUrl?: string;
}

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
    DisableHeaderAuthLinks?: boolean;
    DefaultDomain?: string;
}

export interface InputFieldProps extends AuthConfigField{
    canEdit: boolean;
    value?: any;
    onChange?: Function;
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
