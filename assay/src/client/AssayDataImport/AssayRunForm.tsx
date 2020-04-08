import React from 'react';
import {Col, Form, FormControl, Row} from "react-bootstrap";

import {FORM_IDS} from "./constants";

interface Props {
    onChange: (evt: any) => any
}

export class AssayRunForm extends React.PureComponent<Props, any> {
    render() {
        return (
            <Form>
                <Row>
                    <Col xs={3}>Run Name</Col>
                    <Col xs={9}>
                        <FormControl
                            id={FORM_IDS.RUN_NAME}
                            type="text"
                            placeholder={'Enter a name for this run'}
                            onChange={this.props.onChange}
                        />
                    </Col>
                </Row>
                <Row className={'margin-top'}>
                    <Col xs={3}>Comments</Col>
                    <Col xs={9}>
                        <textarea
                            className="form-control"
                            id={FORM_IDS.RUN_COMMENT}
                            placeholder={'Add a comment'}
                            onChange={this.props.onChange}
                        />
                    </Col>
                </Row>
            </Form>
        )
    }
}