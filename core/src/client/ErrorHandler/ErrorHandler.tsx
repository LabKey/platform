import React, { PureComponent } from 'react';
import { ActionURL } from '@labkey/api';

import { getErrorHeading, getImage, getInstruction, getSubHeading, getViewDetails } from './ErrorType';
import { ErrorDetails } from './model';

import './errorHandler.scss';

export interface AppContext {
    errorDetails: ErrorDetails;
}

interface ErrorHandlerProps {
    context: AppContext;
}

interface ErrorHandlerState {
    showDetails: boolean;
    viewDetailsBtnText: string;
}

export class ErrorHandler extends PureComponent<ErrorHandlerProps, ErrorHandlerState> {
    private _viewDetails = 'View Details';
    private _hideDetails = 'Hide Details';

    state: Readonly<ErrorHandlerState> = { showDetails: false, viewDetailsBtnText: this._viewDetails };

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
        const { showDetails } = this.state;
        const viewDetailsBtnText = !showDetails ? this._hideDetails : this._viewDetails;

        this.setState(state => ({ showDetails: !state.showDetails, viewDetailsBtnText }));
    };

    render() {
        const { errorDetails } = this.props.context;
        const { showDetails, viewDetailsBtnText } = this.state;

        return (
            <>
                <div className="error-details-body">
                    <div className="row">
                        <div className="col-md-1" />
                        <div className="col-md-7">
                            <div className="labkey-error-top">
                                {getErrorHeading()}
                                {getSubHeading(errorDetails)}
                                {getInstruction(errorDetails)}
                                <button className="btn btn-primary error-backButton" onClick={this.onBackClick}>
                                    Back
                                </button>
                                <button className="btn btn-default error-details-btn" onClick={this.onViewDetailsClick}>
                                    {viewDetailsBtnText}
                                </button>
                            </div>
                        </div>
                        <div className="col-md-3 hidden-xs hidden-sm">{getImage(errorDetails)}</div>
                        <div className="col-md-1" />
                    </div>
                </div>
                {showDetails && <div className="error-details-container">{getViewDetails(errorDetails)}</div>}
            </>
        );
    }
}
