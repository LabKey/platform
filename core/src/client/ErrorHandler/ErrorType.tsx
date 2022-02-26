import React, { ReactNode } from 'react';
import { Button } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheckCircle, faExclamationCircle } from '@fortawesome/free-solid-svg-icons';
import { imageURL, HelpLink, HELP_LINK_REFERRER } from '@labkey/components';
import { ActionURL, Ajax, getServerContext } from '@labkey/api';

import { ErrorDetails, ErrorType } from './model';

const ERROR_HEADING = () => <>Oops! An error has occurred.</>

const DETAILS_SUB_INSTRUCTION = (
    <>
        <p className="labkey-error-details labkey-error-details-question">What else can I do?</p>
        <p className="labkey-error-details">
            Search through the <HelpLink topic="default" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>LabKey support documentation</HelpLink> and
            previous forum questions to troubleshoot your issue.
        </p>
        <p className="labkey-error-details">
            If you are part of a{' '}
            <a
                href="https://labkey.com/products-services/labkey-server/#server-editions"
                rel="noopener noreferrer"
                target="_blank"
            >
                {' '}
                LabKey Server Premium Edition
            </a>{' '}
            subscription, please use your support portal or contact your account manager for assistance. If you are
            using the free Community Edition, you may find help by posting on the{' '}
            <a href="https://www.labkey.org/project/home/Support/begin.view?" rel="noopener noreferrer" target="_blank">
                LabKey support forum.
            </a>
        </p>
    </>
);

const NOTFOUND_HEADING = (errorMessage?: string) => (<>
        {errorMessage !== undefined ?
            errorMessage : 'Oops! The requested page cannot be found.'}
    </>);

const NOTFOUND_SUBHEADING = (errorMessage?: string) => (
    <>
        {errorMessage !== undefined
            ? ''
            : 'It seems like something went wrong.'}
    </>
);
const NOTFOUND_INSTRUCTION = (errorDetails: ErrorDetails) => (
    <>
        <div className="labkey-error-instruction">
            Please contact your admin or reference the{' '}
            <a href="https://www.labkey.org/project/home/Support/begin.view?" rel="noopener noreferrer" target="_blank">
                LabKey support forum.
            </a>
        </div>
        {errorDetails.errorCode !== undefined && errorDetails.errorCode !== null && (
            <div className="labkey-error-instruction">
                If you would like to file a{' '}
                <a
                    href="https://www.labkey.org/project/home/Support/begin.view?"
                    rel="noopener noreferrer"
                    target="_blank"
                >
                    {' '}
                    LabKey support ticket
                </a>
                , your unique reference code is: {errorDetails.errorCode}
            </div>
        )}
    </>
);
const NOTFOUND_DETAILS = (errorDetails: ErrorDetails) => (
    <>
        <div className="labkey-error-details labkey-error-details-question">What went wrong?</div>
        <div className="error-page-br" />

        <p className="labkey-error-details">
            {errorDetails.message !== undefined
                ? 'Here are the most common errors:'
                : 'Unfortunately, we are unable to specifically identify what went wrong. However, here are the most common\n' +
                  '            errors:'}
        </p>
        <div className="error-page-br" />
        <div className="labkey-error-details">
            <ul>
                <li>
                    <b>Incorrect URL: </b>the wrong web address has been typed.{' '}
                    <HelpLink topic="url" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>Read More &gt;</HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Double check and
                make sure that your URL has been correctly input.
            </div>
            <ul>
                <li>
                    <b>Permissions: </b>your account does not have the permissions to view this page.{' '}
                    <HelpLink topic="permissionLevels" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>Read More &gt;</HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Contact your
                administrator to request access.
            </div>
        </div>

        {DETAILS_SUB_INSTRUCTION}
    </>
);

const PERMISSION_SUBHEADING = (errorMessage: string) => (
    <>{errorMessage !== undefined ? errorMessage : 'You do not have the permissions required to access this page.'}</>
);

const PERMISSION_INSTRUCTION = (errorDetails: ErrorDetails) => <>{errorDetails.advice} </>;

const PERMISSION_DETAILS = (errorDetails: ErrorDetails) => (
    <>
        {errorDetails.advice === undefined ?
            <>
                <p className="labkey-error-details labkey-error-details-question">What is a permission error?</p>

                <p className="labkey-error-details">
                    A permission error occurs when the account you've logged into does not have the required permissions to
                    access
                    this page. <HelpLink topic="permissionLevels" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>Read
                    More &gt;</HelpLink>
                </p>
                <div className="labkey-error-details labkey-error-subdetails">
                <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Try contacting your
                server administrator to request access to this page.
                </div>
            </> : <p className="labkey-error-details">{errorDetails.advice}</p>
        }
        <div className="labkey-error-details">
            <ul>
                <li>
                    {' '}
                    {getServerContext().user.isSignedIn
                        ? 'You are currently logged in as: ' +
                          (getServerContext().impersonatingUser !== undefined
                              ? getServerContext().impersonatingUser.displayName
                              : getServerContext().user.displayName)
                        : 'You are not logged in.'}
                </li>
            </ul>
        </div>
        {getServerContext().impersonatingUser !== undefined && (
            <div className="labkey-error labkey-error-details labkey-error-subdetails">
                <FontAwesomeIcon icon={faExclamationCircle} className="permission-warning-icon" /> You are currently
                impersonating: <b>{getServerContext().user.displayName} </b>
                <div className="error-page-br" />
                <div className="error-page-br" />
                <Button
                    className="btn btn-primary"
                    onClick={() => {
                        const returnUrl =
                            ActionURL.getReturnUrl() !== undefined
                                ? ActionURL.getReturnUrl()
                                : ActionURL.getBaseURL(false);
                        Ajax.request({
                            url: ActionURL.buildURL('login', 'StopImpersonating', getServerContext().container.path),
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
        )}
    </>
);

const CONFIGURATION_HEADING = () => 'Oops! A server configuration error has occurred.';
const CONFIGURATION_SUBHEADING = (errorMessage?: string) => (
    <>
        {'The requested page cannot be found. '}
        {errorMessage !== undefined
            ? errorMessage.endsWith('.')
                ? errorMessage
                : errorMessage + '.'
            : 'It seems like something went wrong.'}
    </>
);
const CONFIGURATION_INSTRUCTION = () => 'Please check your server configurations.';
const CONFIGURATION_DETAILS = (errorDetails: ErrorDetails) => (
    <>
        <p className="labkey-error-details labkey-error-details-question">What went wrong?</p>

        <p className="labkey-error-details">
            {errorDetails.message !== undefined
                ? errorDetails.message.endsWith('.')
                    ? errorDetails.message
                    : errorDetails.message + '.'
                : 'Unfortunately, we are unable to specifically identify what went wrong.'}{' '}
            It seems that there might be some issues with your server configuration.
        </p>
        <div className="error-page-br" />
        <div className="error-page-br" />
        <div className="labkey-error-details">
            <ul>
                <li>
                    <b>Server Configuration Errors: </b>issues related to your machine, software version, or running
                    dependencies. <HelpLink topic="troubleshootingAdmin" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>Read More &gt;</HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <FontAwesomeIcon icon={faCheckCircle} className="domain-panel-status-icon-green" /> Try restarting your
                current instance of LabKey.
            </div>
        </div>

        {DETAILS_SUB_INSTRUCTION}
        <pre>{errorDetails.stackTrace}</pre>
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
const EXECUTION_INSTRUCTION = (errorDetails: ErrorDetails) => (
    <>
        <div className="labkey-error-instruction">
            Please report this bug to{' '}
            <a href="https://www.labkey.org/project/home/Support/begin.view?" rel="noopener noreferrer" target="_blank">
                {' '}
                LabKey Support{' '}
            </a>{' '}
            by copying and pasting both your unique reference code and the full stack trace in the View Details section
            below.
        </div>
        <div className="labkey-error-instruction">
            Your unique reference code is: <b>{errorDetails.errorCode}</b>
        </div>
    </>
);
const EXECUTION_DETAILS = (errorDetails: ErrorDetails) => <pre>{errorDetails.stackTrace}</pre>;

type ErrorTypeInfo = {
    details: (errorDetails?: ErrorDetails) => ReactNode;
    heading: (errorMessage?: string) => ReactNode;
    subHeading: (errorMessage?: string) => ReactNode;
    imagePath: string;
    instruction: (errorDetails?: ErrorDetails) => ReactNode;
};

const ERROR_TYPE_INFO: { [key in ErrorType]: ErrorTypeInfo } = {
    configuration: {
        details: CONFIGURATION_DETAILS,
        heading: CONFIGURATION_HEADING,
        imagePath: 'configuration_error.svg',
        instruction: CONFIGURATION_INSTRUCTION,
        subHeading: CONFIGURATION_SUBHEADING,
    },
    execution: {
        details: EXECUTION_DETAILS,
        heading: ERROR_HEADING,
        imagePath: 'code_error.svg',
        instruction: EXECUTION_INSTRUCTION,
        subHeading: EXECUTION_SUB_HEADING,
    },
    notFound: {
        details: NOTFOUND_DETAILS,
        heading: NOTFOUND_HEADING,
        imagePath: 'notFound_error.svg',
        instruction: NOTFOUND_INSTRUCTION,
        subHeading: NOTFOUND_SUBHEADING,
    },
    permission: {
        details: PERMISSION_DETAILS,
        heading: ERROR_HEADING,
        imagePath: 'permission_error.svg',
        instruction: PERMISSION_INSTRUCTION,
        subHeading: PERMISSION_SUBHEADING,
    },
};

export const getErrorHeading = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <div className="labkey-error-heading">{info.heading(errorDetails.message)}</div>;
};

export const getImage = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <img alt="LabKey Error" src={imageURL('_images', info.imagePath)} />;
};

export const getSubHeading = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <div className="labkey-error-subheading">{info.subHeading(errorDetails.message)}</div>;
};

export const getInstruction = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return <div className="labkey-error-instruction">{info.instruction(errorDetails)}</div>;
};

export const getViewDetails = (errorDetails: ErrorDetails): ReactNode => {
    const { errorType, message, stackTrace } = errorDetails;
    const info = ERROR_TYPE_INFO[errorType];
    if (!info) return null;

    return (
        <div className="row">
            <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
            <div className="col-lg-10 col-md-10 col-sm-12 col-xs-12">{info.details(errorDetails)}</div>
            <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
        </div>
    );
};
