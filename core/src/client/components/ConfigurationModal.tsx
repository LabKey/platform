import * as React from "react";
import {ActionURL, Ajax} from "@labkey/api";
import {Button, FormControl, Modal} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";

import CheckBoxWithText from "./CheckBoxWithText";
import {FileAttachmentForm} from "@glass/base";
import ReactBootstrapToggle from 'react-bootstrap-toggle';


interface ConfigurationModalProps {
    modalTitle: any
    description: any
    descriptionField: any
    serverUrlField: any
    redirectCheckbox: any
    logoImage: any
    toggleValue: any
}
export default class ConfigurationModal extends React.Component<any, ConfigurationModalProps> {
    constructor(props) {
        super(props);
        this.state = {
            modalTitle: `Configure ${this.props.authName} #1`,
            description: `${this.props.authName} #1 Status`,
            toggleValue: this.props.enabled,
            descriptionField: this.props.description,
            serverUrlField: this.props.serverUrlField,
            redirectCheckbox: false,
            logoImage: null
        };
        this.testFunc = this.testFunc.bind(this);
        this.onToggle = this.onToggle.bind(this);
    }

    testFunc = () => {
        // console.log("Props: ", this.props);

        console.log("dajkhfs");

        Ajax.request({
            url: ActionURL.buildURL("casclient", "SendForm"),
            method : 'GET',
            params: {configuration: "6"},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    };

    handleChange(event) {
        let {name, value} = event.target;
        this.setState(prevState => ({
            ...prevState,
            [name]: value
        }));
        // console.log(this.state[name]);
    }

    onToggle() {
        this.setState({ toggleValue: !this.state.toggleValue });
        console.log(this.state.toggleValue);
    }

    render() {
        return(
            <Modal show={true} onHide={this.testFunc}>
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
                        onstyle={"rowInfo"}
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

                    <div style={{height: "45px"}}>
                        Server URL:

                        <FormControl
                            name="serverUrlField"
                            type="text"
                            value={this.state.serverUrlField}
                            onChange={(e) => this.handleChange(e)}

                            placeholder="Enter text"
                            style ={{borderRadius: "5px", float: "right", width: "300px"}}
                        />
                    </div>

                    <br/>

                    <CheckBoxWithText
                        rowText= "Re-direct login page to CAS login page by default"
                        checked={true}
                        // onClick={() => {this.handleCheckbox(text.id)}}
                        onClick={() => {console.log("placeholder")}}
                    />

                    <hr/>

                    <strong> Logo Settings </strong><br/><br/>
                    Use logo on login page: <br/><br/>

                    <div style={{width: ""}}>
                        <FileAttachmentForm
                            showAcceptedFormats={true}
                            allowDirectories={false}
                            allowMultiple={false}
                            onFileChange={this.testFunc}
                            onFileRemoval={this.testFunc}
                            onCancel={this.testFunc}
                            previewGridProps={{
                                previewCount: 3,
                                header: 'Previewing Data for Import',
                                infoMsg: 'If the data does not look as expected, check you source file for errors and re-upload.',
                                // TODO add info about if the assay has transform scripts, this preview does not reflect that
                                onPreviewLoad: this.testFunc
                            }}
                        />
                    </div>

                    <a href={""}> Remove Current Logo </a>



                    <hr/>
                    <div style={{float: "right"}}>
                        <a href={""} style={{marginRight: "10px"}}> More about authentication </a>
                        <Button className={'labkey-button primary'} onClick={this.testFunc}>Apply</Button>
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
        )
    }
}