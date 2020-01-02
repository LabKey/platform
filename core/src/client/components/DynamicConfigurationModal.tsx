import React, { PureComponent } from 'react';
import {Button, FormControl, Modal} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import FACheckBox from "./FACheckBox";

import ReactBootstrapToggle from 'react-bootstrap-toggle';

import {LabelHelpTip, FileAttachmentForm, ChangePasswordModal} from '@labkey/components';
import "@labkey/components/dist/components.css"
import {ActionURL, Ajax} from "@labkey/api";
import SSOFields from "./SSOFields";

export default class DynamicConfigurationModal extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            enabled: this.props.enabled,
            description: this.props.description,
            errorMessage: "",
            auth_header_logo: "",
            auth_login_page_logo: "",
            deletedLogos: [],
        };
    }

    componentDidMount = () => {
        let fieldValues = {};

        // dummy code to prevent secondaries from breaking modal
        if (!this.props.modalType){
            return false;
        }

        this.props.modalType.settingsFields.forEach((field) => {
                fieldValues[field.name] = (field.name in this.props ? this.props[field.name] : field.defaultValue);
            }
        );

        this.setState(() => ({
            ...fieldValues
        })
            ,() => {console.log("Props ", this.props, "\n", "State ", this.state)} //for testing
        );
    };

    saveEditedModal = () => {
        const baseUrl = ActionURL.getBaseURL(true);
        const saveUrl = baseUrl + this.props.modalType.saveLink;

        // console.log("pre-save state ", this.state);

        let form = new FormData();
        form.append("configuration", this.props.configuration);
        Object.keys(this.state).map(
            (item) => {
                form.append(item, this.state[item]);
            }
        );

        Ajax.request({
            url: saveUrl,
            method : 'POST',
            form,
            scope: this,
            failure: function(error){
                console.log("fail: ", error.response);
                const errorObj = JSON.parse(error.response);
                const errorMessage = errorObj.exception
                console.log("unfortunate state: ", errorObj.exception);
                // const errorMessage = error.response.fail
                this.setState(() => ({errorMessage}));
            },
            success: function(result){
                // console.log("success", result.response);
                this.props.updateAuthRowsAfterSave(result.response, this.props.stateSection);
                this.props.closeModal();
            }
        })
    };

    onToggle = () => {
        this.setState({ enabled: !this.state.enabled }
        // , () => console.log(this.state.enabled)
        );
    };

    handleChange = (event) => {
        const {name, value} = event.target;
        this.setState(() => ({
            [name]: value
        }));
        // console.log(name, " ", value);
    };

    handleDeleteLogo = (value) => {
        const arr = this.state.deletedLogos;
        arr.push(value);

        this.setState(() => ({
                deletedLogos: arr
            }),
            () => {console.log(this.state.deletedLogos)}
        );
    };

    checkCheckBox = (name) => {
        const oldState = this.state[name];
        this.setState(() => ({
            [name]: !oldState
        })
            // , () => this.props.checkDirty(this.state, this.props)
        );
        // console.log(oldState, name);
    };

    onFileChange = (attachment, logoType) => {
        console.log("I am changing the value of the state", logoType);
        this.setState(() => ({[logoType]: attachment.first()})
            , () => console.log("New state: ", this.state)
        );
    };

    dynamicallyCreateFields = (fields, expandableOpen) => {
        let stopPoint = fields.length;
        for (let i = 0; i < fields.length; i++) {
            if ("dictateFieldVisibility" in fields[i]) {
                stopPoint = i + 1;
                break;
            }
        }

        const fieldsToCreate = expandableOpen ? fields : fields.slice(0, stopPoint);

        return fieldsToCreate.map((field, index) => {
            switch (field.type) {
                case "input":
                    return (
                        <TextInput
                            key={index}
                            className={"bottom-margin"}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            {...field}
                        />
                    );
                case "checkbox":


                    return (
                        <CheckBoxInput
                            key={index}
                            className={"bottom-margin"}
                            checkCheckBox={this.checkCheckBox}
                            value={this.state[field.name]}
                            {...field}
                        />
                    );
                default:
                    return <div> Error: Invalid field type received. </div>;
            }
        })
    };

    render() {
        // console.log(this.props);
        let {modalType, closeModal} = this.props;
        let queryString = {"server": this.state.servers, "principal": this.state.principalTemplate, "sasl":this.state.SASL};

        return (
            <Modal show={true} onHide={() => {}} >
                <Modal.Header>
                    <Modal.Title>
                        {"Configure " + this.props.description}
                        <FontAwesomeIcon
                            size='sm'
                            icon={faTimes}
                            style={{float: "right", marginTop: "5px"}}
                            onClick={closeModal}
                        />
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <span className="boldText"> Configuration Status </span>
                    <ReactBootstrapToggle
                        onClick={this.onToggle}
                        on="Enabled"
                        off="Disabled"
                        onstyle={"primary"}
                        active={this.state.enabled}
                        style={{width: "90px", height: "28px", float: "right"}}
                    />

                    <hr/>
                    <span className="boldText"> Settings </span>
                    <br/><br/>

                    <div style={{height: "40px"}}>
                        Description:

                        <FormControl
                            name="description"
                            type="text"
                            value={this.state.description}
                            onChange={(e) => this.handleChange(e)}
                            placeholder="Enter text"
                            style ={{borderRadius: "5px", float: "right", width: "300px"}}
                        />
                    </div>


                    {modalType && this.dynamicallyCreateFields(modalType.settingsFields, this.state.search)}

                    <br/>

                    {(modalType && modalType.sso)  &&
                        <SSOFields
                            headerLogoUrl={this.props.headerLogoUrl}
                            loginLogoUrl={this.props.loginLogoUrl}
                            onFileChange={this.onFileChange}
                            handleDeleteLogo={this.handleDeleteLogo}
                        />
                    }

                    <div className="testButton">
                        {modalType.testLink &&
                            <Button className={'labkey-button'} onClick={() =>
                                window.open(
                                    ActionURL.getBaseURL(true) + modalType.testLink + ActionURL.queryString(queryString)
                                )}
                            >
                                Test
                            </Button>
                        }
                    </div>

                    <div className="editModalErrorMessage"> {this.state.errorMessage} </div>

                    <hr/>

                    <div style={{float: "right"}}>

                        <a target="_blank"
                           href={modalType.helpLink}
                           style={{marginRight: "10px"}}>
                                {"More about " + this.props.provider + " authentication"}
                        </a>

                        <Button className={'labkey-button primary'} onClick={() => this.saveEditedModal()}>Apply</Button>
                    </div>

                    <Button
                        className={'labkey-button'}
                        onClick={closeModal}
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

class TextInput extends PureComponent<any, any> {
    render() {
        const type = (this.props.name == "password") ? "password" : "text";
        return(
            <div style={{height: "40px"}}>
                <span style={{marginRight:"7px"}}>
                    {this.props.caption}
                </span>

                {this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }

                <FormControl
                    name={this.props.name}
                    type={type}
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

class CheckBoxInput extends PureComponent<any, any> {
    render() {
        return(
            <div className="dynamicFieldSpread">
                <span style={{height:"40px", marginRight:"7px"}}>
                    {this.props.caption}
                </span>

                { this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }


                <span style={{float:"right", marginRight: "285px"}}>
                    <FACheckBox
                        rowText={this.props.caption}
                        checked={this.props.value}
                        onClick={() => {this.props.checkCheckBox(this.props.name);}}
                    />
                </span>
            </div>
        );
    }
}
