import React, { PureComponent } from 'react';

import {Button, Alert} from 'react-bootstrap';
import {Map, List, fromJS} from 'immutable';

import GlobalAuthSettings from '../components/GlobalAuthSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { Ajax, ActionURL, Security } from '@labkey/api';

import './authenticationConfiguration.scss';

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

export class App extends PureComponent<Props, any> {
    constructor(props) {
        super(props);
        this.state = {
            singleSignOnAuth: null,
            loginFormAuth: null,
            secondaryAuth: null,
            globalAuthSettings: null,
            canEdit: false,
            primary: null,
            dirtinessData: null,
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
                const dirtinessData = {
                    globalAuthSettings: response.globalAuthSettings,
                    loginFormAuth: response.loginFormAuth,
                    singleSignOnAuth: response.singleSignOnAuth
                };
                this.setState({...response, dirtinessData}, () => {console.log(this.state)});
            }
        })
    };

    // For GlobalAuthSettings

    checkGlobalAuthBox = (id: string) => {
        const oldState = this.state.globalAuthSettings[id];
        this.setState(
            (prevState) => ({
                ...prevState,
                globalAuthSettings: {
                    ...prevState.globalAuthSettings,
                    [id]: !oldState
                }
            }),
            () => {
                this.checkIfDirty(this.state.globalAuthSettings, this.state.dirtinessData.globalAuthSettings);
            }
        );
    };

    checkIfDirty = (obj1, obj2) => {
        const dirty = !this.isEquivalent(obj1, obj2);
        console.log(obj1, " : ", obj2);
        console.log(dirty);
        this.setState(() => ({ dirty }));
    };

    isEquivalent = (a, b) => {
        const aProps = Object.keys(a);
        const bProps = Object.keys(b);

        if (aProps.length != bProps.length) {
            console.log("blip");
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
        return true;
    };




    saveChanges = () => {
        let form = new FormData();
        Object.keys(this.state.globalAuthSettings).map(
            (item) => {
                form.append(item, this.state.globalAuthSettings[item]);
            }
        );

        if (this.draggableIsDirty("singleSignOnAuth")){
            form.append("singleSignOnAuth", this.getAuthConfigConfigurationArray(this.state.singleSignOnAuth).toString());
        }
        if (this.draggableIsDirty("loginFormAuth")){
            form.append("loginFormAuth", this.getAuthConfigConfigurationArray(this.state.loginFormAuth).slice(0,-1).toString());
        }

        Ajax.request({
            url: ActionURL.buildURL("login", "SaveSettings"),
            method : 'POST',
            form,
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
                window.location.href = ActionURL.buildURL("admin", "showAdmin" ) // For reviewer: is this the best way to navigate after success?
            }
        })

    };

    draggableIsDirty = (stateSection) => {
        const newOrdering = this.getAuthConfigConfigurationArray(this.state[stateSection]);
        const oldOrdering = this.getAuthConfigConfigurationArray(this.state.dirtinessData[stateSection]);
        return !this.isEquivalent(newOrdering, oldOrdering);
    };

    getAuthConfigConfigurationArray = (stateSection) => {
        let authArray = stateSection.map((auth : any) => { return auth.configuration });
        console.log(authArray);
        return authArray;
    };


    // For AuthConfigMasterPanel

    onDragEnd = (result) => {
        if (!result.destination) {
            return;
        }

        const stateSection = result.source.droppableId;

        const items = this.reorder (
            this.state[stateSection],
            result.source.index,
            result.destination.index
        );

        this.setState(() => ({
            [stateSection]: items
        })
        , () => {
            console.log(this.state); // for testing

            const dirty = this.draggableIsDirty(stateSection);
            this.setState(() => ({ dirty }));
            }
        )
    };

    reorder = (list, startIndex, endIndex) => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    // rough
    handlePrimaryToggle = (toggle, configuration, stateSection) => {
        const l = List(this.state[stateSection]);
        const l2 = l.setIn([configuration, 'enabled'], !toggle);
        const thing = l2.toArray();
        console.log(" toggle: ", toggle, "\n", "id: ", configuration, "\n", "stateSection: ", stateSection);
        console.log(thing);
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

    deleteAction = (configuration, stateSection) => {
        const prevState = this.state[stateSection];
        const newState = prevState.filter((auth) => {
            return auth.configuration !== configuration;
        });

        this.setState(() => ({
            [stateSection]: newState
        }),
            () => {
            Ajax.request({
                url: ActionURL.buildURL("login", "deleteConfiguration"),
                method : 'POST',
                params: {configuration: configuration},
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



    updateAuthRowsAfterSave = (config, stateSection) => {
        const configObj = JSON.parse(config);
        const configId = configObj.configuration.configuration;

        const prevState = this.state[stateSection];
        const staleAuthIndex = prevState.findIndex((element) => element.configuration == configId);

        const newState = prevState.slice(0); // To reviewer: This avoids mutation of prevState, but is it overzealous?
        newState[staleAuthIndex] = configObj.configuration;

        this.setState({[stateSection]:newState});
    };

    render() {
        const alertText = "You have unsaved changes to your authentication configurations. Hit \"Save and Finish\" to apply these changes.";
        const {globalAuthSettings, ...restProps} = this.state;
        let hideAutoCreateAccounts = (this.state.loginFormAuth && this.state.loginFormAuth.length == 1) && (this.state.singleSignOnAuth && this.state.singleSignOnAuth.length == 0);

        return(
            <div style={{minWidth:"1100px"}}>
                {this.state.globalAuthSettings &&
                    <GlobalAuthSettings
                        {...globalAuthSettings}
                        canEdit={this.state.canEdit}
                        checkGlobalAuthBox = {this.checkGlobalAuthBox}
                        // hideAutoCreateAccounts={hideAutoCreateAccounts}
                    />
                }

                <AuthConfigMasterPanel
                    {...restProps}
                    onDragEnd={this.onDragEnd}
                    handlePrimaryToggle={this.handlePrimaryToggle}
                    deleteAction={this.deleteAction}
                    updateAuthRowsAfterSave={this.updateAuthRowsAfterSave}
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
