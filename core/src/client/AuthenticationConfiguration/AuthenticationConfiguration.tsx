import * as React from 'react'
import { Panel, Button, DropdownButton, MenuItem, Alert, Checkbox, Tab, Tabs, FormGroup, Form, FormControl, Modal, Row, Col } from 'react-bootstrap'
import { LabelHelpTip } from '@glass/base';
import { DragDropContext, Draggable, Droppable } from "react-beautiful-dnd";
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPlusSquare, faMinusSquare, faGripVertical, faPencilAlt, faCheckSquare } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';


import { DomainForm } from "@glass/domainproperties";
import {render} from "react-dom";
import "./authenticationConfiguration.css";
import ToggleButton from 'react-toggle-button';

type State = {
}

export const HIGHLIGHT_BLUE = '#2980B9';  // See $blue-border in variables.scss
export const NOT_HIGHLIGHT_GRAY = '#999999';
const grid = 8;
const getItems = count =>
    Array.from({ length: count }, (v, k) => k).map(k => ({
        id: `item-${k}`,
        content: `item ${k}`
    }));
const reorder = (list, startIndex, endIndex) => {
    const result = Array.from(list);
    const [removed] = result.splice(startIndex, 1);
    result.splice(endIndex, 0, removed);

    return result;
};
const getItemStyle = (isDragging, draggableStyle) => ({
    // some basic styles to make the items look a bit nicer
    userSelect: "none",
    padding: grid * 2,
    margin: `0 0 ${grid}px 0`,

    // change background colour if dragging
    background: isDragging ? "lightgreen" : "grey",

    // styles we need to apply on draggables
    ...draggableStyle
});
const getListStyle = isDraggingOver => ({
    background: isDraggingOver ? "lightblue" : "lightgrey",
    width: 250
});

class DragNDropMe extends React.Component<any, {items: any}>{
    constructor(props){
        super(props);
        this.state = {
          items: getItems(10)
        };
        this.onDragEnd = this.onDragEnd.bind(this);
    }



    onDragEnd(result)
    {
        // dropped outside the list
        if (!result.destination)
        {
            return;
        }

        const items = reorder(
            this.state.items,
            result.source.index,
            result.destination.index
        );

        this.setState({
            items
        });
    }


    render() {
        let initialData = {
            column: {
                id: 'column-1',
                numberIds: ['four', 'one', 'five', 'three', 'two'],
            },
            numbers: {
                'five': { id: 'five', content: '5' },
                'four': { id: 'four', content: '4' },
                'one': { id: 'one', content: '1' },
                'three': { id: 'three', content: '3' },
                'two': { id: 'two', content: '2' },
            }
        };

        const numbers = initialData.column.numberIds.map((numberId: string) => initialData.numbers[numberId]);


        return(
            <div>
                asdf\\

                <DragDropContext onDragEnd={this.onDragEnd}>
                    <Droppable droppableId="droppable">
                        {(provided, snapshot) => (
                            <div
                                {...provided.droppableProps}
                                ref={provided.innerRef}
                                style={getListStyle(snapshot.isDraggingOver)}
                            >
                                {this.state.items.map((item, index) => (
                                    <Draggable key={item.id} draggableId={item.id} index={index}>
                                        {(provided, snapshot) => (
                                            <div
                                                ref={provided.innerRef}
                                                {...provided.draggableProps}
                                                {...provided.dragHandleProps}
                                                style={getItemStyle(
                                                    snapshot.isDragging,
                                                    provided.draggableProps.style
                                                )}
                                            >
                                                {item.content}
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

class AuthRow extends React.Component<any, {value: any}>{
    constructor(props){
        super(props);
        this.state = {value: false };
    }



    render(){

        const borderRadiusStyle = { borderRadius: 2, height: "28px"};
        return(
            <div className="domain-field-row domain-row-border-default">
                <div className="domain-row-container row">
                    <div className="domain-row-handle">
                        <FontAwesomeIcon size='lg' color={(false) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
                    </div>

                    <div className="domain-row-main">
                            <Col xs={9} className='domain-row-base-fields'>
                                <Col xs={4}>
                                    <FormControl
                                        type="text"
                                        value=""
                                        placeholder="Enter text"
                                    />
                                </Col>
                                <Col xs={3}>
                                    <FormControl
                                        type="text"
                                        value=""
                                        placeholder="Enter text"
                                    />
                                </Col>
                                <Col xs={2}>
                                    LDAP
                                </Col>
                            </Col>



                            <Col xs={2} className='domain-row-base-fields'>
                                <ToggleButton
                                    value={ false }

                                    containerStyle={{display:'inline-block',width:'100px'}}
                                    trackStyle={{width:'100px', borderRadius: 2, height: "30px"}}
                                    thumbStyle={borderRadiusStyle}
                                    activeLabelStyle={{ width:'50px' }}
                                    inactiveLabelStyle={{ width:'50px' }}
                                    thumbAnimateRange={[1, 80]}

                                    inactiveLabel={"Disabled"}
                                    activeLabel={"Enabled"}

                                    onToggle={(value) => {
                                        console.log("hey")
                                    }} />
                            </Col>

                            <Col xs={1} className='domain-row-base-fields'>
                                <FontAwesomeIcon size='lg' icon={faPencilAlt}/>
                            </Col>
                    </div>
                </div>
            </div>
        )
    }




}

class CheckBoxRows extends React.Component<any>{
    render(){
        return(
            <div className={"bottom-margin"}>
                {this.props.checked
                    ? <span onClick={() => this.props.checkGlobalAuthBox()}> <FontAwesomeIcon size='lg' icon={faCheckSquare} color={"#0073BB"} /> </span>
                    : <span onClick={() => this.props.checkGlobalAuthBox()}> <FontAwesomeIcon size='lg' icon={faSquare} color={"#0073BB"}/> </span>
                }

                <span className={"left-margin"}> {this.props.rowText} </span>
            </div>
        )
    }
}

class GlobalAuthenticationConfigurations extends React.Component<any, {textField: any}>{
    constructor(props) {
        super(props);
        this.state = {
            textField: ""

        };
        this.handleChange1 = this.handleChange1.bind(this);
    }

    handleChange1(event) {
        let {name, value} = event.target;
        console.log(value);
        this.setState({textField: value})
    }

    render() {
        const rowTexts = [
            {id: "one", text: "Allow self sign up"},
            {id: "two", text: "Allow users to edit their own email addresses"},
            {id: "three", text: "Auto-create authenticated users"}];

        return(
            <Panel>
                <Panel.Heading>
                    <strong>Global Authentication Configurations</strong>
                </Panel.Heading>

                <Panel.Body>
                    <strong> Sign up and email options</strong>

                    <br/><br/>

                    {/* TODO: map for checkBoxRows */}

                    {rowTexts.map((text) =>
                        (<CheckBoxRows
                            key={text.id}
                            rowText={text.text}
                            checked={this.state[text.id]}
                            onClick={(e) => console.log("fuck")}
                            checkGlobalAuthBox={(e) => {this.handleChange1(e)}}
                        />)
                    )}

                    <div className={"form-inline globalAuthConfig-leftMargin"}>
                        Default email domain:

                        <FormControl
                            className={"globalAuthConfig-textInput globalAuthConfig-leftMargin"}
                            type="text"
                            value={this.state.textField}
                            placeholder="Enter text"
                            onChange={(e) => this.handleChange1(e)}
                        >

                        </FormControl>
                    </div>
                </Panel.Body>
            </Panel>
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
                            <MenuItem key={authOption.id} >
                                {authOption.name} : {authOption.description}
                            </MenuItem>
                        ))}
                    </DropdownButton>

                    <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                    <hr/>

                    <strong> Labkey Login Form Authentications </strong>
                    <LabelHelpTip title={'test'} body={() => {
                        return (<div> Tip 1: Ask Adam on text </div>)
                    }}/>

                    <br/>
                    <br/>

                    <Tabs defaultActiveKey={1} id="uncontrolled-tab-example">
                        <Tab eventKey={1} title="Primary">
                            <div className={"auth-tab"}>
                                <DragNDropMe className={"auth-tab"}/>
                            </div>




                            <br/><br/><br/><br/><br/><br/><br/>


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
                        return (<div> Tip 2: Ask Adam on text </div>)
                    }}/>

                    <br/><br/>
                    <Tabs defaultActiveKey={1} id="uncontrolled-tab-example">
                        <Tab eventKey={1} title="Primary">
                            Grid 1

                            <br/><br/><br/><br/><br/><br/><br/>
                        </Tab>
                        <Tab eventKey={2} title="Secondary">
                            Grid 2

                            <br/><br/><br/><br/><br/><br/><br/>
                        </Tab>
                    </Tabs>

                </Panel.Body>
            </Panel>
        )
    }
}

class ConfigurationModal extends React.Component<any>{

    cancelChanges = () => {
        console.log("to do")
    };

    render() {
        return(
            <Modal show={true} onHide={this.cancelChanges}>
                <Modal.Header>
                    <Modal.Title>Modal title</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    <strong>Replace title here </strong>
                    <hr/>
                    <strong>General Settings </strong>

                    <div>
                        Description:
                    </div>

                    <div>
                        Replace me:
                    </div>

                    <Checkbox>Blah</Checkbox>

                    <hr/>

                    <strong> Logo Settings </strong>

                    Use logo on login page:
                </Modal.Body>
            </Modal>
        )
    }
}

export class App extends React.Component<any, State> {

    constructor(props) {
        super(props);
        this.state = {
            // tickSelfSignup: false,
            // tickEditOwnEmail: false,
            // tickAutoCreateAuthUsers: false
        };
        this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);
    }

    cancelChanges = () => {
        console.log("to do")
    };

    checkGlobalAuthBox = () => {
        alert("uh");
    };

    render() {
        return(
            <div>
                <GlobalAuthenticationConfigurations {...this.state} checkGlobalAuthBox={() => this.checkGlobalAuthBox()} />
                <AuthenticationConfigurations/>

                {false && <Alert>You have unsaved changes.</Alert>}

                <Button className={'labkey-button primary'} onClick={this.cancelChanges}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>
            </div>
        );
    }
}
