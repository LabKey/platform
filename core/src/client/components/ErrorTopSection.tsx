import React, { ReactNode } from 'react';
import { Button, Col, Row } from 'react-bootstrap';

import '../ErrorHandler/errorHandler.scss';
import { ErrorType, getErrorHeading, getImage, getInstruction, getSubHeading } from '../ErrorHandler/ErrorType';

interface ErrorTopSectionProps {
    errorType: ErrorType;
    onBackClick: () => void;
    onViewDetailsClick: () => void;
}

export class ErrorTopSection extends React.PureComponent<ErrorTopSectionProps> {
    render(): ReactNode {
        const { errorType, onBackClick, onViewDetailsClick } = this.props;
        return (
            <>
                <div className="panel-body">
                    <Row>
                        <Col md={8}>
                            <div className="labkey-error-top">
                                {getErrorHeading()}
                                {getSubHeading(errorType)}
                                {getInstruction(errorType)}
                                <Button className="btn-group error-backButton" bsStyle="info" onClick={onBackClick}>
                                    Back
                                </Button>
                                <Button className="error-details-btn" onClick={onViewDetailsClick}>
                                    View Details
                                </Button>
                            </div>
                        </Col>
                        <Col md={4}>{getImage(errorType)}</Col>
                    </Row>
                </div>
            </>
        );
    }
}
