import * as React from 'react'
import { Panel, Button, DropdownButton, MenuItem, Alert, Tab, Tabs, FormGroup, Form, FormControl, Modal, Row, Col } from 'react-bootstrap'
import { LabelHelpTip, DragDropHandle, FileAttachmentForm } from '@glass/base';
import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGripVertical, faPencilAlt, faCheckSquare, faTimes } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';
import { LinkContainer } from 'react-router-bootstrap';
import {Map, List, fromJS} from 'immutable';

import "@glass/base/dist/base.css"
import "./authenticationConfiguration.scss";
import ReactBootstrapToggle from 'react-bootstrap-toggle';

// Todo, meta:
// Finalize TS, Immutable

// connectivity from LABKEY.js instead
// import * as Ajax from '../../../resources/scripts/labkey/Ajax.js';
import { Ajax, ActionURL, Security } from '@labkey/api'
import {currentContainer, effectivePermissions, hasPermission} from "@labkey/api/dist/labkey/Security";

// ----------------
// move components into proper place

class LightupHandle extends React.Component<any, {highlight: boolean}>{
    constructor(props){
        super(props);
        this.state = {
            highlight: false
        };
    }

    render(){
        const HIGHLIGHT_BLUE = '#2980B9';  // See $blue-border in variables.scss
        const NOT_HIGHLIGHT_GRAY = '#999999';

        return(
            <div>
                <FontAwesomeIcon size='lg' color={(this.props.highlight) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
            </div>
        )
    }
}

// todo: remove the margin-left
class GenericAuthRow extends React.Component<any, {color: any}>{
    constructor(props){
        super(props);
        this.state = {
            color: false
        };
    }

    render(){
        return(
            <div
                className="domain-field-row domain-row-border-default"
                onMouseOver={() => {this.setState({color: true})}}
                onMouseOut={() => {this.setState({color: false})}}
            >
                <div className="domain-row-container row">
                    <div className="domain-row-handle">
                        {this.props.handle}
                    </div>

                    <div className="domain-row-main row-flex">

                        <Col xs={9} className='domain-row-base-fields'>
                            <Col xs={4}>
                                {this.props.description}
                            </Col>
                            <Col xs={4}>
                                {this.props.url}
                            </Col>
                            <Col xs={1} className={this.props.nameClassName}>
                                {this.props.name}
                            </Col>
                        </Col>

                        <Col xs={1}/>

                        <Col xs={2} className={"domain-row-base-fields"}>
                            <Col xs={8}>
                                {this.props.enabled}
                            </Col>

                            <Col xs={4} >
                                {this.props.edit}
                            </Col>
                        </Col>

                        {this.props.modal}
                    </div>
                </div>
            </div>
    )}
}

// -----------------

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
    highlight: any
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
            authType: "",
            toggleValue: false,
            modalOpen: false,
            highlight: false
        };
        this.onToggle = this.onToggle.bind(this);
        this.handleChange = this.handleChange.bind(this);
    }

    onToggle(toggled) {
        this.setState(prevState => ({
            ...prevState,
            [toggled]: !this.state[toggled]
        }));
    }

    // see if others handle this differently
    handleChange(event) {
        let {name, value} = event.target;
        this.setState(prevState => ({
            ...prevState,
            [name]: value
        }));
    }

    render(){
        // is this bad style?
        const description = <FormControl
            id={this.props.id}
            name="description"
            type="text"
            value={this.props.description}
            onChange={(e) => this.props.handleChangeToPrimary(e)}
            placeholder="Enter text"
            style ={{borderRadius: "5px"}}
        />;

        const url = <FormControl
            name="url"
            type="text"
            value={this.props.url}
            onChange={(e) => this.props.handleChangeToPrimary(e)}
            placeholder="Enter text"
            style ={{borderRadius: "5px"}}
        />;

        const enabled = <ReactBootstrapToggle
            onClick={() => this.props.handlePrimaryToggle(this.props.enabled, this.props.id)}
            on="Enabled"
            off="Disabled"
            onstyle={"primary"}
            active={this.props.enabled}
            style={{width: "90px", height: "28px"}}
        />;

        const edit = <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onToggle("modalOpen")}>
            <FontAwesomeIcon size='1x' icon={faPencilAlt}/>
        </div>;

        let {modalOpen, ...rest} = this.props;
        const modal = (this.state.modalOpen &&  <ConfigurationModal {...rest} closeModal={() => {this.onToggle("modalOpen")}} />);

        return(
            <div
                onMouseOver={() => {this.setState({highlight: true})}}
                onMouseOut={() => {this.setState({highlight: false})}}
            >
                <GenericAuthRow
                    handle = {<LightupHandle highlight={this.state.highlight}/>}
                    description = {description}
                    url = {url}
                    name = {this.props.authName}
                    nameClassName = {"down"}
                    enabled = {enabled}
                    edit = {edit}
                    modal = {modal}
                />
            </div>
        )
    }
}

class AuthConfigRowDnDPanel extends React.Component<any, {items: any}>{
    constructor(props){
        super(props);
        this.state = {
            // items: this.getItems(5)
            items: this.uhItems()
        };
        this.onDragEnd = this.onDragEnd.bind(this);
        this.reorder = this.reorder.bind(this);
        this.ohBoy = this.ohBoy.bind(this);
        this.uhItems = this.uhItems.bind(this);
    }

    uhItems(){
        let bigArr = [
            {id:"1", name: "CAS", description: "CAS Configuration"},
            {id:"2", name: "LDAP", description: "LDAP Configuration"}
        ];

        return bigArr;
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
    }


    render() {
        // console.log("bleh: ", this.props.primary);
        // console.log("state!! ", this.state);

        // this.ohBoy();

        const primaryConfigsWithoutDatabase = this.props.primary.slice(0, -1);
        const dataBaseConfig = this.props.primary.slice(-1)[0];
        // console.log("UH ", dataBaseConfig);

        return(
            <div>
                <DragDropContext onDragEnd={this.props.onDragEnd}>
                    <Droppable droppableId="auth-config-droppable">
                        {(provided) => (
                            <div ref={provided.innerRef} {...provided.droppableProps}>
                                {primaryConfigsWithoutDatabase.map((item, index) => (
                                    <Draggable key={item.id} draggableId={item.id} index={index} >
                                        {(provided) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.draggableProps}
                                                {...provided.dragHandleProps}
                                            >
                                                <AuthRow
                                                    id={index.toString()}
                                                    authName={item.name}
                                                    enabled={item.enabled}
                                                    description={item.description}
                                                    handleChangeToPrimary={this.props.handleChangeToPrimary}
                                                    handlePrimaryToggle={this.props.handlePrimaryToggle}
                                                />
                                            </div>
                                        )}
                                    </Draggable>
                                ))}
                                {provided.placeholder}
                            </div>
                        )}
                    </Droppable>
                </DragDropContext>

                {dataBaseConfig && <GenericAuthRow
                    name={dataBaseConfig.name}
                    enabled={(dataBaseConfig.enabled) ? "Enabled" : "Disabled"}
                    description={dataBaseConfig.description}
                />}
            </div>
        )
    }
}

// todo:
// add new configurations is not in order
// lol fix the 'get help with auth' href
// put in loading wheel
// where is your hr?
class AuthenticationConfigurations extends React.Component<any>{
    render(){
        let addNew = this.props.addNew;
        let primary = this.props.primary;

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
                                {primary &&
                                <AuthConfigRowDnDPanel
                                        className={"auth-tab"}
                                        primary={primary}
                                        onDragEnd={this.props.onDragEnd}
                                        handleChangeToPrimary={this.props.handleChangeToPrimary}
                                        handlePrimaryToggle={this.props.handlePrimaryToggle}
                                />}
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
            toggleValue: this.props.enabled,
            descriptionField: this.props.description,
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

interface AppState {
    // defaultEmailDomainTextField: any
    // selfSignUpCheckBox: boolean
    // userEmailEditCheckbox: boolean
    // autoCreateAuthUsersCheckbox: boolean

    // preSaveConfigState: any
    // currentConfigState: any
    canEdit: boolean
    globalAuthConfigs: Object
    addNew: Object
    primary: Array<Object>
}
export class App extends React.Component<any, AppState> {

    constructor(props) {
        super(props);
        this.state = {
            // tickSelfSignup: false,
            // tickEditOwnEmail: false,
            // tickAutoCreateAuthUsers: false
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

        this.onDragEnd = this.onDragEnd.bind(this);
        this.reorder = this.reorder.bind(this);

        this.handleChangeToPrimary = this.handleChangeToPrimary.bind(this);
        this.handlePrimaryToggle = this.handlePrimaryToggle.bind(this);

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

    // todo: use immutable
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

    // DnD Panel
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
            this.state.primary,
            result.source.index,
            result.destination.index
        );

        this.setState({
            primary: items
        });
    }

    // rough
    handleChangeToPrimary(event) {
        let {name, value, id} = event.target;
        const l = List(this.state.primary);
        // console.log(l.toArray());
        const l2 = l.setIn([id, name], value);

        const thing = l2.toArray();
        // console.log(thing);

        this.setState(prevState => ({
            ...prevState,
            primary: thing
        }))
    }


    // rough
    handlePrimaryToggle(toggle, id){
        const l = List(this.state.primary);
        const l2 = l.setIn([id, 'enabled'], !toggle);
        const thing = l2.toArray();
        this.setState(prevState => ({
            ...prevState,
            primary: thing
        }))
    }

    handleChangeToPrimary1(event){
        const zero = "0";
        let {name, value, id} = event.target;
        console.log("bing!");

        const map = List(this.state.primary);
        console.log("oldstate: ", map.toObject());

        const listUpdated = map.setIn([id, name], value);
        console.log("newstate1: ", listUpdated.toArray());
        // final = listUpdated.toObject() as AppState;

        // this.setState(() => ({primary: listUpdated}));
        // console.log("newsatte? ", this.state);


        // const map = Immutable.Map(this.state);
        // const map2 = map.setIn(['primary', id, name], value);

        // console.log("newstate: ", map2.toObject());
        // this.setState({primary: [{}, {}]});
    }

    // End

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
            <div style={{minWidth:"1040px"}}>
                <GlobalAuthenticationConfigurations
                    {...this.state.globalAuthConfigs}
                    checkGlobalAuthBox={this.checkGlobalAuthBox}
                />

                <AuthenticationConfigurations
                    addNew={this.state.addNew}
                    primary={this.state.primary}
                    onDragEnd={this.onDragEnd}
                    handleChangeToPrimary={this.handleChangeToPrimary}
                    handlePrimaryToggle={this.handlePrimaryToggle}
                />

                {false && <Alert>You have unsaved changes.</Alert>}

                <Button className={'labkey-button primary'} onClick={this.getPermissions}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>
            </div>
        );
    }
}
