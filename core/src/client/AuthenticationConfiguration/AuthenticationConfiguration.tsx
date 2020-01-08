import React, { PureComponent } from 'react';

import {Button, Alert} from 'react-bootstrap';
import {Map, List, fromJS} from 'immutable';

import GlobalSettings from '../components/GlobalSettings';
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
    ssoConfigurations?: Array<Object>
    formConfigurations?: any
    secondaryProviders?: any
    globalSettings?: Object
    canEdit?: boolean
    primary?: Object
    dirty?: boolean
}

export class App extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            ssoConfigurations: null,
            formConfigurations: null,
            secondaryProviders: null,
            globalSettings: null,
            canEdit: false,
            primaryProviders: null,
            dirtinessData: null,
            dirty: false,
            someModalOpen: false,
            authCount: null,
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
                const {formConfigurations, ssoConfigurations, secondaryConfigurations} = response;
                const dirtinessData = {
                    globalSettings: response.globalSettings,
                    formConfigurations: formConfigurations,
                    ssoConfigurations: ssoConfigurations,
                    secondaryConfigurations: secondaryConfigurations,
                };
                const authCount = formConfigurations.length + ssoConfigurations.length;
                this.setState({...response, dirtinessData, authCount}, () => {console.log(this.state)});
            }
        })
    };

    toggleSomeModalOpen = (someModalOpen) => {
        this.setState({someModalOpen});
    };

    // For globalSettings

    checkGlobalAuthBox = (id: string) => {
        const oldState = this.state.globalSettings[id];
        this.setState(
            (prevState) => ({
                ...prevState,
                globalSettings: {
                    ...prevState.globalSettings,
                    [id]: !oldState
                }
            }),
            () => {
                this.checkIfDirty(this.state.globalSettings, this.state.dirtinessData.globalSettings);
            }
        );
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
        return true;
    };

    saveChanges = () => {
        let form = new FormData();
        Object.keys(this.state.globalSettings).map(
            (item) => {
                form.append(item, this.state.globalSettings[item]);
            }
        );

        const dirtyStateSections = this.draggableIsDirty();
        dirtyStateSections.map((stateSection) => {
            if (stateSection == "formConfigurations"){
                // remove database config
                form.append(stateSection, this.getAuthConfigArray(this.state[stateSection]).slice(0,-1).toString());
                console.log("appending ", stateSection, ":", this.getAuthConfigArray(this.state[stateSection]).slice(0,-1).toString())
            } else {
                form.append(stateSection, this.getAuthConfigArray(this.state[stateSection]).toString());
                console.log("appending ", stateSection, ":", this.getAuthConfigArray(this.state[stateSection]).toString())

            }
        });

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
                // window.location.href = ActionURL.buildURL("admin", "showAdmin" )
            }
        })
    };

    draggableIsDirty = () => {
        const stateSections = ["formConfigurations", "ssoConfigurations", "secondaryConfigurations"];

        return stateSections.filter((stateSection) => {
            const newOrdering = this.getAuthConfigArray(this.state[stateSection]);
            const oldOrdering = this.getAuthConfigArray(this.state.dirtinessData[stateSection]);
            return !this.isEquivalent(newOrdering, oldOrdering);
        });
    };

    getAuthConfigArray = (stateSection) => {
        return stateSection.map((auth : any) => { return auth.configuration });
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
            console.log("full state", this.state); // for testing

            const dirty = this.draggableIsDirty().length > 0;
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
                success: function() {
                    console.log("success");
                    const authCount = this.state.authCount - 1;
                    this.setState({authCount});
                }
            })}
        )
    };

    updateAuthRowsAfterSave = (config, stateSection) => {
        const configObj = JSON.parse(config);
        const configId = configObj.configuration.configuration;

        const prevState = this.state[stateSection];
        const staleAuthIndex = prevState.findIndex((element) => element.configuration == configId);

        let newState = prevState.slice(0); // To reviewer: This avoids mutation of prevState, but is it overzealous?
        if (staleAuthIndex == -1) {
            if (stateSection == "formConfigurations") {
                newState = [...newState.slice(0, -1), configObj.configuration, ...newState.slice(-1)]
            } else {
                newState.push(configObj.configuration);
            }
        } else {
            newState[staleAuthIndex] = configObj.configuration;
        }

        this.setState({[stateSection]:newState});
    };

    render() {
        const alertText = "You have unsaved changes to your authentication configurations. Hit \"Save and Finish\" to apply these changes.";
        const {globalSettings, ...restProps} = this.state;
        // const {globalSettings, canEdit, ...restProps} = this.state; //for testing
        const actionFunctions = {
            "onDragEnd": this.onDragEnd,
            "deleteAction": this.deleteAction,
            "updateAuthRowsAfterSave": this.updateAuthRowsAfterSave,
            "toggleSomeModalOpen": this.toggleSomeModalOpen
        };

        return(
            <div style={{minWidth:"1150px"}}>
                {this.state.globalSettings &&
                    <GlobalSettings
                        {...globalSettings}
                        canEdit={this.state.canEdit}
                        checkGlobalAuthBox = {this.checkGlobalAuthBox}
                        authCount={this.state.authCount}
                    />
                }

                <AuthConfigMasterPanel
                    {...restProps}
                    // canEdit={false}
                    isDragDisabled={this.state.someModalOpen}
                    actionFunctions={actionFunctions}
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
