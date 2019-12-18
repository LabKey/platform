import React, { PureComponent } from 'react';

import {Button, Alert} from 'react-bootstrap';
import {Map, List, fromJS} from 'immutable';

import GlobalAuthSettings from '../components/GlobalAuthSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { Ajax, ActionURL, Security } from '@labkey/api';

import "./authenticationConfiguration.scss";

// Todo, meta:
// Finalize TS, Immutable

// Todo:
// Display error component upon ajax fail
// Q: How to specify specific component as TS type in a props interface

interface Props {} // Q: Is this how you specify no props?

interface State {
    singleSignOnAuth?: Array<Object>
    loginFormAuth?: any
    secondaryAuth?: any
    globalAuthSettings?: Object
    canEdit?: boolean
    primary?: Object
    dirty?: boolean
}

export class App extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            singleSignOnAuth: null,
            loginFormAuth: null,
            secondaryAuth: null,
            globalAuthSettings: null,
            canEdit: false,
            primary: null,
            dirty: false,
        };
    }

    componentDidMount() {
        this.loadInitialConfigData();
    }

    loadInitialConfigData = () => {
        Ajax.request({
            url: ActionURL.buildURL("login", "InitialMount"),
            method : 'GET',
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                const response = JSON.parse(result.response);
                this.setState({...response});
                console.log({...response});
            }
        })
    };

    savePls = () => {
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
    };

    checkIfDirty = (obj1, obj2) => {
        const dirty = !this.isEquivalent(obj1, obj2);

        this.setState(() => ({ dirty }));
    };

    isEquivalent = (a, b) => {
        const aProps = Object.keys(a);
        const bProps = Object.keys(b);

        if (aProps.length != bProps.length) {
            return false;
        }

        for (var i = 0; i < aProps.length; i++) {
            const propName = aProps[i];

            if (a[propName] !== b[propName]) {
                return false;
            }
        }

        // minor todo
        // return aProps.some((key) => {
        //     return a[key] !== b[key];
        // });
    };

    saveChanges = () => {
        console.log(this.state);
        console.log("globalAuthConfig props: ",{...this.state.globalAuthSettings});
    };

    onDragEnd = (result) => {
        if (!result.destination) {
            return;
        }

        // console.log("result ", result.source.droppableId);
        const stateSection = result.source.droppableId;

        const items = this.reorder (
            this.state[stateSection],
            result.source.index,
            result.destination.index
        );

        this.setState(() => ({
            [stateSection]: items
        }))
    };

    reorder = (list, startIndex, endIndex) => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    // rough
    handlePrimaryToggle = (toggle, id, stateSection) => {
        const l = List(this.state[stateSection]);
        const l2 = l.setIn([id, 'enabled'], !toggle);
        const thing = l2.toArray();
        this.setState(() => ({
            [stateSection]: thing
        }));

        // const prevConfig = this.state[stateSection][id];
        // const newConfig = {...prevConfig, enabled: !toggle };
        // console.log(newConfig);

        // this.setState( (prevState) => ({
        //     ...prevState,
        //     [stateSection]: {
        //         ...prevState[stateSection],
        //         [id]: newConfig
        //     }
        // }), () => console.log(this.state));
    };

    deleteAction = (id, stateSection) => {
        const prevState = this.state[stateSection];
        const newState = prevState.filter((auth) => {
            return auth.id !== id;
        });

        this.setState(() => ({
            [stateSection]: newState
        }),
            () => {
            Ajax.request({
                url: ActionURL.buildURL("login", "deleteConfiguration"),
                method : 'POST',
                params: {configuration: id.slice(2)},
                scope: this,
                failure: function(error){
                    console.log("fail: ", error);
                },
                success: function(){
                    console.log("success");
                }
            })}
        )
    };

    render() {
        const alertText = "You have unsaved changes to your authentication configurations. Hit \"Save and Finish\" to apply these changes.";
        const {globalAuthSettings, ...restProps} = this.state;

        return(
            <div style={{minWidth:"1100px"}}>
                {this.state.globalAuthSettings &&
                    <GlobalAuthSettings
                        {...globalAuthSettings}
                        checkDirty = {this.checkIfDirty}
                        canEdit={this.state.canEdit}
                    />
                }

                <AuthConfigMasterPanel
                    {...restProps}
                    onDragEnd={this.onDragEnd}
                    handlePrimaryToggle={this.handlePrimaryToggle}
                    deleteAction={this.deleteAction}
                />

                {this.state.dirty && <Alert> {alertText} </Alert>}

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
