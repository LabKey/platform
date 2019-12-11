import * as React from 'react'

import {Button, Alert} from 'react-bootstrap'
import {Map, List, fromJS} from 'immutable';

import "./authenticationConfiguration.scss";
import "@glass/base/dist/base.css"
import GlobalAuthConfigs from '../components/GlobalAuthConfigs';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { Ajax, ActionURL, Security } from '@labkey/api'

// Todo, meta:
// Finalize TS, Immutable

// Todo:
// Display error component upon ajax fail
// Q: How to specify specific component as TS type in a props interface

interface Props {} // Q: Is this how you specify no props?

interface State {
    singleSignOnAuth: Array<Object>
    loginFormAuth: any
    secondaryAuth: any
    globalAuthConfigs: Object
    canEdit: boolean
    addNewPrimary: Object
    dirty: boolean
}
export class App extends React.PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            singleSignOnAuth: null,
            loginFormAuth: null,
            secondaryAuth: null,
            globalAuthConfigs: null,
            canEdit: false,
            addNewPrimary: null,
            dirty: false,
        };

        // Testing functions
        this.savePls = this.savePls.bind(this);
        this.saveChanges = this.saveChanges.bind(this);

        this.checkIfDirty = this.checkIfDirty.bind(this);
        this.isEquivalent = this.isEquivalent.bind(this);


        // For AuthConfigMasterPanel
        this.onDragEnd = this.onDragEnd.bind(this);
        this.reorder = this.reorder.bind(this);
        this.handleChangeToPrimary = this.handleChangeToPrimary.bind(this);
        this.handlePrimaryToggle = this.handlePrimaryToggle.bind(this);
    }

    componentDidMount() {
        Ajax.request({
            url: ActionURL.buildURL("login", "InitialMount"),
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

    savePls() {
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"),
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

    checkIfDirty(obj1, obj2){
        let dirty = !this.isEquivalent(obj1, obj2);

        this.setState(prevState => ({
            ...prevState,
            dirty: dirty
        }))
    }

    isEquivalent(a, b) {
        let aProps = Object.getOwnPropertyNames(a);
        let bProps = Object.getOwnPropertyNames(b);

        if (aProps.length != bProps.length) {
            return false;
        }

        for (var i = 0; i < aProps.length; i++) {
            let propName = aProps[i];

            if (a[propName] !== b[propName]) {
                return false;
            }
        }
        return true;
    }

    saveChanges() {
        console.log(this.state);
        console.log("globalAuthConfig props: ",{...this.state.globalAuthConfigs});
    };

    onDragEnd(result) {
        console.log("result ", result.source.droppableId);
        const stateSection = result.source.droppableId;

        if (!result.destination)
        {
            return;
        }

        const items = this.reorder(
            this.state[stateSection],
            result.source.index,
            result.destination.index
        );

        this.setState(prevState => ({
            ...prevState,
            [stateSection]: items
        }))
    }

    reorder = (list, startIndex, endIndex) => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    // rough
    handleChangeToPrimary(event) {
        let {name, value, id} = event.target;
        const l = List(this.state.singleSignOnAuth);
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
    handlePrimaryToggle(toggle, id, stateSection){
        const l = List(this.state[stateSection]);
        const l2 = l.setIn([id, 'enabled'], !toggle);
        const thing = l2.toArray();
        this.setState(prevState => ({
            ...prevState,
            [stateSection]: thing
        }))
    }

    render() {
        return(
            <div style={{minWidth:"1100px"}}>
                {this.state.globalAuthConfigs &&
                    <GlobalAuthConfigs
                        {...this.state.globalAuthConfigs}
                        checkDirty = {this.checkIfDirty}
                    />
                }

                <AuthConfigMasterPanel
                    singleSignOnAuth={this.state.singleSignOnAuth}
                    loginFormAuth={this.state.loginFormAuth}
                    secondary={this.state.secondaryAuth}
                    addNewPrimary={this.state.addNewPrimary}
                    onDragEnd={this.onDragEnd}
                    handleChangeToPrimary={this.handleChangeToPrimary}
                    handlePrimaryToggle={this.handlePrimaryToggle}
                />

                {this.state.dirty && <Alert>You have unsaved changes to your authentication configurations. Hit "Save and Finish" to apply these changes.</Alert>}

                <Button
                    className={'labkey-button primary'}
                    onClick={this.saveChanges}
                >
                    Save and Finish
                </Button>

                <Button
                    className={'labkey-button'}
                    onClick={() => {window.location.href = ActionURL.buildURL("admin", "showAdmin" )}}
                    style={{marginLeft: '10px'}}
                >
                    Cancel
                </Button>
            </div>
        );
    }
}
