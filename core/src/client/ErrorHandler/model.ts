export enum ErrorType {
    notFound = 'notFound',
    permission = 'permission',
    configuration = 'configuration',
    execution = 'execution',
}

export interface ErrorDetails {
    errorCode?: string;
    errorType: ErrorType;
    message?: string;
    hideViewDetails?: boolean;
    advice?: string;
}
