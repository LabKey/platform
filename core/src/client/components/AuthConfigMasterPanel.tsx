import React, { PureComponent } from 'react';

import { Panel, DropdownButton, MenuItem, Tab, Tabs } from 'react-bootstrap';

import { LabelHelpTip } from '@labkey/components';

import DragAndDropPane from './DragAndDropPane';
import AuthRow from './AuthRow';
import DynamicConfigurationModal from './DynamicConfigurationModal';

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
                        {...item}
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
    loginFormTipText: string;
    primaryTabLoginForm: JSX.Element;
    SSOTipText: string;
    primaryTabSSO: JSX.Element;
}

class PrimaryTab extends PureComponent<PrimaryTabProps> {
    render() {
        const {
            addNewPrimaryDropdown,
            loginFormTipText,
            primaryTabLoginForm,
            SSOTipText,
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
                                title="Add New Primary Configuration">
                                {addNewPrimaryDropdown}
                            </DropdownButton>
                        </div>
                    )}

                    <div className="configurations__config-section-title">
                        <span className="bold-text"> Login Form Configurations </span>
                        <LabelHelpTip
                            title="Tip"
                            body={() => {
                                return <div> {loginFormTipText} </div>;
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
                                return <div> {SSOTipText} </div>;
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
                            title="Add New Secondary Configuration">
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
    formConfigurations?: AuthConfig[];
    ssoConfigurations?: AuthConfig[];
    secondaryConfigurations?: AuthConfig[];
    primaryProviders?: AuthConfigProvider[];
    secondaryProviders?: AuthConfigProvider[];
    helpLink?: string;
    canEdit?: boolean;
    isDragDisabled?: boolean;
    actions?: Actions;
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
        let authType;
        if (addModalType in this.props.primaryProviders) {
            const authInfo = this.props.primaryProviders[addModalType];

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
        } = this.props;

        const {primaryModalOpen, secondaryModalOpen, addModalType} = this.state;

        const SSOTipText = 'These configurations let LabKey users authenticate against an external service such as a SAML identify provider or a CAS server. LabKey will render SSO logos in the header and on the login page in the order that the configurations are listed below.';
        const loginFormTipText = "These configurations make use of LabKey's login page to collect credentials and authenticate against either hashed credentials stored in the LabKey database or external LDAP servers. LabKey will attempt authenticating against each configuration in the order they are listed below.";
        const authenticationDocsLink = this.props.helpLink;

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
        console.log(primaryModalOpen ? "true1" : "false1");
        console.log(secondaryModalOpen ? "true2" : "false2");

        const isDragDisabled = this.props.isDragDisabled;
        const primaryTabLoginForm =
            canEdit ? (
                <div>
                    <DragAndDropPane
                        configType="formConfigurations"
                        authConfigs={formConfigurations.slice(0, -1)} // Database config is excluded from DragAndDrop
                        providers={primaryProviders}
                        canEdit={canEdit}
                        isDragDisabled={isDragDisabled}
                        actions={this.props.actions}
                    />

                    <AuthRow
                        canEdit={canEdit}
                        draggable={false}
                        description={dbAuth.description}
                        provider={dbAuth.provider}
                        enabled={dbAuth.enabled}
                        toggleModalOpen={this.props.actions.toggleModalOpen}
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
                    canEdit={canEdit}
                    isDragDisabled={isDragDisabled}
                    actions={this.props.actions}
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
                    canEdit={canEdit}
                    isDragDisabled={isDragDisabled}
                    actions={this.props.actions}
                />
            ) : (
                <ViewOnlyAuthConfigRows data={secondaryConfigurations} providers={secondaryProviders} />
            );

        const dataBaseModal = (primaryModalOpen || secondaryModalOpen) &&
                <DynamicConfigurationModal
                        modalType={
                            primaryModalOpen
                                ? primaryProviders[addModalType]
                                : secondaryProviders[addModalType]
                        }
                        configType={this.determineConfigType(addModalType)}
                        description={addModalType + ' Configuration'}
                        enabled={true}
                        canEdit={canEdit}
                        title={'New ' + addModalType + ' Configuration'}
                        provider={addModalType}
                        updateAuthRowsAfterSave={this.props.actions.updateAuthRowsAfterSave}
                        closeModal={() => {
                            this.onToggleModal(
                                primaryModalOpen ? 'primaryModalOpen' : 'secondaryModalOpen'
                            );
                            this.props.actions.toggleModalOpen(false);
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
                                loginFormTipText={loginFormTipText}
                                primaryTabLoginForm={primaryTabLoginForm}
                                SSOTipText={SSOTipText}
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
