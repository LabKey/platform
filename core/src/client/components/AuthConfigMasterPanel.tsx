import React, { PureComponent } from 'react';

import { Panel, DropdownButton, MenuItem, Tab, Tabs } from 'react-bootstrap';

import { LabelHelpTip } from '@labkey/components';

import DragAndDropPane from './DragAndDropPane';
import AuthRow from './AuthRow';
import DynamicConfigurationModal from './DynamicConfigurationModal';
import {LOGIN_FORM_TIP_TEXT, SSO_TIP_TEXT} from "../AuthenticationConfiguration/constants";

interface ViewOnlyAuthConfigRowsProps {
    data: AuthConfig[];
    providers?: AuthConfigProvider[];
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
                        modalType={{ ...providers[item.provider] }} // RP TODO
                    />
                ))}
            </div>
        );
    }
}

interface PrimaryTabProps {
    canEdit: boolean;
    addNewPrimaryDropdown: JSX.Element[];
    primaryTabLoginForm: JSX.Element;
    primaryTabSSO: JSX.Element;
}

class PrimaryTab extends PureComponent<PrimaryTabProps> {
    render() {
        const {
            addNewPrimaryDropdown,
            primaryTabLoginForm,
            primaryTabSSO,
            canEdit
        } = this.props;

        return(
            <>
                <div className="configurations__auth-tab">
                    {canEdit && (
                        <div className="configurations__dropdown">
                            <DropdownButton
                                id="primary-configurations-dropdown"
                                title="Add New Primary Configuration"
                            >
                                {addNewPrimaryDropdown}
                            </DropdownButton>
                        </div>
                    )}

                    <div className="configurations__config-section-title">
                        <span className="bold-text"> Login Form Configurations </span>
                        <LabelHelpTip
                            title="Tip"
                            body={() => {
                                return <div> {LOGIN_FORM_TIP_TEXT} </div>;
                            }}
                        />
                    </div>

                    {primaryTabLoginForm}
                </div>

                <div>
                    <div className="configurations__config-section-title">
                        <span className="bold-text"> Single Sign On Configurations </span>
                        <LabelHelpTip
                            title="Tip"
                            body={() => {
                                return <div> {SSO_TIP_TEXT} </div>;
                            }}
                        />
                    </div>

                    {primaryTabSSO}
                </div>
            </>
        );
    }
}

interface SecondaryTabProps {
    canEdit: boolean;
    addNewSecondaryDropdown: JSX.Element[];
    secondaryTabDuo: JSX.Element;
}

class SecondaryTab extends PureComponent<SecondaryTabProps> {
    render() {
        const {canEdit, addNewSecondaryDropdown, secondaryTabDuo} = this.props;

        return(
            <div className="configurations__auth-tab">
                {canEdit && (
                    <div className="configurations__dropdown">
                        <DropdownButton
                            id="secondary-configurations-dropdown"
                            title="Add New Secondary Configuration"
                        >
                            {addNewSecondaryDropdown}
                        </DropdownButton>
                    </div>
                )}

                {secondaryTabDuo}
            </div>
        );
    }
}

interface Props {
    formConfigurations: AuthConfig[];
    ssoConfigurations: AuthConfig[];
    secondaryConfigurations: AuthConfig[];
    primaryProviders: AuthConfigProvider[];
    secondaryProviders: AuthConfigProvider[];
    helpLink: string;
    canEdit: boolean;
    isDragDisabled: boolean;
    actions: Actions;
}

interface State {
    primaryModalOpen?: boolean;
    secondaryModalOpen?: boolean;
    addModalType?: string | null;
}

export default class AuthConfigMasterPanel extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            primaryModalOpen: false,
            secondaryModalOpen: false,
            addModalType: null,
        };
    }

    onToggleModal = (toggled: string): void => {
        this.setState(() => ({
            [toggled]: !this.state[toggled],
        }));
    };

    determineConfigType = (addModalType: string): string => {
        const {primaryProviders} = this.props;
        let authType;
        if (addModalType in primaryProviders) {
            const authInfo = primaryProviders[addModalType];

            if (authInfo.sso) {
                authType = 'ssoConfigurations';
            } else {
                authType = 'formConfigurations';
            }
        } else {
            authType = 'secondaryConfigurations';
        }
        return authType;
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
            helpLink
        } = this.props;

        const {primaryModalOpen, secondaryModalOpen, addModalType} = this.state;

        const authenticationDocsLink = helpLink;

        const addNewPrimaryDropdown =
            Object.keys(primaryProviders).map(authOption => (
                <MenuItem
                    key={authOption}
                    onClick={() => this.setState({ primaryModalOpen: true, addModalType: authOption })}>
                    {authOption} : {primaryProviders[authOption].description}
                </MenuItem>
            ));

        const addNewSecondaryDropdown =
            Object.keys(secondaryProviders).map(authOption => (
                <MenuItem
                    key={authOption}
                    onClick={() => this.setState({ secondaryModalOpen: true, addModalType: authOption })}>
                    {authOption} : {secondaryProviders[authOption].description}
                </MenuItem>
            ));

        const dbAuth = formConfigurations.slice(-1)[0];

        const isDragDisabled1 = primaryModalOpen || secondaryModalOpen;
        // console.log(primaryModalOpen ? "true1" : "false1");
        // console.log(secondaryModalOpen ? "true2" : "false2");

        const isDragDisabled = this.props.isDragDisabled;
        const primaryTabLoginForm =
            canEdit ? (
                <div>
                    <DragAndDropPane
                        configType="formConfigurations"
                        authConfigs={formConfigurations.slice(0, -1)} // Database config is excluded from DragAndDrop
                        providers={primaryProviders}
                        isDragDisabled={isDragDisabled}
                        actions={actions}
                        canEdit={canEdit}
                    />

                    <AuthRow
                        draggable={false}
                        toggleModalOpen={actions.toggleModalOpen}
                        canEdit={canEdit}
                        authConfig={{description: dbAuth.description, provider: dbAuth.provider, enabled: dbAuth.enabled}}
                    />
                </div>
            ) : (
                <ViewOnlyAuthConfigRows data={formConfigurations} providers={primaryProviders} />
            );

        const primaryTabSSO =
            canEdit ? (
                <DragAndDropPane
                    configType="ssoConfigurations"
                    authConfigs={ssoConfigurations}
                    providers={primaryProviders}
                    isDragDisabled={isDragDisabled}
                    actions={actions}
                    canEdit={canEdit}
                />
            ) : (
                <ViewOnlyAuthConfigRows data={ssoConfigurations} providers={primaryProviders} />
            );

        const secondaryTabDuo =
            canEdit ? (
                <DragAndDropPane
                    configType="secondaryConfigurations"
                    authConfigs={secondaryConfigurations}
                    providers={secondaryProviders}
                    isDragDisabled={isDragDisabled}
                    actions={actions}
                    canEdit={canEdit}
                />
            ) : (
                <ViewOnlyAuthConfigRows data={secondaryConfigurations} providers={secondaryProviders} />
            );

        const DBAuthConfig = {description: addModalType + ' Configuration', enabled: true, provider: addModalType};
        const dataBaseModal = (primaryModalOpen || secondaryModalOpen) &&
                <DynamicConfigurationModal
                        authConfig={DBAuthConfig}
                        modalType={
                            primaryModalOpen
                                ? primaryProviders[addModalType]
                                : secondaryProviders[addModalType]
                        }
                        configType={this.determineConfigType(addModalType)}
                        canEdit={canEdit}
                        title={'New ' + addModalType + ' Configuration'}
                        updateAuthRowsAfterSave={actions.updateAuthRowsAfterSave}
                        closeModal={() => {
                            this.onToggleModal(
                                primaryModalOpen ? 'primaryModalOpen' : 'secondaryModalOpen'
                            );
                            actions.toggleModalOpen(false);
                        }}
                />;

        return (
            <Panel>
                <Panel.Heading>
                    <span className="bold-text"> Configurations </span>
                </Panel.Heading>
                <Panel.Body>
                    <a className="configurations__help-link" href={authenticationDocsLink}>
                        Get help with authentication
                    </a>

                    {dataBaseModal}

                    <Tabs defaultActiveKey={1} id="tab-panel">
                        <Tab eventKey={1} title="Primary">
                            <PrimaryTab
                                canEdit={canEdit}
                                addNewPrimaryDropdown={addNewPrimaryDropdown}
                                primaryTabLoginForm={primaryTabLoginForm}
                                primaryTabSSO={primaryTabSSO}
                            />
                        </Tab>

                        <Tab eventKey={2} title="Secondary">
                            <SecondaryTab
                                canEdit={canEdit}
                                addNewSecondaryDropdown={addNewSecondaryDropdown}
                                secondaryTabDuo={secondaryTabDuo}
                            />
                        </Tab>
                    </Tabs>
                </Panel.Body>
            </Panel>
        );
    }
}
