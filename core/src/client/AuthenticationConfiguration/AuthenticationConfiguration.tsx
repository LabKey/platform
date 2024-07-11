import React, { PureComponent } from 'react';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { LoadingSpinner, resolveErrorMessage, Alert } from '@labkey/components';

import { GlobalSettings } from '../components/GlobalSettings';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';

import { Actions, AuthConfig, AuthConfigProvider, GlobalSettingsOptions } from '../components/models';

import { reorder, isEquivalent, addOrUpdateAnAuthConfig } from './utils';

const UNSAVED_ALERT =
    'You have unsaved changes to your authentication configurations. Click "Save and Finish" to apply these changes.';

interface State {
    authCount: number;
    canEdit: boolean;
    dirtinessData: { [key: string]: AuthConfig[] };
    dirty: boolean;
    error: string;
    formConfigurations: AuthConfig[];
    globalSettings: { [key: string]: any };
    helpLink: string;
    initError: string;
    loading: boolean;
    primaryProviders: AuthConfigProvider[];
    secondaryConfigurations: AuthConfig[];
    secondaryProviders: AuthConfigProvider[];
    ssoConfigurations: AuthConfig[];
}

export class App extends PureComponent<{}, Partial<State>> {
    actions: Actions;
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
            failure: Utils.getCallbackWrapper(
                error => {
                    console.error('Failed to load initial configuration', error);
                    this.setState({
                        loading: false,
                        initError: resolveErrorMessage(error),
                    });
                },
                undefined,
                true
            ),
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

    globalAuthOnChange = (id: string, value: any): void => {
        if (!this.state.canEdit) {
            return;
        }
        const globalSettingsDirtinessData = this.state.dirtinessData.globalSettings;
        const defaultDomainValidity = id === 'DefaultDomain' && !value.includes('@') ? '' : this.state.error;

        this.setState(prevState => ({
            ...prevState,
            error: defaultDomainValidity,
            globalSettings: { ...prevState.globalSettings, [id]: value },
            dirty: !isEquivalent({ ...prevState.globalSettings, [id]: value }, globalSettingsDirtinessData),
        }));
    };

    saveChanges = (): void => {
        // Do not warn about navigating away if user has pressed 'save'
        window.removeEventListener('beforeunload', this.handleLeavePage);

        if (this.state.error) {
            this.setState({ error: undefined });
        }

        if (this.state.globalSettings.DefaultDomain.includes('@')) {
            this.setState({ error: "The System Default Domain must not contain an '@' character." });
            return;
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
                    form.append(configType, this.getAuthConfigArray(this.state[configType]).slice(0, -1).toString());
                } else {
                    form.append(configType, this.getAuthConfigArray(this.state[configType]).toString());
                }
            });
        }

        Ajax.request({
            url: ActionURL.buildURL('login', 'saveSettings.api'),
            method: 'POST',
            form,
            failure: Utils.getCallbackWrapper(
                error => {
                    console.error('Failed to save settings', error);
                    this.setState({
                        error: resolveErrorMessage(error),
                    });
                },
                undefined,
                true
            ),
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

    onDragEnd = (result: { [key: string]: any }): void => {
        const { globalSettings, dirtinessData } = this.state;

        if (!result.destination) {
            return;
        }

        const configType = result.source.droppableId;

        const items = reorder(this.state[configType], result.source.index, result.destination.index);

        this.setState(state => {
            const newState = { ...state, [configType]: items };

            const configTypes = ['formConfigurations', 'ssoConfigurations', 'secondaryConfigurations'];
            const dirtyStateAreas = configTypes.filter(configType => {
                const newOrdering = newState[configType];
                const oldOrdering = state.dirtinessData[configType];
                return !isEquivalent(newOrdering, oldOrdering);
            });
            const dirty = dirtyStateAreas.length > 0 || !isEquivalent({ ...globalSettings }, dirtinessData);

            return { ...newState, dirty };
        });
    };

    resetSecondaryProviders = (): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'initialMount.api'),
            failure: Utils.getCallbackWrapper(
                error => {
                    console.error('Failed to load data', error);
                    this.setState({
                        loading: false,
                        initError: resolveErrorMessage(error),
                    });
                },
                undefined,
                true
            ),
            success: Utils.getCallbackWrapper(response => {
                const { secondaryProviders } = response;
                this.setState({ secondaryProviders });
            }),
        });
    };

    onDelete = (configuration: number, configType: string): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'deleteConfiguration.api'),
            method: 'POST',
            params: { configuration },
            failure: Utils.getCallbackWrapper(
                error => {
                    console.error('Failed to delete configuration', error);
                    this.setState({
                        error: resolveErrorMessage(error),
                    });
                },
                undefined,
                true
            ),
            success: Utils.getCallbackWrapper(() => {
                this.setState(
                    state => ({
                        [configType]: state[configType].filter(auth => auth.configuration !== configuration),
                    }),
                    this.resetSecondaryProviders
                );
            }),
        });
    };

    updateAuthRowsAfterSave = (config: string, configType: string): void => {
        this.setState(state => {
            const prevState = state[configType];
            const newState = addOrUpdateAnAuthConfig(config, prevState, configType);

            // Update our dirtiness information with added modal, since dirtiness should only track reordering
            const dirtinessData = { ...state.dirtinessData, [configType]: newState };
            return { [configType]: newState, dirtinessData };
        }, this.resetSecondaryProviders);
    };

    render() {
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
            canEdit,
        } = this.state;

        if (loading) {
            return <LoadingSpinner />;
        }

        if (initError) {
            return <Alert bsStyle="danger">{initError}</Alert>;
        }

        const authCount = formConfigurations.length + ssoConfigurations.length;

        return (
            <div className="parent-panel">
                <GlobalSettings
                    globalSettings={globalSettings as GlobalSettingsOptions}
                    canEdit={canEdit}
                    onChange={this.globalAuthOnChange}
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

                {dirty && <Alert>{UNSAVED_ALERT}</Alert>}
                {error && <Alert bsStyle="danger">{error}</Alert>}

                {canEdit ? (
                    <>
                        <button
                            className="labkey-button btn btn-default parent-panel__cancel-button"
                            onClick={this.onCancel}
                            type="button"
                        >
                            Cancel
                        </button>
                        <button
                            className="labkey-button primary btn btn-primary parent-panel__save-button pull-right"
                            onClick={this.saveChanges}
                            type="button"
                        >
                            Save and Finish
                        </button>
                    </>
                ) : (
                    <button className="btn btn-default labkey-button" onClick={this.onCancel}>
                        Done
                    </button>
                )}
            </div>
        );
    }
}
