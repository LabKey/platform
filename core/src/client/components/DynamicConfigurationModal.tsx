import * as React from "react";
import {Button, FormControl, Modal} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import FACheckBox from "./FACheckBox";

import ReactBootstrapToggle from 'react-bootstrap-toggle';

import { LabelHelpTip } from '@labkey/components';


export default class DynamicConfigurationModal extends React.PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            modalTitle: `Configure ${this.props.authName}`,
            description: `${this.props.authName} Status`,
            toggleValue: this.props.enabled,
            descriptionField: this.props.description,
            // serverUrlField: this.props.serverUrlField,
            // redirectCheckbox: false,
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
                        "name": "redirect",
                        "caption": "Default to CAS Login",
                        "description": "Redirects the login page directly to the CAS login instead of requiring the user click the CAS option.",
                        "type": "checkbox",
                        "required": false
                    }
                ]
        };
        this.onToggle = this.onToggle.bind(this);
        this.dynamicallyCreateFields = this.dynamicallyCreateFields.bind(this);
        this.handleChange = this.handleChange.bind(this);
        this.checkCheckBox = this.checkCheckBox.bind(this);

    }


    componentDidMount() {
        let fieldValues = {};
        this.state.fields.forEach( (field) => {
                fieldValues[field.name] = field.defaultValue
            }
        );

        this.setState(prevState => ({
            ...prevState,
            ...fieldValues
        })
            ,() => {console.log(this.state)} //for testing
        );
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

        console.log(name, " ", value);
    }

    checkCheckBox(name) {

        let oldState = this.state[name];
        this.setState(prevState => ({
            ...prevState,
            [name]: !oldState
        })
            // , () => this.props.checkDirty(this.state, this.props)
        );

        console.log(oldState, name);

    }

    dynamicallyCreateFields(fields) {
        return fields.map((field, index) => {
            switch (field.type) {
                case "input":
                    return <TextInput
                        key={index.toString()}
                        className={"bottom-margin"}
                        handleChange={this.handleChange}
                        value={this.state[field.name]}
                        {...field} />;
                case "checkbox":
                    return <CheckBoxInput
                        key={index.toString()}
                        className={"bottom-margin"}
                        checkCheckBox={this.checkCheckBox}
                        value={this.state[field.name]}
                        {...field} />;
            }
        })
    }

    render() {
        let fields = this.state.fields;

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
                    <strong> Settings </strong>
                    <br/><br/>

                    <div style={{height: "40px"}}>
                        Description:

                        <FormControl
                            name="descriptionField"
                            type="text"
                            value={this.state.descriptionField}
                            onChange={(e) => this.handleChange(e)}
                            placeholder="Enter text"
                            style ={{borderRadius: "5px", float: "right", width: "300px"}}
                        />
                    </div>


                    {fields && this.dynamicallyCreateFields(fields)}

                    <br/>
                    <hr/>
                    <div style={{float: "right"}}>
                        <a href={""} style={{marginRight: "10px"}}> More about authentication </a>
                        <Button className={'labkey-button primary'} onClick={()=>{}}>Apply</Button>
                    </div>

                    <Button
                        className={'labkey-button'}
                        onClick={() => this.props.closeModal()}
                        style={{marginLeft: '10px'}}
                    >
                        Cancel
                    </Button>
                    
                </Modal.Body>
            </Modal>
        );
    }
}


interface TextInputProps {
    "defaultValue": any
    "name": string
    "caption": string
    "description": string
    "required": boolean
}
class TextInput extends React.PureComponent<any, any> {
    render() {
        return(
            <div style={{height: "40px"}}>
                <span style={{marginRight:"7px"}}>
                    {this.props.caption}
                </span>

                <LabelHelpTip title={'Tip'} body={() => {
                    return (<div> {this.props.description} </div>)
                }}/>

                <FormControl
                    name={this.props.name}
                    type="text"
                    value={this.props.value}
                    onChange={(e) => this.props.handleChange(e)}

                    // placeholder="Enter text"
                    style ={{borderRadius: "5px", float: "right", width: "300px"}}
                />

                <br/>
            </div>
        );
    }
}

interface CheckBoxInputProps {
    "defaultValue": any
    "name": string
    "caption": string
    "description": string
    "required": boolean
}
class CheckBoxInput extends React.PureComponent<any, any> {


    render() {
        return(
            <div >
                <span style={{height:"40px", marginRight:"7px"}}>
                    {this.props.caption}
                </span>

                <LabelHelpTip title={'Tip'} body={() => {
                    return (<div> {this.props.description} </div>)
                }}/>

                <span style={{float:"right", marginRight: "285px", height:"40px"}}>
                    <FACheckBox
                        rowText={this.props.caption}
                        checked={this.props.value}
                        onClick={() => {this.props.checkCheckBox(this.props.name)}}
                        // onClick={(this.props.canEdit) ? (() => {this.checkGlobalAuthBox(text.id)}) : (() => {})} // empty function might be bad style, here?
                    />
                </span>
            </div>
        );
    }
}