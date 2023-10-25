export interface AuthConfig {
    configuration?: number;
    description: string;
    details?: string;
    enabled: boolean;
    headerLogoUrl?: string;
    loginLogoUrl?: string;
    provider: string;
}

// Specifies possible attributes of one field
export interface AuthConfigField {
    caption: string;
    defaultValue?: any;
    description?: string;
    html?: string;
    name?: string;
    options?: Record<string, string>;
    required?: boolean;
    type: string;
}

export interface AuthConfigProvider {
    description: string;
    helpLink: string;
    saveLink: string;
    settingsFields: AuthConfigField[];
    sso?: boolean;
    testLink?: string;
    allowInsert?: boolean;
}

export interface GlobalSettingsOptions {
    AutoCreateAccounts?: boolean;
    DefaultDomain?: string;
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
}

export interface InputFieldProps extends AuthConfigField {
    canEdit: boolean;
    onChange?: (name: string, value: string | boolean) => void;
    value?: any;
}

export interface DatabasePasswordSettings {
    expiration: string;
    strength: string;
}

export interface Actions {
    onDelete: (configuration: number, configType: string) => void;
    onDragEnd: (result: { [key: string]: any }) => void;
    toggleModalOpen?: (modalOpen: boolean) => void;
    updateAuthRowsAfterSave: (config: string, configType: string) => void;
}
