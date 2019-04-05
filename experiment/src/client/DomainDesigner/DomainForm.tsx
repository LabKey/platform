import * as React from "react";
import {Col, Form, FormControl, Label, Panel, Row} from "react-bootstrap";
import {DomainRow} from "./DomainRow";
import {DomainDesign} from "./models";
import {clearFieldDetails, updateField} from "./actions";
import {Formik} from "formik";

const panelTopRowStyle = {
    margin: '0 0 5px 10px'
};

const panelSearchStyle = {
    margin: '0 0 25px 5px'
};

const paddingZero = {
    padding: 0
}

const paddingRightZero = {
    padding: '0 0 0 20px'
};

const styleRequired = {
    paddingLeft: '12%'
}

interface IDomainFormInput {
    domainDesign: DomainDesign,
    id: string
}

export default class DomainForm extends React.Component<IDomainFormInput, DomainDesign> {

    constructor(props: IDomainFormInput) {
        super(props);

        this.onChangeHandler = this.onChangeHandler.bind(this);
        this.clearDetails = this.clearDetails.bind(this);
    }

    clearDetails() {
        this.setState(clearFieldDetails(this.props.domainDesign));
    }

    onChangeHandler(evt) {
        let value = evt.target.value;
        if (evt.target.type === "checkbox") {
            value = evt.target.checked;
        }
        this.setState(updateField(this.props.domainDesign, evt.target.id, value));
    }

    render() {
        const { domainDesign, id } = this.props;

        return (
            <Panel>
                <Panel.Heading>
                    <div className={"panel-title"}>{"Field Properties - " + domainDesign.name}</div>
                </Panel.Heading>
                <Panel.Body>
                    <Row style={panelTopRowStyle}>
                        <p>Adjust fields and their properties that will be shown within this domain. Click a row to
                            access additional options. Drag and drop rows to re-order them.</p>
                    </Row>
                    <Row style={panelSearchStyle}>
                        <Col xs={3}>
                            <FormControl id={"dom-search-" + name} type="text" placeholder={'Filter Fields'}/>
                        </Col>
                        <Col xs={1} />
                        <Col xs={8} md={6} lg={4}>
                            <Col xs={5} style={paddingZero}>
                                <span>Show Fields Defined By: </span>
                            </Col>
                            <Col xs={7} style={paddingZero}>
                                <FormControl id={"dom-user-" + name} type="text" placeholder={'User'}/>
                            </Col>
                        </Col>
                    </Row>
                    <Row style={panelTopRowStyle}>
                        <Col xs={3}>
                            <b>Field Name</b>
                        </Col>
                        <Col xs={2}>
                            <b>Date Type</b>
                        </Col>
                        <Col xs={1}>
                            <b>Required?</b>
                        </Col>
                        <Col xs={6}>
                            <b>Details</b>
                        </Col>
                    </Row>
                    <Form id={id} ref={id}>
                        {domainDesign.fields.map((field) => {
                            return <DomainRow
                                key={'domain-row-key-' + field.propertyId}
                                onChange = { this.onChangeHandler }
                                field = { field }
                             />
                        })}
                    </Form>
                </Panel.Body>
            </Panel>

        );
    }
}