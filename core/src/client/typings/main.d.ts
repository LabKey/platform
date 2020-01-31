
type LabKey = {
    defaultHeaders: any
    devMode: boolean
    container: any
    contextPath: string
    moduleContext: any
    user: any
    vis: any
};

/* App globals */
declare const LABKEY: LabKey;

interface AuthConfig {
    provider?: string;
    description?: string;
    details?: string;
    enabled?: boolean;
    configuration?: number;
}

interface AuthConfigField {
    defaultValue?: any;
    name?: string;
    caption: string;
    description: string;
    type: string;
    required?: boolean;
    options?: Record<string, string>;
}

interface AuthConfigProvider {
    helpLink: string;
    saveLink: string;
    settingsFields: AuthConfigField[];
    description: string;
    sso?: boolean;
    testLink?: string;
}

interface InputFieldProps {
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

interface DatabasePasswordRules {
    Weak: string;
    Strong: string;
}

interface DatabasePasswordSettings {
    strength: string;
    expiration: string;
}

interface Actions {
    onDragEnd: (result: {[key:string]: any}) => void;
    onDelete: (configuration: number, configType: string) => void;
    updateAuthRowsAfterSave: (config: string, configType: string) => void;
    toggleModalOpen: (modalOpen: boolean) => void;
}
