/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { ReactNode } from 'react';
import { HELP_LINK_REFERRER, HelpLink, imageURL, redirect } from '@labkey/components';
import { ActionURL, Ajax, getServerContext } from '@labkey/api';

import { ErrorDetails, ErrorType } from './model';

const ERROR_HEADING = () => <>Oops! An error has occurred.</>;

const DETAILS_SUB_INSTRUCTION = (
    <>
        <p className="labkey-error-details labkey-error-details-question">What else can I do?</p>
        <p className="labkey-error-details">
            Search through the{' '}
            <HelpLink topic="default" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>
                LabKey support documentation
            </HelpLink>{' '}
            to troubleshoot your issue.
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
            using the free Community Edition, you may{' '}
            <a href="https://www.labkey.org/home/Support/project-begin.view" rel="noopener noreferrer" target="_blank">
                request support here.
            </a>
        </p>
    </>
);

const NOTFOUND_HEADING = (errorMessage?: string) => (
    <>{errorMessage !== undefined ? errorMessage : 'Oops! The requested page cannot be found.'}</>
);

const NOTFOUND_SUBHEADING = (errorMessage?: string) => (
    <>{errorMessage !== undefined ? '' : 'It seems like something went wrong.'}</>
);
const NOTFOUND_INSTRUCTION = () => (
    <>
        <div className="labkey-error-instruction">
            Please contact your admin or{' '}
            <a href="https://www.labkey.org/home/Support/project-begin.view" rel="noopener noreferrer" target="_blank">
                request support here.
            </a>
        </div>
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
                    <HelpLink topic="url" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>
                        Read More &gt;
                    </HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <span className="fa fa-check-circle domain-panel-status-icon-green" /> Double check and make sure that
                your URL has been correctly input.
            </div>
            <ul>
                <li>
                    <b>Permissions: </b>your account does not have the permissions to view this page.{' '}
                    <HelpLink topic="permissionLevels" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>
                        Read More &gt;
                    </HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <span className="fa fa-check-circle domain-panel-status-icon-green" /> Contact your administrator to
                request access.
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
        {errorDetails.advice === undefined ? (
            <>
                <p className="labkey-error-details labkey-error-details-question">What is a permission error?</p>

                <p className="labkey-error-details">
                    A permission error occurs when the account you've logged into does not have the required permissions
                    to access this page.{' '}
                    <HelpLink topic="permissionLevels" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>
                        Read More &gt;
                    </HelpLink>
                </p>
                <div className="labkey-error-details labkey-error-subdetails">
                    <span className="fa fa-check-circle domain-panel-status-icon-green" /> Try contacting your server
                    administrator to request access to this page.
                </div>
            </>
        ) : (
            <p className="labkey-error-details">{errorDetails.advice}</p>
        )}
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
                <span className="fa fa-exclamation-circle permission-warning-icon" /> You are currently impersonating:{' '}
                <b>{getServerContext().user.displayName} </b>
                <div className="error-page-br" />
                <div className="error-page-br" />
                <button
                    className="btn btn-primary"
                    type="button"
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
                                redirect(returnUrl);
                            },
                        });
                    }}
                >
                    Stop Impersonating
                </button>
            </div>
        )}
    </>
);

const CONFIGURATION_HEADING = () => 'Oops! A server configuration error has occurred.';
const CONFIGURATION_SUBHEADING = (errorMessage?: string) => (
    <>
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
                    dependencies.{' '}
                    <HelpLink topic="troubleshootingAdmin" referrer={HELP_LINK_REFERRER.ERROR_PAGE}>
                        Read More &gt;
                    </HelpLink>
                </li>
            </ul>
            <div className="labkey-error-subdetails">
                <span className="fa fa-check-circle domain-panel-status-icon-green" /> Try restarting your current
                instance of LabKey.
            </div>
        </div>

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
const EXECUTION_INSTRUCTION = (errorDetails: ErrorDetails) => (
    <>
        <div className="labkey-error-instruction">
            You can{' '}
            <a href="https://www.labkey.org/home/Support/project-begin.view" rel="noopener noreferrer" target="_blank">
                {' '}
                find help resources here{' '}
            </a>{' '}
            and may find troubleshooting hints by reading the full stack trace in the server logs.
        </div>
        <div className="labkey-error-instruction">
            Your unique reference code is: <b>{errorDetails.errorCode}</b>
        </div>
    </>
);
const EXECUTION_DETAILS = () => null;

type ErrorTypeInfo = {
    details: (errorDetails?: ErrorDetails) => ReactNode;
    heading: (errorMessage?: string) => ReactNode;
    imagePath: string;
    instruction: (errorDetails?: ErrorDetails) => ReactNode;
    showDetailsBtn: boolean;
    subHeading: (errorMessage?: string) => ReactNode;
};

// FIXME: we should not be using a constant with hard coded functions to render contents. We should be using actual
//  components so that we can use hooks like useServerContext instead of getServerContext.
const ERROR_TYPE_INFO: { [key in ErrorType]: ErrorTypeInfo } = {
    configuration: {
        details: CONFIGURATION_DETAILS,
        heading: CONFIGURATION_HEADING,
        imagePath: 'configuration_error.svg',
        instruction: CONFIGURATION_INSTRUCTION,
        showDetailsBtn: true,
        subHeading: CONFIGURATION_SUBHEADING,
    },
    execution: {
        details: EXECUTION_DETAILS,
        heading: ERROR_HEADING,
        imagePath: 'code_error.svg',
        instruction: EXECUTION_INSTRUCTION,
        showDetailsBtn: false,
        subHeading: EXECUTION_SUB_HEADING,
    },
    notFound: {
        details: NOTFOUND_DETAILS,
        heading: NOTFOUND_HEADING,
        imagePath: 'notFound_error.svg',
        instruction: NOTFOUND_INSTRUCTION,
        showDetailsBtn: true,
        subHeading: NOTFOUND_SUBHEADING,
    },
    permission: {
        details: PERMISSION_DETAILS,
        heading: ERROR_HEADING,
        imagePath: 'permission_error.svg',
        instruction: PERMISSION_INSTRUCTION,
        showDetailsBtn: true,
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

export const getShowDetailsBtn = (errorDetails: ErrorDetails): boolean => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    return info?.showDetailsBtn ?? true;
};

export const getViewDetails = (errorDetails: ErrorDetails): ReactNode => {
    const info = ERROR_TYPE_INFO[errorDetails.errorType];
    if (!info) return null;

    return (
        <div className="row">
            <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
            <div className="col-lg-10 col-md-10 col-sm-12 col-xs-12">{info.details(errorDetails)}</div>
            <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
        </div>
    );
};
