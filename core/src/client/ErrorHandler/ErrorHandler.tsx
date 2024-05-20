import React, { PureComponent } from 'react';
import { ActionURL } from '@labkey/api';

import { getErrorHeading, getImage, getInstruction, getSubHeading, getViewDetails } from './ErrorType';
import { ErrorDetails } from './model';
import { withServerContext } from '@labkey/components';

export interface AppContext {
    errorDetails: ErrorDetails;
}

interface ErrorHandlerProps {
    context: AppContext;
}

interface ErrorHandlerState {
    showDetails: boolean;
}

export class ErrorHandlerImpl extends PureComponent<ErrorHandlerProps, ErrorHandlerState> {
    state: Readonly<ErrorHandlerState> = { showDetails: false };

    onBackClick = (): void => {
        // Back button - takes you back to the previous page if available
        // and to the ‘home’ folder if not possible to go back to the previous page
        if (window.history.length !== 1) {
            // browsers like chrome stores their homepage as first item
            window.history.back();
        } else {
            window.location.href = ActionURL.getBaseURL(false);
        }
    };

    onViewDetailsClick = (): void => {
        this.setState(state => ({ showDetails: !state.showDetails }));
    };

    onHomeClick = (): void => {
        window.location.href = ActionURL.getBaseURL(false);
    }

    render() {
        const { errorDetails } = this.props.context;
        const { showDetails } = this.state;

        const viewDetailsBtnText = showDetails ? 'Hide Details' : 'View Details';

        return (
            <>
                <div className="error-details-body">
                    <div className="row">
                        <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
                        <div className="col-lg-7 col-md-6 col-sm-12 col-xs-12">
                            <div>
                                {getErrorHeading(errorDetails)}
                                {getSubHeading(errorDetails)}
                                {getInstruction(errorDetails)}
                                <button className="btn btn-primary error-backButton error-page-button" onClick={this.onBackClick}>
                                    Back
                                </button>
                                <button className="btn btn-default error-page-button" onClick={this.onHomeClick}>
                                    Home
                                </button>
                                {!errorDetails.hideViewDetails &&
                                    <button className="btn btn-default error-page-button" onClick={this.onViewDetailsClick}>
                                        {viewDetailsBtnText}
                                    </button>
                                }
                            </div>
                        </div>
                        <div className="col-lg-3 col-md-4 hidden-xs hidden-sm">{getImage(errorDetails)}</div>
                        <div className="col-lg-1 col-md-1 hidden-xs hidden-sm" />
                    </div>
                </div>
                {showDetails && <div className="error-details-container">{getViewDetails(errorDetails)}</div>}
            </>
        );
    }
}

// export const ErrorHandler = ErrorHandlerImpl;
export const ErrorHandler = withServerContext(ErrorHandlerImpl);
