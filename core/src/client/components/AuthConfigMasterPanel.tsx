import React, { PureComponent, ReactNode } from 'react';
import { DropdownButton, MenuItem, LabelHelpTip, Tab, Tabs } from '@labkey/components';

import { LOGIN_FORM_TIP_TEXT, SSO_TIP_TEXT } from '../AuthenticationConfiguration/constants';

import DragAndDropPane from './DragAndDropPane';
import AuthRow from './AuthRow';
import DynamicConfigurationModal from './DynamicConfigurationModal';
import { Actions, AuthConfig, AuthConfigProvider } from './models';

interface ViewOnlyAuthConfigRowsProps {
    data: AuthConfig[];
    providers: AuthConfigProvider[];
}

class ViewOnlyAuthConfigRows extends PureComponent<ViewOnlyAuthConfigRowsProps> {
    render() {
        const { providers, data } = this.props;

        return (
            <div>
                {data.map(item => (
                    <AuthRow
                        authConfig={item}
                        key={item.configuration}
                        canEdit={false}
                        draggable={false}
                        modalType={{ ...providers[item.provider] }}
                    />
                ))}
            </div>
        );
    }
}

interface PrimaryTabProps {
    menuItems: ReactNode;
    canEdit: boolean;
    primaryTabLoginForm: ReactNode;
    body: ReactNode;
}

class PrimaryTab extends PureComponent<PrimaryTabProps> {
    render() {
        const { menuItems, primaryTabLoginForm, body, canEdit } = this.props;

        return (
            <>
                <div className="configurations__auth-tab">
                    {canEdit && (
                        <div className="configurations__dropdown">
                            <DropdownButton title="Add New Primary Configuration">{menuItems}</DropdownButton>
                        </div>
                    )}

                    <div className="configurations__config-section-title">
                        <span className="bold-text"> Login Form Configurations </span>
                        <LabelHelpTip title="Tip">
                            <div> {LOGIN_FORM_TIP_TEXT} </div>
                        </LabelHelpTip>
                    </div>

                    {primaryTabLoginForm}
                </div>

                <div>
                    <div className="configurations__config-section-title">
                        <span className="bold-text"> Single Sign-On Configurations </span>
                        <LabelHelpTip title="Tip">
                            <div> {SSO_TIP_TEXT} </div>
                        </LabelHelpTip>
                    </div>

                    {body}
                </div>
            </>
        );
    }
}

interface SecondaryTabProps {
    menuItems: ReactNode;
    canEdit: boolean;
    body: ReactNode;
}

class SecondaryTab extends PureComponent<SecondaryTabProps> {
    render() {
        const { canEdit, menuItems, body } = this.props;

        return (
            <div className="configurations__auth-tab">
                {canEdit && (
                    <div className="configurations__dropdown">
                        <DropdownButton title="Add New Secondary Configuration">
                            {menuItems}
                        </DropdownButton>
                    </div>
                )}

                {body}
            </div>
        );
    }
}

interface Props {
    actions: Actions;
    canEdit: boolean;
    formConfigurations: AuthConfig[];
    helpLink: string;
    primaryProviders: AuthConfigProvider[];
    secondaryConfigurations: AuthConfig[];
    secondaryProviders: AuthConfigProvider[];
    ssoConfigurations: AuthConfig[];
}

interface State {
    addModalType: string | null;
    modalOpen: boolean;
    primaryModalOpen: boolean;
    secondaryModalOpen: boolean;
}

export default class AuthConfigMasterPanel extends PureComponent<Props, Partial<State>> {
    constructor(props) {
        super(props);
        this.state = {
            primaryModalOpen: false,
            secondaryModalOpen: false,
            addModalType: null,
            modalOpen: false,
        };
    }

    // Whether a Create New AuthConfig modal is open
    onToggleModal = (toggled: string): void => {
        this.setState(() => ({
            [toggled]: !this.state[toggled],
        }));
    };

    // Whether an AuthRow modal is open
    toggleModalOpen = (modalOpen: boolean): void => {
        this.setState({ modalOpen });
    };

    determineConfigType = (addModalType: string): string => {
        const { primaryProviders } = this.props;
        const authInfo = primaryProviders[addModalType];

        if (authInfo) {
            return authInfo.sso ? 'ssoConfigurations' : 'formConfigurations';
        }

        return 'secondaryConfigurations';
    };

    render() {
        const {
            primaryProviders,
            secondaryProviders,
            ssoConfigurations,
            formConfigurations,
            secondaryConfigurations,
            canEdit,
            actions,
            helpLink,
        } = this.props;

        const { primaryModalOpen, secondaryModalOpen, addModalType } = this.state;

        const addNewPrimaryDropdown = Object.keys(primaryProviders).map(authOption => (
            <MenuItem
                key={authOption}
                onClick={() => this.setState({ primaryModalOpen: true, addModalType: authOption })}
            >
                {authOption} : {primaryProviders[authOption].description}
            </MenuItem>
        ));

        const addNewSecondaryDropdown = Object.keys(secondaryProviders).map(authOption => (
            <MenuItem
                key={authOption}
                onClick={() => this.setState({ secondaryModalOpen: true, addModalType: authOption })}
                disabled={!secondaryProviders[authOption].allowInsert}
            >
                {authOption} : {secondaryProviders[authOption].description}
                {!secondaryProviders[authOption].allowInsert && <i className="fa fa-question-circle" />}
            </MenuItem>
        ));

        const dbAuth = formConfigurations.slice(-1)[0];
        const isDragDisabled = this.state.modalOpen;

        const primaryTabLoginForm = canEdit ? (
            <div>
                <DragAndDropPane
                    configType="formConfigurations"
                    authConfigs={formConfigurations.slice(0, -1)} // Database config is excluded from DragAndDrop
                    providers={primaryProviders}
                    isDragDisabled={isDragDisabled}
                    actions={{ ...actions, toggleModalOpen: this.toggleModalOpen }}
                    canEdit={canEdit}
                />

                <AuthRow
                    draggable={false}
                    toggleModalOpen={this.toggleModalOpen}
                    authConfig={dbAuth}
                    canEdit={canEdit}
                />
            </div>
        ) : (
            <ViewOnlyAuthConfigRows data={formConfigurations} providers={primaryProviders} />
        );

        const primaryTabBody = canEdit ? (
            <DragAndDropPane
                configType="ssoConfigurations"
                authConfigs={ssoConfigurations}
                providers={primaryProviders}
                isDragDisabled={isDragDisabled}
                actions={{ ...actions, toggleModalOpen: this.toggleModalOpen }}
                canEdit={canEdit}
            />
        ) : (
            <ViewOnlyAuthConfigRows data={ssoConfigurations} providers={primaryProviders} />
        );

        const secondaryTabBody = canEdit ? (
            <DragAndDropPane
                configType="secondaryConfigurations"
                authConfigs={secondaryConfigurations}
                providers={secondaryProviders}
                isDragDisabled={isDragDisabled}
                actions={{ ...actions, toggleModalOpen: this.toggleModalOpen }}
                canEdit={canEdit}
            />
        ) : (
            <ViewOnlyAuthConfigRows data={secondaryConfigurations} providers={secondaryProviders} />
        );

        const authConfig = { description: addModalType + ' Configuration', enabled: true, provider: addModalType };
        const addNewModal = (primaryModalOpen || secondaryModalOpen) && (
            <DynamicConfigurationModal
                authConfig={authConfig}
                modalType={primaryModalOpen ? primaryProviders[addModalType] : secondaryProviders[addModalType]}
                configType={this.determineConfigType(addModalType)}
                canEdit={canEdit}
                title={'New ' + addModalType + ' Configuration'}
                updateAuthRowsAfterSave={actions.updateAuthRowsAfterSave}
                closeModal={() => {
                    this.onToggleModal(primaryModalOpen ? 'primaryModalOpen' : 'secondaryModalOpen');
                    this.toggleModalOpen(false);
                }}
            />
        );

        return (
            <div className="panel panel-default">
                <div className="panel-heading">Configurations</div>
                <div className="panel-body">
                    <a className="configurations__help-link" href={helpLink}>
                        Get help with authentication
                    </a>

                    {addNewModal}

                    <Tabs>
                        <Tab eventKey="primary" title="Primary">
                            <PrimaryTab
                                canEdit={canEdit}
                                menuItems={addNewPrimaryDropdown}
                                primaryTabLoginForm={primaryTabLoginForm}
                                body={primaryTabBody}
                            />
                        </Tab>

                        <Tab eventKey="secondary" title="Secondary">
                            <SecondaryTab
                                canEdit={canEdit}
                                menuItems={addNewSecondaryDropdown}
                                body={secondaryTabBody}
                            />
                        </Tab>
                    </Tabs>
                </div>
            </div>
        );
    }
}
