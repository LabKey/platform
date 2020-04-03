
export interface AuthConfig {
    provider?: string;
    description?: string;
    details?: string;
    enabled?: boolean;
    configuration?: number;
}

export interface AuthConfigField {
    defaultValue?: any;
    name?: string;
    caption: string;
    description: string;
    type: string;
    required?: boolean;
    options?: Record<string, string>;
}

export interface AuthConfigProvider {
    helpLink: string;
    saveLink: string;
    settingsFields: AuthConfigField[];
    description: string;
    sso?: boolean;
    testLink?: string;
}

export interface InputFieldProps {
    defaultValue?: any;
    name?: string;
    caption: string;
    description?: string;
    required?: boolean;
    canEdit: boolean;
    type: string;
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
    toggleModalOpen: (modalOpen: boolean) => void;
}