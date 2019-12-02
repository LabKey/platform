import * as React from 'react'
import { Panel, Button, DropdownButton, MenuItem, Alert, Tab, Tabs, FormGroup, Form, FormControl, Modal, Row, Col } from 'react-bootstrap'
import { LabelHelpTip, DragDropHandle, FileAttachmentForm } from '@glass/base';
import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGripVertical, faPencilAlt, faCheckSquare, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';
import Immutable from 'immutable';

import "@glass/base/dist/base.css"
import "./authenticationConfiguration.scss";
import ReactBootstrapToggle from 'react-bootstrap-toggle';


// connectivity from LABKEY.js instead
// import * as Ajax from '../../../resources/scripts/labkey/Ajax.js';
import { Ajax, ActionURL, Security } from '@labkey/api'
import {currentContainer, effectivePermissions, hasPermission} from "@labkey/api/dist/labkey/Security";

// Todo:
// Find a nicer solution for the highlight
// Make pointer upon hover-over
class CheckBoxRows extends React.Component<any>{
    render(){
        return(
            <div className={"bottom-margin"}>
                {this.props.checked
                    ? <span className="noHighlight" onClick={() => this.props.checkGlobalAuthBox()}>
                        <FontAwesomeIcon size='lg' icon={faCheckSquare} color={"#0073BB"} />
                    </span>
                    : <span className="noHighlight" onClick={() => this.props.checkGlobalAuthBox()}>
                        <FontAwesomeIcon size='lg' icon={faSquare} color={"#adadad"}/>
                    </span>
                }

                <span className={"left-margin"}> {this.props.rowText} </span>
            </div>
        )
    }
}

// Todo:
// Interface
// use Immutable in handleCheckbox
// move render const into a const folder?
// bubble up form elements into app component
interface GACProps {
    // defaultEmailDomainTextField: any
    // selfSignUpCheckBox: boolean
    // userEmailEditCheckbox: boolean
    // autoCreateAuthUsersCheckbox: boolean
    preSaveConfigState: any
    currentConfigState: any
}
class GlobalAuthenticationConfigurations extends React.Component<any, GACProps>{
    constructor(props) {
        super(props);
        this.state = {

            preSaveConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            },
            currentConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            }

        };
        this.handleChange = this.handleChange.bind(this);
        this.handleCheckbox = this.handleCheckbox.bind(this);
        this.saveGlobalAuthConfigs = this.saveGlobalAuthConfigs.bind(this);
        this.getPermissions = this.getPermissions.bind(this);
    }

    handleChange(event) {
        let {value} = event.target;
        let newState = {...this.state};
        newState.currentConfigState.defaultEmailDomainTextField = value;
        this.setState(newState);

        // let oldState1 = Immutable.Map(this.state);
        // let newState1 = oldState1.setIn(["currentConfigState", "defaultEmailDomainTextField"], value);
        // this.setState(newState1.toObject());
        // console.log(newState1.toObject());

        // this.setState(({currentConfigState}) => ({
        //     currentConfigState: currentConfigState.update()
        // }));
    }

    // To Reviewer: I found this extra function was necessary in order to make TS happy with the dynamic state key;
    // open to learning a cleaner way to do it
    handleCheckbox(id: string) {
        let oldState = this.state[id];
        this.setState(prevState => ({
            ...prevState,
            [id]: !oldState
        }))
    }

    saveGlobalAuthConfigs(parameter, enabled){
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"), //generate this url
            method : 'POST',
            params: {parameter: "SelfRegistration", enabled:"true"},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    }

    getPermissions(){
        let myContainer = Security.currentContainer;
        // console.log("mycontainer: ", myContainer);
        let info;


        Security.getUserPermissions({
            success: (data) => { console.log(data)}
        });
    }

    render() {
        const rowTexts = [
            {id: "selfSignUpCheckBox", text: "Allow self sign up"},
            {id: "userEmailEditCheckbox", text: "Allow users to edit their own email addresses"},
            {id: "autoCreateAuthUsersCheckbox", text: "Auto-create authenticated users"}];

        return(
            <Panel>
                <Panel.Heading>
                    <strong>Global Authentication Configurations</strong>
                </Panel.Heading>

                <Panel.Body>
                    <strong> Sign up and email options</strong>
                    <br/><br/>

                    {rowTexts.map((text) =>
                        (<CheckBoxRows
                            key={text.id}
                            rowText={text.text}
                            checked={this.state[text.id]}
                            checkGlobalAuthBox={() => {this.handleCheckbox(text.id)}}
                        />)
                    )}

                    <div className={"form-inline globalAuthConfig-leftMargin"}>
                        Default email domain:
                        <FormControl
                            className={"globalAuthConfig-textInput globalAuthConfig-leftMargin"}
                            type="text"
                            value={this.state.currentConfigState.defaultEmailDomainTextField}
                            placeholder="Enter text"
                            onChange={(e) => this.handleChange(e)}
                            style ={{borderRadius: "5px"}}
                        />
                    </div>

                    <br/>
                    {/*<Button className={'labkey-button primary'} onClick={() => {this.getpermissions()}}>Save and Finish</Button>*/}

                </Panel.Body>
            </Panel>
        )
    }
}

interface AuthRowProps {
    descriptionField: any
    serverUrlField: any
    authType: any
    toggleValue: any
    modalOpen: any
}
// Todo:
// don't use the style to round the corners
// maybe be handleChange be a const
class AuthRow extends React.Component<any, AuthRowProps>{
    constructor(props){
        super(props);
        this.state = {
            descriptionField: "",
            serverUrlField: "",
            authType: "LDAP2",
            toggleValue: false,
            modalOpen: false
        };
        this.onToggle = this.onToggle.bind(this);
        this.handleChange = this.handleChange.bind(this);
    }

    onToggle(toggled) {
        this.setState(prevState => ({
            ...prevState,
            [toggled]: !this.state[toggled]
        }));
        // console.log(this.state[toggled]);
    }

    // see if others handle this differently
    handleChange(event) {
        let {name, value} = event.target;
        this.setState(prevState => ({
            ...prevState,
            [name]: value
        }));
        // console.log(this.state[name]);
    }

    render(){
        let {modalOpen, ...rest} = this.state;
        const HIGHLIGHT_BLUE = '#2980B9';  // See $blue-border in variables.scss
        const NOT_HIGHLIGHT_GRAY = '#999999';
        return(
            <div className="domain-field-row domain-row-border-default">
                <div className="domain-row-container row">
                    <div className="domain-row-handle">
                        <FontAwesomeIcon size='lg' color={(false) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
                        {/*<DragDropHandle highlighted={true}/>*/}
                    </div>

                    <div className="domain-row-main row-flex">
                            <Col xs={9} className='domain-row-base-fields'>
                                <Col xs={4}>
                                    <FormControl
                                        name="descriptionField"
                                        type="text"
                                        value={this.state.descriptionField}
                                        onChange={(e) => this.handleChange(e)}
                                        placeholder="Enter text"
                                        style ={{borderRadius: "5px"}}
                                    />
                                </Col>
                                <Col xs={3}>
                                    <FormControl
                                        name="serverUrlField"
                                        type="text"
                                        value={this.state.serverUrlField}
                                        onChange={(e) => this.handleChange(e)}
                                        placeholder="Enter text"
                                        style ={{borderRadius: "5px"}}
                                    />
                                </Col>
                                <Col xs={2} style={{marginTop: "5px"}}>
                                    {this.state.authType}
                                </Col>
                            </Col>

                            <Col xs={2} className='domain-row-base-fields'>
                                <ReactBootstrapToggle
                                    onClick={() => this.onToggle("toggleValue")}
                                    on="Enabled"
                                    off="Disabled"
                                    onstyle={"primary"}
                                    active={this.state.toggleValue}
                                    style={{width: "90px", height: "28px"}}
                                />
                            </Col>

                            <Col xs={1} className='domain-row-base-fields'>
                                <div onClick={() => this.onToggle("modalOpen")}>
                                    <FontAwesomeIcon size='1x' icon={faPencilAlt}/>
                                </div>
                            </Col>
                    </div>

                    {this.state.modalOpen &&  <ConfigurationModal {...rest} closeModal={() => {this.onToggle("modalOpen")}} />}
                </div>
            </div>
        )
    }
}

class AuthConfigRowDnDPanel extends React.Component<any, {items: any}>{
    constructor(props){
        super(props);
        this.state = {
            items: this.getItems(5)
        };
        this.onDragEnd = this.onDragEnd.bind(this);
        this.getItems = this.getItems.bind(this);
        this.reorder = this.reorder.bind(this);
    }

    getItems = (count) =>
        Array.from({ length: count }, (v, k) => k).map(k => ({
            id: `item-${k}`,
            content: `item ${k}`

        }));

    reorder = (list, startIndex, endIndex) => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    onDragEnd(result)
    {
        if (!result.destination)
        {
            return;
        }

        const items = this.reorder(
            this.state.items,
            result.source.index,
            result.destination.index
        );

        this.setState({
            items
        });

        console.log(this.state);
    }


    render() {
        return(
            <div>
                <DragDropContext onDragEnd={this.onDragEnd}>
                    <Droppable droppableId="auth-config-droppable">
                        {(provided) => (
                            <div {...provided.droppableProps} ref={provided.innerRef}>
                                {this.state.items.map((item, index) => (
                                    <Draggable key={item.id} draggableId={item.id} index={index}>
                                        {(provided) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.draggableProps}
                                                {...provided.dragHandleProps}
                                            >
                                                <AuthRow/>
                                            </div>
                                        )}
                                    </Draggable>
                                ))}
                                {provided.placeholder}
                            </div>
                        )}


                    </Droppable>
                </DragDropContext>
            </div>
        )
    }
}

class AuthenticationConfigurations extends React.Component<any>{
    render(){
        // dummy variables
        let authOptions = [
            {name: "CAS", description:"cas description", id:1},
            {name: "LDAP", description:"ldap description", id:2},
            {name:"SAML", description:"saml description", id:3}];

        return(
            <Panel>
                <Panel.Heading> <strong>Authentication Configurations </strong> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title="Add New">
                        {authOptions.map((authOption) => (
                            <MenuItem key={authOption.id}>
                                {/*<Link to="">*/}
                                    {authOption.name} : {authOption.description}

                                {/*</Link>*/}
                                {/*<a href={"https://stackoverflow.com/questions/19935480/bootstrap-3-how-to-make-head-of-dropdown-link-clickable-in-navbar"}>*/}
                                {/*</a>*/}
                            </MenuItem>
                        ))}
                    </DropdownButton>

                    <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                    <hr/>

                    <strong> Labkey Login Form Authentications </strong>
                    <LabelHelpTip title={'test'} body={() => {
                        return (<div> Tip 1: Ask Adam on text </div>)
                    }}/>

                    <br/><br/>

                    <Tabs defaultActiveKey={1} id="uncontrolled-tab-example">
                        <Tab eventKey={1} title="Primary">
                            <div className={"auth-tab"}>
                                <AuthConfigRowDnDPanel className={"auth-tab"}/>
                            </div>

                        </Tab>
                        <Tab eventKey={2} title="Secondary">

                            <div className={"auth-tab"}>
                                <AuthRow/>
                            </div>

                            <br/><br/><br/><br/><br/><br/><br/>

                        </Tab>
                    </Tabs>

                    <hr/>

                    <strong> Single Sign On Authentications </strong>


                    <LabelHelpTip title={'test'} body={() => {
                        return (<div> Tip 2: text </div>)
                    }}/>

                    <br/><br/>

                    {/*<div className={"auth-tab"}>*/}
                    {/*    <DragNDropMe className={"auth-tab"}/>*/}
                    {/*</div>*/}

                </Panel.Body>
            </Panel>
        )
    }
}

interface ConfigurationModalProps {
    modalTitle: any
    description: any
    descriptionField: any
    serverUrlField: any
    redirectCheckbox: any
    logoImage: any
    toggleValue: any
}
class ConfigurationModal extends React.Component<any, ConfigurationModalProps>{
    constructor(props) {
        super(props);
        this.state = {
            modalTitle: `Configure ${this.props.authType} #1`,
            description: `${this.props.authType} #1 Status`,
            toggleValue: this.props.toggleValue,
            descriptionField: this.props.descriptionField,
            serverUrlField: this.props.serverUrlField,
            redirectCheckbox: false,
            logoImage: null
        };
        this.cancelChanges = this.cancelChanges.bind(this);
        this.onToggle = this.onToggle.bind(this);
    }

    cancelChanges = () => {
        console.log("Props: ", this.props)
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
            <Modal show={true} onHide={this.cancelChanges}>
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
                            name="descriptionField"
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

                    <CheckBoxRows
                        rowText= "Re-direct login page to CAS login page by default"
                        checked={true}
                        // checkGlobalAuthBox={() => {this.handleCheckbox(text.id)}}
                    />

                    <hr/>

                    <strong> Logo Settings </strong><br/><br/>
                    Use logo on login page: <br/><br/>

                    <div style={{width: ""}}>
                        <FileAttachmentForm
                            showAcceptedFormats={true}
                            allowDirectories={false}
                            allowMultiple={false}
                            onFileChange={this.cancelChanges}
                            onFileRemoval={this.cancelChanges}
                            onCancel={this.cancelChanges}
                            previewGridProps={{
                                previewCount: 3,
                                header: 'Previewing Data for Import',
                                infoMsg: 'If the data does not look as expected, check you source file for errors and re-upload.',
                                // TODO add info about if the assay has transform scripts, this preview does not reflect that
                                onPreviewLoad: this.cancelChanges
                            }}
                        />
                    </div>

                    <a href={""}> Remove Current Logo </a>



                    <hr/>
                    <div style={{float: "right"}}>
                        <a href={""} style={{marginRight: "10px"}}> More about authentication </a>
                        <Button className={'labkey-button primary'} onClick={this.cancelChanges}>Apply</Button>
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

interface AppProps {
    // defaultEmailDomainTextField: any
    // selfSignUpCheckBox: boolean
    // userEmailEditCheckbox: boolean
    // autoCreateAuthUsersCheckbox: boolean
    preSaveConfigState: any
    currentConfigState: any
    canEdit: boolean
    value: any
}
export class App extends React.Component<any, AppProps> {

    constructor(props) {
        super(props);
        this.state = {
            // tickSelfSignup: false,
            // tickEditOwnEmail: false,
            // tickAutoCreateAuthUsers: false
            value: false,
            canEdit: false,
            preSaveConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            },
            currentConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            }

        };
        // this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);
        this.savepls = this.savepls.bind(this);
        this.getpermissions = this.getpermissions.bind(this);
    }

    cancelChanges = () => {
        console.log("to do")
    };

    checkGlobalAuthBox = () => {
        alert("uh");
    };

    savepls(){
        Ajax.request({
            url: "/labkey/login-setAuthenticationParameter.view", //generate this url
            method : 'POST',
            params: {parameter: "SelfRegistration", enabled:"true"},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    }

    getpermissions(){
        Security.getUserPermissions({
            success: (data) => {
                let canEdit = data.container.effectivePermissions.includes("org.labkey.api.security.permissions.AdminOperationsPermission");
                this.setState({canEdit: canEdit})
            }
        });
        // /labkey/login-setAuthenticationParameter.view
        let thing = ActionURL.buildURL("login", "setAuthenticationParameter")
        console.log("thingie ", thing)
    }

    render() {
        return(
            <div>
                <GlobalAuthenticationConfigurations
                    {...this.state}
                    // checkGlobalAuthBox={() => this.checkGlobalAuthBox()}
                />
                <AuthenticationConfigurations/>

                {false && <Alert>You have unsaved changes.</Alert>}

                <Button className={'labkey-button primary'} onClick={this.getpermissions}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>
            </div>
        );
    }
}
