import React, { PureComponent } from 'react';
import {Button, ButtonGroup, FormControl, Modal} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import FACheckBox from "./FACheckBox";

import ReactBootstrapToggle from 'react-bootstrap-toggle';

import {LabelHelpTip, FileAttachmentForm} from '@labkey/components';
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
            emptyRequiredFields: null,
        };
    }

    componentDidMount = () => {
        let fieldValues = {};

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

    saveEditedModal = () : void => {
        const baseUrl = ActionURL.getBaseURL(true);
        let saveUrl = baseUrl + this.props.modalType.saveLink;
        let form = new FormData();

        if (this.areRequiredFieldsEmpty()) {
            return;
        }

        if (this.props.configuration) {
            form.append("configuration", this.props.configuration);
        }
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
                const errorMessage = errorObj.exception;
                console.log("unfortunate state: ", errorObj.exception);
                // const errorMessage = error.response.fail
                this.setState(() => ({errorMessage}));
            },
            success: function(result){
                console.log("saveEditedModal Success", result);
                this.props.updateAuthRowsAfterSave(result.response, this.props.stateSection);
                this.props.closeModal();
            }
        })
    };

    areRequiredFieldsEmpty = () => {
        const requiredFields = this.props.modalType.settingsFields.reduce((accum, current) => {
            if (current.required) {
                accum.push(current.name)
            }
            return accum;
        }, ["description"]);

        const emptyRequiredFields = requiredFields.filter((name) =>
            this.state[name] == ""
        );

        if (emptyRequiredFields.length > 0){
            this.setState({emptyRequiredFields}, () => (console.log("dfiuas", this.state)));
            return true;
        } else {
            return false;
        }
    };

    onToggle = () => {
        this.setState({enabled: !this.state.enabled});
    };

    handleChange = (event) : void => {
        const {name, value} = event.target;
        this.setState(() => ({
            [name]: value
        }));
    };

    handleDeleteLogo = (value: string) => {
        const arr = this.state.deletedLogos;
        arr.push(value);

        this.setState(() => ({deletedLogos: arr}));
    };

    checkCheckBox = (name: string) => {
        const oldState = this.state[name];
        this.setState(() => ({
            [name]: !oldState
        }));
    };

    onFileChange = (attachment, logoType: string) => {
        this.setState(() => ({[logoType]: attachment.first()}));
    };



    dynamicallyCreateFields = (fields: Array<AuthConfigField>, expandableOpen) => {
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
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            type={"text"}
                            canEdit={this.props.canEdit}
                            emptyRequiredFields={this.state.emptyRequiredFields}
                            {...field}
                        />
                    );
                case "checkbox":
                    return (
                        <CheckBoxInput
                            key={index}
                            checkCheckBox={this.checkCheckBox}
                            value={this.state[field.name]}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );
                case "password":
                    if (!this.props.canEdit){
                        return;
                    }
                    return (
                        <TextInput
                            key={index}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            type={"password"}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );

                case "textarea":
                    return (
                        <SmallFileUpload
                            key={index}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );

                case "options":
                    return(
                        <Option
                            key={index}
                            handleChange={this.handleChange}
                            value={this.state[field.name]}
                            options={field.options}
                            canEdit={this.props.canEdit}
                            {...field}
                        />
                    );

                case "fixedHtml":
                    return(
                        <FixedHtml
                            key={index}
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
        let {modalType, closeModal, canEdit} = this.props;
        let queryString = {"server": this.state.servers, "principal": this.state.principalTemplate, "sasl":this.state.SASL};
        let isAddNewConfig = (this.props.title);
        let modalTitle = (isAddNewConfig) ? this.props.title : this.props.description;
        let finalizeButtonText = (isAddNewConfig) ? "Finish" : "Apply";

        return (
            <Modal show={true} onHide={() => {}} >
                <Modal.Header>
                    <Modal.Title>
                        {"Configure " + modalTitle}
                        <FontAwesomeIcon
                            size='sm'
                            icon={faTimes}
                            className="modal__close-icon"
                            onClick={closeModal}
                        />
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <div className="modal__top">
                        <span className="bold-text"> Configuration Status </span>
                        <ReactBootstrapToggle
                            onClick={this.onToggle}
                            on="Enabled"
                            off="Disabled"
                            onstyle={"primary"}
                            active={this.state.enabled}
                            className="modal__enable-toggle"
                            disabled={!canEdit}
                        />
                    </div>

                    <div className="bold-text modal__settings-text"> Settings </div>

                    <div className="modal__text-input">
                        Description *

                        {(this.state.emptyRequiredFields && this.state.emptyRequiredFields.includes("description")) &&
                            <div className={"modal__tiny-error"}> This field is required </div>
                        }


                        {canEdit ?
                            <FormControl
                                name="description"
                                type="text"
                                value={this.state.description}
                                onChange={(e) => this.handleChange(e)}
                                placeholder="Enter text"
                                style ={{borderRadius: "5px", float: "right", width: "300px"}}
                            />
                            : <span style ={{borderRadius: "5px", float: "right", width: "300px"}}> {this.state.description} </span>
                        }
                    </div>


                    {modalType && this.dynamicallyCreateFields(modalType.settingsFields, this.state.search)}

                    {(modalType && modalType.sso)  &&
                        <SSOFields
                            headerLogoUrl={this.props.headerLogoUrl}
                            loginLogoUrl={this.props.loginLogoUrl}
                            onFileChange={this.onFileChange}
                            handleDeleteLogo={this.handleDeleteLogo}
                            canEdit={canEdit}
                        />
                    }

                    <div className="modal__test-button">
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

                    <div className="modal__error-message"> {this.state.errorMessage} </div>

                    <div className="modal__bottom">
                        <div className="modal__bottom-buttons">
    
                            <a target="_blank"
                               href={modalType.helpLink}
                               className="modal__help-link"
                            >
                                    {"More about " + this.props.provider + " authentication"}
                            </a>
    
                            {canEdit
                                ? <Button className={'labkey-button primary'} onClick={() => this.saveEditedModal()}> {finalizeButtonText} </Button>
                                : null
                            }
                        </div>
    
                        <Button
                            className={'labkey-button modal__save-button'}
                            onClick={closeModal}
                        >
                            {canEdit ? "Cancel" : "Close"}
                        </Button>
                    </div>

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
        const fieldIsRequiredAndEmpty = (this.props.emptyRequiredFields && this.props.emptyRequiredFields.includes(this.props.name));
        console.log(this.props.emptyRequiredFields);

        return(
            <div className="modal__text-input">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? "*" : null}


                </span>

                {this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }

                {(fieldIsRequiredAndEmpty) &&
                    <div className={"modal__tiny-error"}> This field is required </div>
                }

                {this.props.canEdit ?

                        <FormControl
                            name={this.props.name}
                            type={this.props.type}
                            value={this.props.value}
                            onChange={(e) => this.props.handleChange(e)}
                            style ={{borderRadius: "5px", float: "right", width: "300px"}}
                            // className="modal__text-input"
                        />
                    : <span style ={{borderRadius: "5px", float: "right", width: "300px"}}> {this.props.value} </span>
                }
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
            <div className="modal__field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? "*" : null}
                </span>

                { this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }

                <span className="modal__input">
                    {this.props.canEdit ?
                        <FACheckBox
                            name={this.props.name}
                            checked={this.props.value}
                            canEdit={true}
                            onClick={() => {this.props.checkCheckBox(this.props.name);}}
                        />
                    :
                        <FACheckBox
                            name={this.props.name}
                            checked={this.props.value}
                            canEdit={false}
                        />
                    }
                </span>
            </div>
        );
    }
}

// todo: add 'canEdit' option
class TextArea extends PureComponent<any, any> {
    render() {
        return(
            <div className="modal__textarea-field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? "*" : null}
                </span>

                { this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }

                {this.props.canEdit ?
                    <div className="modal__textarea-input">
                        <FormControl
                            id={this.props.name}
                            name={this.props.name}
                            componentClass="textarea"
                            onChange={(e) => this.props.handleChange(e)}
                            value={this.props.value}
                            className="modal__textarea"
                        />
                    </div>
                    :
                    <div className="modal__textarea-input">
                        {this.props.value}
                    </div>
                }

            </div>
        );
    }
}

class Option extends PureComponent<any, any> {
    render() {
        const {options} = this.props;
        return(
            <div className="modal__option-field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? "*" : null}
                </span>

                { this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }


                    { this.props.canEdit ?
                        <div className="modal__option-input">
                            <FormControl
                                componentClass="select"
                                name={this.props.name}
                                onChange={this.props.handleChange}
                                value={this.props.value}
                            >
                                {options && Object.keys(options).map((item) => (
                                    <option value={item} key={item} > {options[item]} </option>
                                ))}
                            </FormControl>
                        </div>
                        : <span style ={{float: "right", width: "300px"}}> {this.props.value} </span>
                    }
            </div>
        );
    }
}

class TextAreaOrFileUpload extends PureComponent<any, any> { //todo: you'll probably want unique names on these
    constructor(props) {
        super(props);
        this.state = {
            whichField: "copyPaste"
        };
    }

    handleChange = (event) => {
        const {name, value} = event.target;
        console.log(name, value);
        this.setState({[name]: value});

        // this.setState((prevState) => ({
        //     ...prevState,
        //     currentSettings: {
        //         ...prevState.currentSettings,
        //         [name]: value
        //     }
        // }));
    };

    render() {
        const canEdit = true;

        const caption = "Caption";
        const required = false;
        const description = "description";

        const name = "something";
        const value = "bruh";

        return(
            <div>
                <span className="modal__field-label">
                    {caption} {required ? "*" : null}
                </span>

                { description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {description} </div>)
                    }}/>
                }

                <span style={{float: "right", marginRight:"145px"}}>
                    <ButtonGroup onClick={this.handleChange} bsSize="xsmall">
                        <Button data-key='1' value="copyPaste" name="whichField" active={this.state.whichField == "copyPaste"} disabled={!canEdit}>
                            Copy/Paste
                        </Button>
                        <Button data-key='2' value="fileUpload" name="whichField" active={this.state.whichField == "fileUpload"} disabled={!canEdit}>
                            File Upload
                        </Button>
                    </ButtonGroup>
                </span>

                <br/><br/>

                {(this.state.whichField == "copyPaste") ?
                    <FormControl
                        id={name}
                        componentClass="textarea"
                        placeholder="textarea"
                        value={value}
                        style={{borderRadius: "5px", width:"270px", height:"85px", marginLeft:"270px"}}
                    />
                :
                    <div style={{width: "270px", height:"70px", marginLeft:"270px"}}>
                        <FileAttachmentForm
                            key={this.props.text}
                            showLabel={false}
                            allowMultiple={false}
                            allowDirectories={false}
                            acceptedFormats={".jpeg,.png,.gif,.tif"}
                            showAcceptedFormats={false}
                            onFileChange={(attachment) => {
                                this.props.onFileChange(attachment, this.props.fileTitle)
                            }}
                        />
                    </div>
                }
                <br/> <br/>
            </div>
        );
    }
}

class FixedHtml extends PureComponent<any, any> {
    render() {
        return (
            <div className="modal__fixed-html-field">
                <span className="modal__field-label">
                    {this.props.caption}
                </span>

                <div className="modal__fixed-html-text">
                    <div dangerouslySetInnerHTML={{ __html: this.props.html }} />
                </div>
            </div>
        );
    }
}

class SmallFileUpload extends PureComponent<any, any> {



    render() {
        return (
            <div className="modal__compact-file-upload-field">
                <span className="modal__field-label">
                    {this.props.caption} {this.props.required ? "*" : null}
                </span>

                { this.props.description &&
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {this.props.description} </div>)
                    }}/>
                }

                <div className="modal__compact-file-upload-input">
                    <input
                        type="file"
                        className="file-upload--input" // Hides file input
                        name={this.props.name}
                        id={`${this.props.name}-fileUpload`}
                        multiple={false}
                        onChange={() => {}}
                    />

                    {/* We render a label here so click and drag events propagate to the input above */}
                    <label
                        className={"file-upload--compact-label modal__file-upload--compact"}
                        htmlFor={""}
                        onDrop={() => {}}
                        onDragEnter={() => {}}
                        onDragOver={() => {}}
                        onDragLeave={() => {}}
                    >
                        <i className="fa fa-cloud-upload" aria-hidden="true"/>
                        &nbsp;
                        <span>Select file or drag and drop here</span>
                        <span className="file-upload--error-message">{null}</span>
                    </label>
                </div>

            </div>
        );
    }
}