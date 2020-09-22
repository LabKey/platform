import React, { ReactNode } from 'react';
import { Button } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheckCircle, faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { helpLinkNode, imageURL } from '@labkey/components';
import { ActionURL, Ajax, getServerContext } from '@labkey/api';

import { ErrorDetails, ErrorType } from './model';

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
            {helpLinkNode('default', 'LabKey support documentation')} is another immediate resource that is available to
            all users. A search through the documentation or previous forum questions may also help troubleshoot your
            issue.
        </div>
    </>
);

const NOTFOUND_SUBHEADING = (errorMessage?: string) => (
    <>
        {' '}
        {errorMessage !== undefined
            ? errorMessage.endsWith('.')
                ? errorMessage
                : errorMessage + '.'
            : 'It seems like something went wrong.'}{' '}
        The requested page cannot be found.
    </>
);
const NOTFOUND_INSTRUCTION = (errorCode?: string) => (
    <>
        <div className="labkey-error-instruction">
            Please contact your admin or reference the{' '}
            <a href="https://www.labkey.org/project/home/Support/begin.view?" rel="noopener noreferrer" target="_blank">
                LabKey support forum.
            </a>
        </div>
        {errorCode !== undefined && (
            <div className="labkey-error-instruction">
                If you would like to file a{' '}
                <a
                    href="https://www.labkey.org/Support%20Tickets/wiki-edit.view?"
                    rel="noopener noreferrer"
                    target="_blank"
                >
                    {' '}
                    LabKey support ticket
                </a>
                , your unique reference code is: {errorCode}
            </div>
        )}
    </>
);
const NOTFOUND_DETAILS = (errorMessage?: string) => (
    <>
        <div className="labkey-error-details labkey-error-details-question">What went wrong?</div>

        <div className="labkey-error-details">
            {errorMessage !== undefined
                ? 'Here are the most common errors:'
                : 'Unfortunately, we are unable to specifically identify what went wrong. However, here are the most common\n' +
                  '            errors:'}
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <div>
                <li>
                    <b>Incorrect URL: </b>the wrong web address has been typed. {helpLinkNode('url', 'Read More >')}
                </li>
            </div>
            <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Double check and make
            sure that your URL has been correctly input.
            <br />
            <br />
            <div>
                <li>
                    <b>Permissions: </b>your account does not have the permissions to view this page.{' '}
                    {helpLinkNode('permissionLevels', 'Read More >')}
                </li>
            </div>
            <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Contact your
            administrator to request for access.
        </div>
        <br />
        <br />

        {DETAILS_SUB_INSTRUCTION}
    </>
);

const PERMISSION_SUBHEADING = () => 'You do not have the permissions required to access this page.';
const PERMISSION_INSTRUCTION = () => 'Please contact this server\'s admin to gain access.';
const PERMISSION_DETAILS = () => (
    <>
        <div className="labkey-error-details labkey-error-details-question">What is a permission error?</div>

        <div className="labkey-error-details">
            A permission error occurs when the account you've logged into does not have the set permissions to access
            this page. {helpLinkNode('permissionLevels', 'Read More >')}
        </div>
        <div className="labkey-error-details labkey-error-subdetails">
            <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Try contacting your
            server administrator to request access to this page.
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <li>
                {' '}
                {getServerContext().user.isSignedIn
                    ? 'You are currently logged in as: ' + getServerContext().user.displayName
                    : 'You are not logged in.'}{' '}
            </li>
        </div>
        <br />
        {getServerContext().impersonatingUser !== undefined && (
            <>
                <div className="labkey-error-details labkey-error-subdetails">
                    <FontAwesomeIcon icon={faExclamationCircle} className="permission-warning-icon" /> You are currently
                    impersonating as: {getServerContext().impersonatingUser.displayName}
                    <br />
                    <br />
                    <Button
                        className="btn-group error-backButton"
                        bsStyle="info"
                        onClick={() => {
                            const returnUrl =
                                ActionURL.getParameter('returnUrl') !== undefined
                                    ? ActionURL.getParameter('returnUrl')
                                    : ActionURL.getBaseURL(false);
                            Ajax.request({
                                url: ActionURL.buildURL(
                                    'login',
                                    'StopImpersonating',
                                    getServerContext().container.path
                                ),
                                method: 'post',
                                jsonData: {
                                    returnUrl,
                                },
                                callback: () => {
                                    window.location.href = returnUrl;
                                },
                            });
                        }}
                    >
                        Stop Impersonating
                    </Button>
                </div>
            </>
        )}
    </>
);

const CONFIGURATION_SUBHEADING = (errorMessage?: string) => (
    <>
        {' '}
        {errorMessage !== undefined
            ? errorMessage.endsWith('.')
                ? errorMessage
                : errorMessage + '.'
            : 'It seems like something went wrong.'}{' '}
        The requested page cannot be found.
    </>
);
const CONFIGURATION_INSTRUCTION = () => 'Please check your server configurations.';
const CONFIGURATION_DETAILS = (errorMessage?: string) => (
    <>
        <div className="labkey-error-details labkey-error-details-question">What went wrong?</div>

        <div className="labkey-error-details">
            {errorMessage !== undefined
                ? errorMessage
                : 'Unfortunately, we are unable to specifically identify what went wrong.'}{' '}
            It seems that there might be some issues with your server configuration.
        </div>
        <br />
        <br />
        <div className="labkey-error-details labkey-error-subdetails">
            <div>
                <li>
                    <b>Server Configuration Errors (Tomcat Errors): </b>issues related to your machine, software
                    version, or running dependencies. {helpLinkNode('troubleshootingAdmin', 'Read More >')}
                </li>
            </div>
            <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Try restarting your
            current instance of LabKey.
        </div>
        <br />
        <br />

        {DETAILS_SUB_INSTRUCTION}
    </>
);

const EXECUTION_SUB_HEADING = (errorMessage?: string) => (
    <>
        {' '}
        {errorMessage !== undefined
            ? errorMessage
            : 'It seems like there is an issue with this installation of LabKey server.'}
    </>
);
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

type ErrorTypeInfo = {
    details: (errorMessage?: string) => ReactNode;
    heading: (errorMessage?: string) => ReactNode;
    imagePath: string;
    instruction: (errorCode?: string) => ReactNode;
}

const ERROR_TYPE_INFO: { [key in ErrorType]: ErrorTypeInfo } = {
    configuration: {
        details: CONFIGURATION_DETAILS,
        heading: CONFIGURATION_SUBHEADING,
        imagePath: 'configuration_error.svg',
        instruction: CONFIGURATION_INSTRUCTION,
    },
    execution: {
        details: EXECUTION_DETAILS,
        heading: EXECUTION_SUB_HEADING,
        imagePath: 'code_error.svg',
        instruction: EXECUTION_INSTRUCTION,
    },
    notFound: {
        details: NOTFOUND_DETAILS,
        heading: NOTFOUND_SUBHEADING,
        imagePath: 'notFound_error.svg',
        instruction: NOTFOUND_INSTRUCTION,
    },
    permission: {
        details: PERMISSION_DETAILS,
        heading: PERMISSION_SUBHEADING,
        imagePath: 'permission_error.svg',
        instruction: PERMISSION_INSTRUCTION,
    },
};

export const getErrorHeading = () => <div className="labkey-error-heading">{ERROR_HEADING}</div>;

export const getImage = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <img alt="LabKey Error" src={imageURL('_images', info.imagePath)} />;
};

export const getSubHeading = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <div className="labkey-error-subheading">{info.heading(errorDetails.message)}</div>;
};

export const getInstruction = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <div className="labkey-error-instruction">{info.instruction(errorDetails.errorCode)}</div>;
};

export const getViewDetails = (errorDetails: ErrorDetails): ReactNode => {
    const { errorType, message, stackTrace } = errorDetails;
    const info = ERROR_TYPE_INFO[errorType];
    if (!info) return null;

    let details;
    if (stackTrace && errorType === ErrorType.execution) {
        details = info.details(stackTrace);
    } else {
        details = info.details(message);
    }

    return <div>{details}</div>;
};
