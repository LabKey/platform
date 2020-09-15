import React, { ReactNode } from 'react';
import { Button, Col, Row } from 'react-bootstrap';

import '@labkey/components/dist/components.css';
import './errorHandler.scss';

import { getErrorHeading, getImage, getInstruction, getSubHeading, getViewDetails } from './ErrorType';
import { IErrorDetailsModel } from './model';

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
        window.history.back();
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
