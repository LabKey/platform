import React, { ReactNode } from 'react';

import { imageURL } from '@labkey/components';

import { IErrorDetailsModel } from './model';

const ERROR_HEADING = 'Oops! An error has occurred.';

const DETAILS_SUB_INSTRUCTION = (
    <>
        <div className="labkey-error-details labkey-error-details-question">What else can I do?</div>
        <div className="labkey-error-details">
            If you are subscribed to LabKey, then reach out to your account manager for immediate help. If you are using
            the community edition, your support ticket will be resolved within the next 2 to 3 weeks.
        </div>
        <br />
        <div className="labkey-error-details">
            <a href="#">LabKey support documentation </a> is another immediate resource that is available to all users.
            A search through the documentation or previous forum questions may also help troubleshoot your issue.
        </div>
    </>
);

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
const NOTFOUND_DETAILS = (
    <>
        <div className="labkey-error-details labkey-error-details-question">What went wrong?</div>

        <div className="labkey-error-details">
            Unfortunately, we are unable to specifically identify what went wrong. However, here are the most common
            errors:
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <div>
                <li>
                    <b>Incorrect URL: </b>the wrong web address has been typed.
                </li>
            </div>
            <div>Double check and make sure that your URL has been correctly input.</div>

            <br />
            <br />

            <div>
                <li>
                    <b>Permissions: </b>your account does not have the permissions to view this page.
                </li>
            </div>
            <div>Contact your administrator to request for access.</div>
        </div>
        <br />
        <br />

        {DETAILS_SUB_INSTRUCTION}
    </>
);

const PERMISSION_SUBHEADING = <>You do not have the permissions required to access this page.</>;
const PERMISSION_INSTRUCTION = <>Please contact this server's admin to gain access.</>;
const PERMISSION_DETAILS = (
    <>
        <div className="labkey-error-details labkey-error-details-question">What is a permission error?</div>

        <div className="labkey-error-details">
            A permission error occurs when the account you've logged into does not have the set permissions to access
            this page.
            <a href="#"> Read More</a>
        </div>
        <div className="labkey-error-details labkey-error-subdetails">
            Try contacting your administrator to request access to this page.
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <li>You are currently logged in as: </li>
            <li>This server's admin: </li>
        </div>
    </>
);

const CONFIGURATION_SUBHEADING = <>It seems like something went wrong. The requested page cannot be found.</>;
const CONFIGURATION_INSTRUCTION = <>Please check your server configurations.</>;
const CONFIGURATION_DETAILS = (
    <>
        <div className="labkey-error-details labkey-error-details-question">What went wrong?</div>

        <div className="labkey-error-details">
            Unfortunately, we are unable to specifically identify what went wrong. It seems that there might be some
            issues with your server configuration.
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <div>
                <li>
                    <b>Server Configuration Errors (Tomcat Errors): </b>issues related to your machine, software
                    version, or running dependencies.{' '}
                </li>
            </div>
            <div>Try restarting your current instance of LabKey.</div>
        </div>
        <br />
        <br />

        {DETAILS_SUB_INSTRUCTION}
    </>
);

const EXECUTION_SUB_HEADING = <>It seems like there is an issue with this installation of LabKey server.</>;
const EXECUTION_INSTRUCTION = (errorCode?: string) => (
    <>
        <div className="labkey-error-instruction">
            Please report this bug to <a href="#"> LabKey Support </a> by copying and pasting both unique reference code
            and code under 'view details'.
        </div>
        <div className="labkey-error-instruction">Your unique reference code is: {errorCode}</div>
    </>
);
const EXECUTION_DETAILS = (stackTrace?: string) => <div className="labkey-error-stacktrace">{stackTrace}</div>;

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
        details: NOTFOUND_DETAILS,
    },
    permission: {
        heading: PERMISSION_SUBHEADING,
        instruction: PERMISSION_INSTRUCTION,
        imagePath: 'permission_error.svg',
        details: PERMISSION_DETAILS,
    },
    configuration: {
        heading: CONFIGURATION_SUBHEADING,
        instruction: CONFIGURATION_INSTRUCTION,
        imagePath: 'configuration_error.svg',
        details: CONFIGURATION_DETAILS,
    },
    execution: {
        heading: EXECUTION_SUB_HEADING,
        instruction: (errorCode?: string) => EXECUTION_INSTRUCTION(errorCode),
        imagePath: 'code_error.svg',
        details: (stackTrace?: string) => EXECUTION_DETAILS(stackTrace),
    },
};

export const getErrorHeading = (): ReactNode => {
    return <div className="labkey-error-heading"> {ERROR_HEADING} </div>;
};

export const getImage = (errorDetails: IErrorDetailsModel): ReactNode => {
    if (ERROR_TYPE_INFO[errorDetails.errorType]) {
        const path = ERROR_TYPE_INFO[errorDetails.errorType].imagePath;
        return <img alt="LabKey Error" src={imageURL('_images', path)} />;
    }
};

export const getSubHeading = (errorDetails: IErrorDetailsModel): ReactNode => {
    if (ERROR_TYPE_INFO[errorDetails.errorType]) {
        const subHeading = ERROR_TYPE_INFO[errorDetails.errorType].heading;
        return <div className="labkey-error-subheading">{subHeading}</div>;
    }
};

export const getInstruction = (errorDetails: IErrorDetailsModel): ReactNode => {
    if (ERROR_TYPE_INFO[errorDetails.errorType]) {
        const errorType = errorDetails.errorType;
        let instruction;
        if (errorDetails.errorCode) {
            instruction = ERROR_TYPE_INFO[errorType].instruction(errorDetails.errorCode);
        } else {
            instruction = ERROR_TYPE_INFO[errorType].instruction;
        }
        return <div className="labkey-error-instruction">{instruction}</div>;
    }
};

export const getViewDetails = (errorDetails: IErrorDetailsModel): ReactNode => {
    if (ERROR_TYPE_INFO[errorDetails.errorType]) {
        const errorType = errorDetails.errorType;
        let details;
        if (errorDetails.stackTrace && errorType == ErrorType.execution) {
            details = ERROR_TYPE_INFO[errorType].details(errorDetails.stackTrace);
        } else {
            details = ERROR_TYPE_INFO[errorType].details;
        }
        return <div>{details}</div>;
    }
};
