import React, { ReactNode } from 'react';
import { Button, Col, Row } from 'react-bootstrap';

import { getErrorHeading, getImage, getInstruction, getSubHeading, getViewDetails } from './ErrorType';

import './errorHandler.scss';
import { IErrorDetailsModel } from './model';

import { ActionURL } from '@labkey/api';

export interface AppContext {
    errorDetails: IErrorDetailsModel;
}

interface ErrorHandlerProps {
    context: AppContext;
}

interface ErrorHandlerState {
    showDetails: boolean;
}

export class ErrorHandler extends React.PureComponent<ErrorHandlerProps, ErrorHandlerState> {
    constructor(props) {
        super(props);

        this.state = {
            showDetails: false,
        };
    }

    onBackClick = (): void => {
        // Back button - takes you back to the previous page if available
        // and to the ‘home’ folder if not possible to go back to the previous page
        const currentLocation = window.location.href;
        if (window.history.length !== 1) {
            // browsers like chrome stores their homepage as first item
            window.history.back();
        }

        const newLocation = window.location.href;

        if (currentLocation === newLocation) {
            window.location.href = ActionURL.getBaseURL(false);
        }
    };

    onViewDetailsClick = (): void => {
        const { showDetails } = this.state;
        this.setState(() => ({
            showDetails: !showDetails,
        }));
    };

    renderErrorTopSection = (): ReactNode => {
        const { errorDetails } = this.props.context;
        return (
            <>
                <div className="panel-body">
                    <Row>
                        <Col md={8}>
                            <div className="labkey-error-top">
                                {getErrorHeading()}
                                {getSubHeading(errorDetails)}
                                {getInstruction(errorDetails)}
                                <Button
                                    className="btn-group error-backButton"
                                    bsStyle="info"
                                    onClick={this.onBackClick}
                                >
                                    Back
                                </Button>
                                <Button className="error-details-btn" onClick={this.onViewDetailsClick}>
                                    View Details
                                </Button>
                            </div>
                        </Col>
                        <Col md={4}>{getImage(errorDetails)}</Col>
                    </Row>
                </div>
            </>
        );
    };

    renderErrorDetailsSection = (): ReactNode => {
        const { errorDetails } = this.props.context;
        return (
            <>
                <div className="error-details-container">{getViewDetails(errorDetails)}</div>
            </>
        );
    };

    render() {
        const { showDetails } = this.state;

        return (
            <>
                {this.renderErrorTopSection()}
                {showDetails && this.renderErrorDetailsSection()}
            </>
        );
    }
}
