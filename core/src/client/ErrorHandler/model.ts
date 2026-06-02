/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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
