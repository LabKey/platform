import { ErrorType } from './ErrorType';

export interface IErrorDetailsModel {
    message?: string;
    errorCode?: string;
    stackTrace?: string;
    errorType: ErrorType;
}

export class ErrorDetailsModel implements IErrorDetailsModel {
    message?: string;
    errorCode?: string;
    stackTrace?: string;
    errorType: ErrorType;

    constructor(errorDetailsModel: IErrorDetailsModel) {
        Object.assign(this, errorDetailsModel);
    }
}
