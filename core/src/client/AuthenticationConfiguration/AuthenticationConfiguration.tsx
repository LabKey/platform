import React, { PureComponent } from 'react';

import { Button, Alert } from 'react-bootstrap';

import { Ajax, ActionURL } from '@labkey/api';

import GlobalSettings from '../components/GlobalSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';

import './authenticationConfiguration.scss';

interface State {
    formConfigurations?: AuthConfig[];
    ssoConfigurations?: AuthConfig[];
    secondaryConfigurations?: AuthConfig[];
    primaryProviders?: Record<string, any>;
    secondaryProviders?: Record<string, any>;
    globalSettings?: Record<string, any>;
    helpLink?: string;
    canEdit?: boolean;
    dirtinessData?: Record<string, any>;
    dirty?: boolean;
    someModalOpen?: boolean;
    authCount?: number;
}

export class App extends PureComponent<{}, State> {
    constructor(props) {
        super(props);
        this.state = {
            formConfigurations: null,
            ssoConfigurations: null,
            secondaryConfigurations: null,
            primaryProviders: null,
            secondaryProviders: null,
            globalSettings: null,
            helpLink: null,
            canEdit: false,
            dirtinessData: null,
            dirty: false,
            someModalOpen: false,
            authCount: null,
        };
    }

    componentDidMount(): void {
        this.loadInitialConfigData();
        window.addEventListener('beforeunload', this.handleLeavePage);
    }

    componentWillUnmount(): void {
        window.removeEventListener('beforeunload', this.handleLeavePage);
    }

    loadInitialConfigData = (): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'InitialMount'),
            method: 'GET',
            scope: this,
            failure: function(error) {
                alert('Error: ' + error);
            },
            success: function(result) {
                const response = JSON.parse(result.response);
                const { formConfigurations, ssoConfigurations, secondaryConfigurations } = response;
                const dirtinessData = {
                    globalSettings: response.globalSettings,
                    formConfigurations,
                    ssoConfigurations,
                    secondaryConfigurations,
                };
                const authCount = formConfigurations.length + ssoConfigurations.length;
                this.setState({ ...response, dirtinessData, authCount });
            },
        });
    };

    handleLeavePage = e => {
        if (this.state.dirty) {
            e.preventDefault();
            e.returnValue = 'Unsaved changes.';
        }
    };

    toggleSomeModalOpen = (someModalOpen: boolean): void => {
        this.setState({ someModalOpen });
    };

    checkGlobalAuthBox = (id: string): void => {
        const oldState = this.state.globalSettings[id];
        this.setState(
            prevState => ({
                ...prevState,
                globalSettings: {
                    ...prevState.globalSettings,
                    [id]: !oldState,
                },
            }),
            () => {
                this.checkIfDirty(this.state.globalSettings, this.state.dirtinessData.globalSettings);
            }
        );
    };

    checkIfDirty = (obj1: any, obj2: any): void => {
        const dirty = !this.isEquivalent(obj1, obj2);
        this.setState(() => ({ dirty }));
    };

    isEquivalent = (a: any, b: any): boolean => {
        const aProps = Object.keys(a);
        const bProps = Object.keys(b);

        if (aProps.length != bProps.length) {
            return false;
        }

        return !aProps.some(key => {
            return a[key] !== b[key];
        });
    };

    saveChanges = (): void => {
        const form = new FormData();

        Object.keys(this.state.globalSettings).map(item => {
            form.append(item, this.state.globalSettings[item]);
        });

        const dirtyStateSections = this.draggableIsDirty();
        if (dirtyStateSections.length !== 0) {
            dirtyStateSections.map(stateSection => {
                if (stateSection == 'formConfigurations') {
                    // slice to remove database config, which has a fixed position
                    form.append(
                        stateSection,
                        this.getAuthConfigArray(this.state[stateSection])
                            .slice(0, -1)
                            .toString()
                    );
                } else {
                    form.append(stateSection, this.getAuthConfigArray(this.state[stateSection]).toString());
                }
            });
        }

        Ajax.request({
            url: ActionURL.buildURL('login', 'SaveSettings'),
            method: 'POST',
            form,
            scope: this,
            failure: function(error) {
                alert('Error: ' + error);
            },
            success: function() {
                window.location.href = ActionURL.buildURL('admin', 'showAdmin');
            },
        });
    };

    draggableIsDirty = (): string[] => {
        const stateSections = ['formConfigurations', 'ssoConfigurations', 'secondaryConfigurations'];

        return stateSections.filter(stateSection => {
            const newOrdering = this.getAuthConfigArray(this.state[stateSection]);
            const oldOrdering = this.getAuthConfigArray(this.state.dirtinessData[stateSection]);
            return !this.isEquivalent(newOrdering, oldOrdering);
        });
    };

    getAuthConfigArray = (stateSection: AuthConfig[]): AuthConfig[] => {
        return stateSection.map((auth: any) => {
            return auth.configuration;
        });
    };

    onDragEnd = (result: Record<string, any>): void => {
        if (!result.destination) {
            return;
        }

        const stateSection = result.source.droppableId;

        const items = this.reorder(this.state[stateSection], result.source.index, result.destination.index);

        this.setState(
            () => ({
                [stateSection]: items,
            }),
            () => {
                const dirty = this.draggableIsDirty().length > 0;
                this.setState(() => ({ dirty }));
            }
        );
    };

    // to reviewer: do we have a helper function for this somewhere? I started writing this
    // using slice instead of splice for immutability, but since I'm leaning on splice's additional
    // parameters, the slice version gets pretty yucky.
    reorder = (list: number[], startIndex: number, endIndex: number): number[] => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    deleteAction = (configuration: number, stateSection: string): void => {
        const prevState = this.state[stateSection];
        const newState = prevState.filter(auth => {
            return auth.configuration !== configuration;
        });

        Ajax.request({
            url: ActionURL.buildURL('login', 'deleteConfiguration'),
            method: 'POST',
            params: { configuration },
            scope: this,
            failure: function(error) {
                alert('Error: ' + error);
            },
            success: function() {
                const authCount = this.state.authCount - 1;
                this.setState({ authCount, [stateSection]: newState });
            },
        });
    };

    updateAuthRowsAfterSave = (config: string, stateSection: string): void => {
        const configObj = JSON.parse(config);
        const configId = configObj.configuration.configuration;
        console.log("config", config);
        console.log("stateSection", stateSection);

        const prevState = this.state[stateSection];
        const staleAuthIndex = prevState.findIndex(element => element.configuration == configId);

        let newState = prevState.slice(0); // To reviewer: This avoids mutation of prevState, but is it overzealous?
        if (staleAuthIndex == -1) {
            if (stateSection == 'formConfigurations') {
                newState = [...newState.slice(0, -1), configObj.configuration, ...newState.slice(-1)];
            } else {
                newState.push(configObj.configuration);
            }
        } else {
            newState[staleAuthIndex] = configObj.configuration;
        }

        // Update our dirtiness information with added modal, since dirtiness should only track reordering
        const dirtinessData = this.state.dirtinessData;
        dirtinessData[stateSection] = newState;

        this.setState({ [stateSection]: newState, dirtinessData });
    };

    render() {
        const alertText =
            'You have unsaved changes to your authentication configurations. Click "Save and Finish" to apply these changes.';
        const { globalSettings, dirtinessData, dirty, authCount, someModalOpen, ...restProps } = this.state;
        const actionFunctions = {
            onDragEnd: this.onDragEnd,
            deleteAction: this.deleteAction,
            updateAuthRowsAfterSave: this.updateAuthRowsAfterSave,
            toggleSomeModalOpen: this.toggleSomeModalOpen,
        };

        return (
            <div className="parent-panel">
                {this.state.globalSettings && (
                    <GlobalSettings
                        {...globalSettings}
                        canEdit={this.state.canEdit}
                        checkGlobalAuthBox={this.checkGlobalAuthBox}
                        authCount={this.state.authCount}
                    />
                )}

                <AuthConfigMasterPanel
                    {...restProps}
                    isDragDisabled={this.state.someModalOpen}
                    actionFunctions={actionFunctions}
                />

                {this.state.dirty && <Alert> {alertText} </Alert>}

                <Button className="labkey-button primary parent-panel__save-button" onClick={this.saveChanges}>
                    Save and Finish
                </Button>

                <Button
                    className="labkey-button parent-panel__cancel-button"
                    onClick={() => {
                        window.location.href = ActionURL.buildURL('admin', 'showAdmin');
                    }}>
                    Cancel
                </Button>
            </div>
        );
    }
}
