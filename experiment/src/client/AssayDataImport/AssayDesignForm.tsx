import * as React from 'react';
import {Col, Form, FormControl, Row} from "react-bootstrap";

import {FORM_IDS} from "./constants";

interface Props {
    onChange: (evt: any) => any
}

export class AssayDesignForm extends React.Component<Props, any> {
    render() {
        return (
            <Form className={'assay-data-import-text'}>
                <div>
                    Define basic properties for this new design. These and other advanced settings can always be
                    modified later on the assay runs list by choosing "Manage Assay Design".
                </div>
                <div className={'margin-top'}>
                    By default, this assay design will include the column headers detected from your uploaded file.
                </div>
                <Row className={'margin-top'}>
                    <Col xs={3}>Assay Name *</Col>
                    <Col xs={9}>
                        <FormControl
                            id={FORM_IDS.ASSAY_NAME}
                            type="text"
                            placeholder={'Enter a name for this assay'}
                            onChange={this.props.onChange}
                        />
                    </Col>
                </Row>
                <Row className={'margin-top'}>
                    <Col xs={3}>Description</Col>
                    <Col xs={9}>
                        <textarea
                            className="form-control"
                            id={FORM_IDS.ASSAY_DESCRIPTION}
                            placeholder={'Add a description'}
                            onChange={this.props.onChange}
                        />
                    </Col>
                </Row>
            </Form>
        )
    }
}