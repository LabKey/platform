import React, { ReactNode } from 'react';
import { Button, Col, Row } from 'react-bootstrap';

import '../ErrorHandler/errorHandler.scss';
import { ERROR_HEADING, ErrorType, getImage, getInstruction, getSubHeading } from '../ErrorHandler/ErrorType';

interface ErrorTopSectionProps {
    errorType: ErrorType;
}

export class ErrorTopSection extends React.PureComponent<ErrorTopSectionProps> {
    render(): ReactNode {
        const { errorType } = this.props;
        return (
            <>
                <Row className="panel-body">
                    <Col md={1} />
                    <Col md={7}>
                        <div className="labkey-error-top">
                            {ERROR_HEADING}
                            {getSubHeading(errorType)}
                            {getInstruction(errorType)}
                            <Button className="btn-group error-backButton" bsStyle="info">
                                Back
                            </Button>
                            <Button className="error-details-btn">View Details</Button>
                        </div>
                    </Col>
                    <Col md={3}>{getImage(errorType)}</Col>
                    <Col md={1} />
                </Row>
            </>
        );
    }
}
