import React, { ReactNode } from 'react';
import { imageURL } from '@labkey/components';

const ERROR_HEADING = 'Oops! An error has occurred.';

const GENERAL_SUBHEADING = <>It seems like something went wrong. The requested page cannot be found.</>;
const GENERAL_INSTRUCTION = (
    <>
        <div className="labkey-error-instruction">
            Please contact your admin or reference the <a href="#">LabKey support forum.</a>
        </div>
        <div className="labkey-error-instruction">
            If you would like to file a <a href="#"> LabKey support ticket</a>, your unique reference code is:
        </div>
    </>
);

const PERMISSION_SUBHEADING = <>You do not have the permissions required to access this page.</>;
const PERMISSION_INSTRUCTION = <>Please contact this server's admin to gain access.</>;

const CONFIGURATION_SUBHEADING = <>It seems like something went wrong. The requested page cannot be found.</>;
const CONFIGURATION_INSTRUCTION = <>Please check your server configurations.</>;

const EXECUTION_SUB_HEADING = <>It seems like there is an issue with this installation of LabKey server.</>;
const EXECUTION_INSTRUCTION = (
    <>
        <div className="labkey-error-instruction">
            Please report this bug to <a href="#"> LabKey Support </a> by copying and pasting both unique reference code
            and code under 'view details'.
        </div>
        <div className="labkey-error-instruction">Your unique reference code is:</div>
    </>
);

export enum ErrorType {
    general = 'general',
    permission = 'permission',
    configuration = 'configuration',
    execution = 'execution',
}

export const getErrorHeading = (): ReactNode => {
    return <div className="labkey-error-heading"> {ERROR_HEADING} </div>;
};

export const getImage = (errorType: ErrorType): ReactNode => {
    let path = '';
    switch (errorType) {
        case ErrorType.general:
            path = 'general_error.svg';
            break;
        case ErrorType.permission:
            path = 'permission_error.svg';
            break;
        case ErrorType.configuration:
            path = 'configuration_error.svg';
            break;
        case ErrorType.execution:
            path = 'code_error.svg';
            break;
    }
    return <img alt="LabKey Error" src={imageURL('_images', path)} />;
};

export const getSubHeading = (errorType: ErrorType): ReactNode => {
    let subHeading;
    switch (errorType) {
        case ErrorType.general:
            subHeading = GENERAL_SUBHEADING;
            break;
        case ErrorType.permission:
            subHeading = PERMISSION_SUBHEADING;
            break;
        case ErrorType.configuration:
            subHeading = CONFIGURATION_SUBHEADING;
            break;
        case ErrorType.execution:
            subHeading = EXECUTION_SUB_HEADING;
            break;
    }
    return <div className="labkey-error-subheading">{subHeading}</div>;
};

export const getInstruction = (errorType: ErrorType): ReactNode => {
    let instruction;
    switch (errorType) {
        case ErrorType.general:
            instruction = GENERAL_INSTRUCTION;
            break;
        case ErrorType.permission:
            instruction = PERMISSION_INSTRUCTION;
            break;
        case ErrorType.configuration:
            instruction = CONFIGURATION_INSTRUCTION;
            break;
        case ErrorType.execution:
            instruction = EXECUTION_INSTRUCTION;
            break;
    }
    return <div className="labkey-error-instruction">{instruction}</div>;
};
