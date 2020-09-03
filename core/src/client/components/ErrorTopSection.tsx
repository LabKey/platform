import React, { ReactNode } from 'react';
import { Button, Col, Row } from 'react-bootstrap';

import '../ErrorHandler/errorHandler.scss';
import { ErrorType, getErrorHeading, getImage, getInstruction, getSubHeading } from '../ErrorHandler/ErrorType';

interface ErrorTopSectionProps {
    errorType: ErrorType;
    loadBack: () => void;
    loadViewDetails: () => void;
}

export class ErrorTopSection extends React.PureComponent<ErrorTopSectionProps> {
    render(): ReactNode {
        const { errorType, loadBack, loadViewDetails } = this.props;
        return (
            <>
                <Row className="panel-body">
                    <Col md={1} />
                    <Col md={7}>
                        <div className="labkey-error-top">
                            {getErrorHeading()}
                            {getSubHeading(errorType)}
                            {getInstruction(errorType)}
                            <Button className="btn-group error-backButton" bsStyle="info" onClick={loadBack}>
                                Back
                            </Button>
                            <Button className="error-details-btn" onClick={loadViewDetails}>
                                View Details
                            </Button>
                        </div>
                    </Col>
                    <Col md={3}>{getImage(errorType)}</Col>
                    <Col md={1} />
                </Row>
            </>
        );
    }
}
