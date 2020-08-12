import React, { PureComponent } from 'react';
import { Button, Alert } from 'react-bootstrap';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { LoadingSpinner, resolveErrorMessage } from '@labkey/components';

import GlobalSettings from '../components/GlobalSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { reorder, isEquivalent, addOrUpdateAnAuthConfig } from './utils';
import { Actions, AuthConfig, AuthConfigProvider, GlobalSettingsOptions } from '../components/models';

import '@labkey/components/dist/components.css';
import './authenticationConfiguration.scss';

interface State {
    error: string;
    initError: string;
    loading: boolean;
    formConfigurations: AuthConfig[];
    ssoConfigurations: AuthConfig[];
    secondaryConfigurations: AuthConfig[];
    primaryProviders: AuthConfigProvider[];
    secondaryProviders: AuthConfigProvider[];
    globalSettings: {[key: string]: any;};
    helpLink: string;
    canEdit: boolean;
    dirtinessData: {[key: string]: AuthConfig[] };
    dirty: boolean;
    authCount: number;
}

export class App extends PureComponent<{}, Partial<State>> {
    public actions: Actions;
    constructor(props) {
        super(props);

        this.actions = {
            onDragEnd: this.onDragEnd,
            onDelete: this.onDelete,
            updateAuthRowsAfterSave: this.updateAuthRowsAfterSave,
        };

        this.state = {
            error: undefined,
            initError: undefined,
            loading: true,
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
            url: ActionURL.buildURL('login', 'initialMount.api'),
            failure: Utils.getCallbackWrapper(error => {
                console.error('Failed to load initial configuration', error);
                this.setState({
                    loading: false,
                    initError: resolveErrorMessage(error),
                });
            }, undefined, true),
            success: Utils.getCallbackWrapper(response => {
                const { formConfigurations, ssoConfigurations, secondaryConfigurations } = response;
                const dirtinessData = {
                    globalSettings: response.globalSettings,
                    formConfigurations,
                    ssoConfigurations,
                    secondaryConfigurations,
                };
                const authCount = formConfigurations.length + ssoConfigurations.length;
                const loading = false;
                this.setState({ ...response, dirtinessData, authCount, loading });
            }),
        });
    };

    handleLeavePage = e => {
        if (this.state.dirty) {
            e.preventDefault();
            e.returnValue = 'Unsaved changes.';
        }
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

        if (this.state.error) {
            this.setState({ error: undefined });
        }

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
            url: ActionURL.buildURL('login', 'saveSettings.api'),
            method: 'POST',
            form,
            failure: Utils.getCallbackWrapper(error => {
                console.error('Failed to save settings', error);
                this.setState({
                    error: resolveErrorMessage(error),
                });
            }, undefined, true),
            success: () => {
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

    getAuthConfigArray = (configType: AuthConfig[]): number[] => {
        return configType.map(auth => auth.configuration);
    };

    onDragEnd = (result: {[key: string]: any;}): void => {
        const {globalSettings, dirtinessData} = this.state;

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
            const dirty = dirtyStateAreas.length > 0 || !isEquivalent({...globalSettings}, dirtinessData);

            return {...newState, dirty};
        });
    };

    onDelete = (configuration: number, configType: string): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'deleteConfiguration.api'),
            method: 'POST',
            params: { configuration },
            failure: Utils.getCallbackWrapper(error => {
                console.error('Failed to delete configuration', error);
                this.setState({
                    error: resolveErrorMessage(error),
                });
            }, undefined, true),
            success: function() {
                this.setState((state) => ({
                    [configType]: state[configType].filter(auth => auth.configuration !== configuration)
                }))
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
        const {
            error,
            initError,
            loading,
            globalSettings,
            dirty,
            formConfigurations,
            ssoConfigurations,
            secondaryConfigurations,
            primaryProviders,
            secondaryProviders,
            helpLink,
            canEdit
        } = this.state;

        if (loading) {
            return <LoadingSpinner/>;
        }

        if (initError) {
            return <Alert bsStyle="danger">{initError}</Alert>;
        }

        let authCount = formConfigurations.length + ssoConfigurations.length;

        return (
            <div className="parent-panel">
                {error && <Alert bsStyle="danger">{error}</Alert>}

                <GlobalSettings
                    globalSettings={globalSettings as GlobalSettingsOptions}
                    canEdit={canEdit}
                    checkGlobalAuthBox={this.checkGlobalAuthBox}
                    authCount={authCount}
                />

                <AuthConfigMasterPanel
                    formConfigurations={formConfigurations}
                    ssoConfigurations={ssoConfigurations}
                    secondaryConfigurations={secondaryConfigurations}
                    primaryProviders={primaryProviders}
                    secondaryProviders={secondaryProviders}
                    helpLink={helpLink}
                    canEdit={canEdit}
                    actions={this.actions}
                />

                {dirty && <Alert> {alertText} </Alert>}

                {canEdit ?
                    <>
                        <Button
                            className="labkey-button parent-panel__cancel-button"
                            onClick={this.onCancel}
                        >
                            Cancel
                        </Button>
                        <Button className="labkey-button primary parent-panel__save-button pull-right" onClick={this.saveChanges}>
                            Save and Finish
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
