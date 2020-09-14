import React, { ReactNode } from 'react';
import { imageURL } from '@labkey/components';

const ERROR_HEADING = 'Oops! An error has occurred.';

const NOTFOUND_SUBHEADING = <>It seems like something went wrong. The requested page cannot be found.</>;
const NOTFOUND_INSTRUCTION = (
    <>
        <div className="labkey-error-instruction">
            {/* TODO: ErrorPage - add href link*/}
            Please contact your admin or reference the <a href="#">LabKey support forum.</a>
        </div>
        <div className="labkey-error-instruction">
            {/* TODO: ErrorPage - add href link*/}
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
    notFound = 'general',
    permission = 'permission',
    configuration = 'configuration',
    execution = 'execution',
}

const ERROR_TYPE_INFO = {
    notFound: {
        heading: NOTFOUND_SUBHEADING,
        instruction: NOTFOUND_INSTRUCTION,
        imagePath: 'notFound_error.svg',
    },
    permission: {
        heading: PERMISSION_SUBHEADING,
        instruction: PERMISSION_INSTRUCTION,
        imagePath: 'permission_error.svg',
    },
    configuration: {
        heading: CONFIGURATION_SUBHEADING,
        instruction: CONFIGURATION_INSTRUCTION,
        imagePath: 'configuration_error.svg',
    },
    execution: {
        heading: EXECUTION_SUB_HEADING,
        instruction: EXECUTION_INSTRUCTION,
        imagePath: 'code_error.svg',
    },
};

export const getErrorHeading = (): ReactNode => {
    return <div className="labkey-error-heading"> {ERROR_HEADING} </div>;
};

export const getImage = (errorType: ErrorType): ReactNode => {
    if (ERROR_TYPE_INFO[errorType]) {
        const path = ERROR_TYPE_INFO[errorType].imagePath;
        return <img alt="LabKey Error" src={imageURL('_images', path)} />;
    }
};

export const getSubHeading = (errorType: ErrorType): ReactNode => {
    if (ERROR_TYPE_INFO[errorType]) {
        const subHeading = ERROR_TYPE_INFO[errorType].heading;
        return <div className="labkey-error-subheading">{subHeading}</div>;
    }
};

export const getInstruction = (errorType: ErrorType): ReactNode => {
    if (ERROR_TYPE_INFO[errorType]) {
        const instruction = ERROR_TYPE_INFO[errorType].instruction;
        return <div className="labkey-error-instruction">{instruction}</div>;
    }
};
