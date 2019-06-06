import * as React from 'react';
import {Col, Form, FormControl, Row} from "react-bootstrap";

import {FORM_IDS} from "./constants";

interface Props {
    onChange: (evt: any) => any
}

export class AssayDesignForm extends React.Component<Props, any> {
    render() {
        return (
            <Form>
                <Row>
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
                <br/>
                <Row>
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