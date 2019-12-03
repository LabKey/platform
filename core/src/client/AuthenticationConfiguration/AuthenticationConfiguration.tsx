import * as React from 'react'
import { Panel, Button, DropdownButton, MenuItem, Alert, Tab, Tabs, FormGroup, Form, FormControl, Modal, Row, Col } from 'react-bootstrap'
import { LabelHelpTip, DragDropHandle, FileAttachmentForm } from '@glass/base';
import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGripVertical, faPencilAlt, faCheckSquare, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';
import { LinkContainer } from 'react-router-bootstrap';
import Immutable from 'immutable';

import "@glass/base/dist/base.css"
import "./authenticationConfiguration.scss";
import ReactBootstrapToggle from 'react-bootstrap-toggle';

// Todo, meta:
// Finalize TS, Immutable

// connectivity from LABKEY.js instead
// import * as Ajax from '../../../resources/scripts/labkey/Ajax.js';
import { Ajax, ActionURL, Security } from '@labkey/api'
import {currentContainer, effectivePermissions, hasPermission} from "@labkey/api/dist/labkey/Security";

// Todo:
// Find a nicer solution for the highlight
// make reusable component
class CheckBoxRows extends React.Component<any>{
    render(){
        return(
            <div className={"bottom-margin"}>
                {this.props.checked
                    ? <span className="noHighlight clickable" onClick={() => this.props.checkGlobalAuthBox()}>
                        <FontAwesomeIcon size='lg' icon={faCheckSquare} color={"#0073BB"} />
                    </span>
                    : <span className="noHighlight clickable" onClick={() => this.props.checkGlobalAuthBox()}>
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
// might be a better way to do the rowTexts thing you're doing
// hook up default email domain!
interface GACProps {
    preSaveConfigState: any
    currentConfigState: any
    what: any
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
            },


            what: this.props.autoCreateAuthUsers,
        };
        this.handleChange = this.handleChange.bind(this);
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
        // let myContainer = Security.currentContainer;
        // // console.log("mycontainer: ", myContainer);
        // let info;
        //
        //
        // Security.getUserPermissions({
        //     success: (data) => { console.log(data)}
        // });
        console.log(this.props);
    }

    render() {
        const rowTexts = [
            {id: "selfSignUp", text: "Allow self sign up"},
            {id: "userEmailEdit", text: "Allow users to edit their own email addresses"},
            {id: "autoCreateAuthUsers", text: "Auto-create authenticated users"}];

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
                            checked={this.props[text.id]}
                            checkGlobalAuthBox={() => {this.props.checkGlobalAuthBox(text.id)}}
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
                    {/*<Button className={'labkey-button primary'} onClick={() => {this.getPermissions()}}>Save and Finish</Button>*/}

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
    color: any
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
            authType: "LDAP",
            toggleValue: false,
            modalOpen: false,
            color: false
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
            <div
                className="domain-field-row domain-row-border-default"
                onMouseOver={() => {this.setState({color: true})}}
                onMouseOut={() => {this.setState({color: false})}}
            >
                <div className="domain-row-container row">
                    <div className="domain-row-handle">
                        <FontAwesomeIcon size='lg' color={(this.state.color) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
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
                                <div className={"clickable"} onClick={() => this.onToggle("modalOpen")}>
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
            // items: this.uhItems()l
        };
        this.onDragEnd = this.onDragEnd.bind(this);
        this.getItems = this.getItems.bind(this);
        this.reorder = this.reorder.bind(this);
        this.ohBoy = this.ohBoy.bind(this);

    }

    getItems = (count) =>
        Array.from({ length: count }, (v, k) => k).map(k => ({
            id: `item-${k}`,
            content: `item ${k}`

        }));

    uhItems(){
        return this.props.primary;
    }

    ohBoy(){
        console.log("ohboy");
        const configs = this.props.primary;
        console.log("primary: ", configs);
        console.log("state: ", this.state)
    }

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
        // console.log("bleh: ", this.props.primary);
        // console.log("state!! ", this.state);


        this.ohBoy();

        return(
            <div>
                <DragDropContext onDragEnd={this.onDragEnd}>
                    <Droppable droppableId="auth-config-droppable">
                        {(provided) => (
                            <div ref={provided.innerRef} {...provided.droppableProps}>
                                {this.state.items.map((item, index) => (
                                    <Draggable key={item.id} draggableId={item.id} index={index} >
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

// todo:
// add new configurations is not in order
// lol fix the 'get help with auth' href
class AuthenticationConfigurations extends React.Component<any>{
    render(){
        let addNew = this.props.addNew;

        return(
            <Panel>
                <Panel.Heading> <strong>Authentication Configurations </strong> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title="Add New">
                        {this.props.addNew &&
                        Object.keys(addNew).map((authOption) => (
                            <MenuItem key={authOption} href={addNew[authOption].configLink}>
                                    {authOption} : {addNew[authOption].description}
                            </MenuItem>
                        ))}
                    </DropdownButton>

                    <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                    <hr/>

                    <strong> Labkey Login Form Authentications </strong>
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> Authentications in this group make use of LabKey's login form. During login, LabKey will attempt validation in the order that the configurations below are listed. </div>)
                    }}/>

                    <br/><br/>

                    <Tabs defaultActiveKey={1} id="uncontrolled-tab-example">
                        <Tab eventKey={1} title="Primary">
                            <div className={"auth-tab"}>
                                <AuthConfigRowDnDPanel className={"auth-tab"} primary={this.props.primary}/>
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

    // preSaveConfigState: any
    // currentConfigState: any
    canEdit: boolean
    value: any
    globalAuthConfigs: any
    addNew: any
    primary: any
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
            globalAuthConfigs: null,
            addNew: null,
            primary: null,
            // preSaveConfigState: {
            //     selfSignUpCheckBox: false,
            //     userEmailEditCheckbox: false,
            //     autoCreateAuthUsersCheckbox: false,
            //     defaultEmailDomainTextField: "",
            // },
            // currentConfigState: {
            //     selfSignUpCheckBox: false,
            //     userEmailEditCheckbox: false,
            //     autoCreateAuthUsersCheckbox: false,
            //     defaultEmailDomainTextField: "",
            // }
        };
        // this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);
        this.savepls = this.savepls.bind(this);
        this.getPermissions = this.getPermissions.bind(this);
        this.cancelChanges = this.cancelChanges.bind(this);
        this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);

    }

    componentDidMount() {
        Ajax.request({
            url: "/labkey/login-InitialMount.api", //generate this url
            method : 'GET',
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                let response = JSON.parse(result.response);
                this.setState({...response});
                console.log({...response});
            }
        })
    }

    // GAC

    // To Reviewer: I found this extra function was necessary in order to make TS happy with the dynamic state key;
    // open to learning a cleaner way to do it
    checkGlobalAuthBox(id: string) {
        let oldState = this.state.globalAuthConfigs[id];
        this.setState(prevState => ({
            ...prevState,
            globalAuthConfigs: {
                ...prevState.globalAuthConfigs,
                [id]: !oldState
            }
        }));
    }

    // End GAC

    cancelChanges() {
        console.log(this.state);
        console.log("globalAuthConfig props: ",{...this.state.globalAuthConfigs});
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
                this.state({...result});
                console.log(this.state);
            }
        })
    }

    getPermissions(){
        Security.getUserPermissions({
            success: (data) => {
                let canEdit = data.container.effectivePermissions.includes("org.labkey.api.security.permissions.AdminOperationsPermission");
                this.setState({canEdit: canEdit})
            }
        });
        // /labkey/login-setAuthenticationParameter.view
        let thing = ActionURL.buildURL("login", "setAuthenticationParameter");
        console.log("thingie ", thing)
    }

    render() {
        return(
            <div>
                <GlobalAuthenticationConfigurations
                    {...this.state.globalAuthConfigs}
                    checkGlobalAuthBox={this.checkGlobalAuthBox}
                />

                <AuthenticationConfigurations
                    addNew={this.state.addNew}
                    primary={this.state.primary}
                />

                {false && <Alert>You have unsaved changes.</Alert>}

                <Button className={'labkey-button primary'} onClick={this.getPermissions}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>
            </div>
        );
    }
}
