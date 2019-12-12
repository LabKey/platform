import * as React from "react";
import {Button, FormControl, Modal} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import CheckBoxWithText from "./CheckBoxWithText";
import {FileAttachmentForm} from "@labkey/components";

import ReactBootstrapToggle from 'react-bootstrap-toggle';


export default class DynamicConfigurationModal extends React.PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            modalTitle: `Configure ${this.props.authName}`,
            description: `${this.props.authName} Status`,
            toggleValue: this.props.enabled,
            descriptionField: this.props.description,
            serverUrlField: this.props.serverUrlField,
            redirectCheckbox: false,
            fields:
                [
                    {
                        "defaultValue": "",
                        "name": "serverUrl",
                        "caption": "CAS Server URL",
                        "description": "Enter a valid HTTPS URL to your CAS server. The URL should start with https:// and end with /cas, for example: https://test.org/cas.",
                        "type": "input",
                        "required": true
                    },
                    {
                        "defaultValue": true,
                        "name": "serverUrl",
                        "caption": "Default to CAS Login",
                        "description": "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
                        "type": "checkbox",
                        "required": false
                    }
                ]
        };
        this.onToggle = this.onToggle.bind(this);

    }

    onToggle() {
        this.setState({ toggleValue: !this.state.toggleValue });
        console.log(this.state.toggleValue);
    }

    handleChange(event) {
        let {name, value} = event.target;
        this.setState(prevState => ({
            ...prevState,
            [name]: value
        }));
        // console.log(this.state[name]);
    }

    render() {
        return (
            <Modal show={true} onHide={() => {}}>
                <Modal.Header>
                    <Modal.Title>
                        {this.state.modalTitle}
                        <FontAwesomeIcon
                            size='sm'
                            icon={faTimes}
                            style={{float: "right", marginTop: "5px"}}
                            onClick={() => this.props.closeModal()}
                        />
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <strong> {this.state.description} </strong>
                    <ReactBootstrapToggle
                        onClick={this.onToggle}
                        on="Enabled"
                        off="Disabled"
                        onstyle={"primary"}
                        active={this.state.toggleValue}
                        style={{width: "90px", height: "28px", float: "right"}}
                    />

                    <hr/>
                    <strong>General Settings </strong>
                    <br/><br/>

                    <div style={{height: "45px"}}>
                        Description:

                        <FormControl
                            name="description"
                            type="text"
                            value={this.state.descriptionField}
                            onChange={(e) => this.handleChange(e)}
                            placeholder="Enter text"
                            style ={{borderRadius: "5px", float: "right", width: "300px"}}
                        />
                    </div>



                </Modal.Body>
            </Modal>
        );
    }

}