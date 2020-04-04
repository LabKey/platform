import React, { PureComponent } from 'react';
import { Button, Alert } from 'react-bootstrap';
import { Ajax, ActionURL } from '@labkey/api';

import GlobalSettings from '../components/GlobalSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { reorder, isEquivalent, addOrUpdateAnAuthConfig } from './utils';
import { AuthConfig, AuthConfigProvider } from "../components/models";

import "@labkey/components/dist/components.css"
import './authenticationConfiguration.scss';

interface State {
    formConfigurations?: AuthConfig[];
    ssoConfigurations?: AuthConfig[];
    secondaryConfigurations?: AuthConfig[];
    primaryProviders?: AuthConfigProvider[];
    secondaryProviders?: AuthConfigProvider[];
    globalSettings?: {[key: string]: any;};
    helpLink?: string;
    canEdit?: boolean;
    dirtinessData?: {[key: string]: AuthConfig[] };
    dirty?: boolean;
    modalOpen?: boolean;
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
            modalOpen: false,
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

    toggleModalOpen = (modalOpen: boolean): void => {
        this.setState({ modalOpen });
    };

    checkGlobalAuthBox = (id: string): void => {
        if (!this.state.canEdit) {
            return;
        }
        const globalSettingsDirtinessData = this.state.dirtinessData.globalSettings;
        const oldState = this.state.globalSettings[id];
        const newState = {...this.state.globalSettings, [id]: !oldState };

        this.setState(
            prevState => ({
                ...prevState,
                globalSettings: { ...newState },
                dirty: !isEquivalent(newState, globalSettingsDirtinessData)
            })
        );
    };

    saveChanges = (): void => {
        // Do not warn about navigating away if user has pressed 'save'
        window.removeEventListener('beforeunload', this.handleLeavePage);

        const form = new FormData();

        Object.keys(this.state.globalSettings).map(item => {
            form.append(item, this.state.globalSettings[item]);
        });

        const dirtyConfigType = this.draggableIsDirty();
        if (dirtyConfigType.length !== 0) {
            dirtyConfigType.map(configType => {
                if (configType == 'formConfigurations') {
                    // slice to remove database config, which has a fixed position
                    form.append(
                        configType,
                        this.getAuthConfigArray(this.state[configType])
                            .slice(0, -1)
                            .toString()
                    );
                } else {
                    form.append(configType, this.getAuthConfigArray(this.state[configType]).toString());
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
                window.location.assign(ActionURL.buildURL('admin', 'showAdmin'));
            },
        });
    };

    onCancel = (): void => {
        window.location.assign(ActionURL.buildURL('admin', 'showAdmin'));
    };

    draggableIsDirty = (): string[] => {
        const configTypes = ['formConfigurations', 'ssoConfigurations', 'secondaryConfigurations'];

        return configTypes.filter(configType => {
            const newOrdering = this.getAuthConfigArray(this.state[configType]);
            const oldOrdering = this.getAuthConfigArray(this.state.dirtinessData[configType]);
            return !isEquivalent(newOrdering, oldOrdering);
        });
    };

    getAuthConfigArray = (configType: AuthConfig[]): AuthConfig[] => {
        return configType.map((auth: any) => {
            return auth.configuration;
        });
    };

    onDragEnd = (result: {[key: string]: any;}): void => {
        if (!result.destination) {
            return;
        }

        const configType = result.source.droppableId;

        const items = reorder(this.state[configType], result.source.index, result.destination.index);

        this.setState((state) => {
            const newState = {...state, [configType]: items };

            const configTypes = ['formConfigurations', 'ssoConfigurations', 'secondaryConfigurations'];
            const dirtyStateAreas = configTypes.filter(configType => {
                const newOrdering = newState[configType];
                const oldOrdering = state.dirtinessData[configType];
                return !isEquivalent(newOrdering, oldOrdering);
            });
            const dirty = dirtyStateAreas.length > 0;

            return {...newState, dirty};
        });
    };

    onDelete = (configuration: number, configType: string): void => {
        const prevState = this.state[configType];
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
                this.setState({ authCount, [configType]: newState });
            },
        });
    };

    updateAuthRowsAfterSave = (config: string, configType: string): void => {
        this.setState((state) => {
            const prevState = state[configType];
            const newState = addOrUpdateAnAuthConfig(config, prevState, configType);

            // Update our dirtiness information with added modal, since dirtiness should only track reordering
            const dirtinessData = { ...state.dirtinessData, [configType]: newState };
            return { [configType]: newState, dirtinessData };
        });
    };

    render() {
        const alertText =
            'You have unsaved changes to your authentication configurations. Click "Save and Finish" to apply these changes.';
        const { globalSettings, dirtinessData, dirty, authCount, modalOpen, ...restProps } = this.state;
        const actions = {
            onDragEnd: this.onDragEnd,
            onDelete: this.onDelete,
            updateAuthRowsAfterSave: this.updateAuthRowsAfterSave,
            toggleModalOpen: this.toggleModalOpen,
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
                    isDragDisabled={this.state.modalOpen}
                    actions={actions}
                />

                {this.state.dirty && <Alert> {alertText} </Alert>}

                {this.state.canEdit ?
                    <>
                        <Button className="labkey-button primary parent-panel__save-button" onClick={this.saveChanges}>
                            Save and Finish
                        </Button>

                        <Button
                            className="labkey-button parent-panel__cancel-button"
                            onClick={this.onCancel}
                        >
                            Cancel
                        </Button>
                    </>
                    :
                    < Button
                        className="labkey-button"
                        onClick={this.onCancel}
                    >
                        Done
                    </Button>
                }
            </div>
        );
    }
}
