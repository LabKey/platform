import React, { PureComponent } from 'react';

import { Panel, DropdownButton, MenuItem, Tab, Tabs } from 'react-bootstrap';

import { LabelHelpTip } from '@labkey/components';

import DragAndDropPane from './DragAndDropPane';
import AuthRow from './AuthRow';
import DynamicConfigurationModal from './DynamicConfigurationModal';

interface Props {
    formConfigurations?: AuthConfig[];
    ssoConfigurations?: AuthConfig[];
    secondaryConfigurations?: AuthConfig[];
    primaryProviders?: Record<string, any>;
    secondaryProviders?: Record<string, any>;
    helpLink?: string;
    canEdit?: boolean;
    isDragDisabled?: boolean;
    actionFunctions?: { [key: string]: Function };
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

    determineStateSection = (addModalType: string): string => {
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

        const SSOTipText = 'These configurations let LabKey users authenticate against an external service such as a SAML identify provider or a CAS server. LabKey will render SSO logos in the header and on the login page in the order that the configurations are listed below.';
        const loginFormTipText = "These configurations make use of LabKey's login page to collect credentials and authenticate against either hashed credentials stored in the LabKey database or external LDAP servers. LabKey will attempt authenticating against each configuration in the order they are listed below.";
        const authenticationDocsLink = this.props.helpLink;

        const addNewPrimaryDropdown =
            primaryProviders &&
            Object.keys(primaryProviders).map(authOption => (
                <MenuItem
                    key={authOption}
                    onClick={() => this.setState({ primaryModalOpen: true, addModalType: authOption })}>
                    {authOption} : {primaryProviders[authOption].description}
                </MenuItem>
            ));

        const addNewSecondaryDropdown =
            secondaryProviders &&
            Object.keys(secondaryProviders).map(authOption => (
                <MenuItem
                    key={authOption}
                    onClick={() => this.setState({ secondaryModalOpen: true, addModalType: authOption })}>
                    {authOption} : {secondaryProviders[authOption].description}
                </MenuItem>
            ));

        const dbAuth = formConfigurations && formConfigurations.slice(-1)[0];

        const primaryTab_LoginForm =
            formConfigurations && canEdit ? (
                <div>
                    <DragAndDropPane
                        stateSection="formConfigurations"
                        rowInfo={formConfigurations.slice(0, -1)} // Database config is excluded from DragAndDrop
                        primaryProviders={primaryProviders}
                        canEdit={this.props.canEdit}
                        isDragDisabled={this.props.isDragDisabled}
                        actionFunctions={this.props.actionFunctions}
                    />

                    <AuthRow
                        canEdit={this.props.canEdit}
                        draggable={false}
                        description={dbAuth.description}
                        provider={dbAuth.provider}
                        enabled={dbAuth.enabled}
                        toggleSomeModalOpen={this.props.actionFunctions.toggleSomeModalOpen}
                    />
                </div>
            ) : (
                <ViewOnlyAuthConfigRows data={formConfigurations} primaryProviders={primaryProviders} />
            );

        const primaryTab_SSO =
            ssoConfigurations && canEdit ? (
                <DragAndDropPane
                    stateSection="ssoConfigurations"
                    rowInfo={ssoConfigurations}
                    primaryProviders={primaryProviders}
                    canEdit={this.props.canEdit}
                    isDragDisabled={this.props.isDragDisabled}
                    actionFunctions={this.props.actionFunctions}
                />
            ) : (
                <ViewOnlyAuthConfigRows data={ssoConfigurations} primaryProviders={primaryProviders} />
            );

        const secondaryTab =
            secondaryConfigurations && canEdit ? (
                <DragAndDropPane
                    stateSection="secondaryConfigurations"
                    rowInfo={secondaryConfigurations}
                    secondaryProviders={secondaryProviders}
                    canEdit={this.props.canEdit}
                    isDragDisabled={this.props.isDragDisabled}
                    actionFunctions={this.props.actionFunctions}
                />
            ) : (
                <ViewOnlyAuthConfigRows data={secondaryConfigurations} secondaryProviders={secondaryProviders} />
            );

        const { updateAuthRowsAfterSave, toggleSomeModalOpen } = this.props.actionFunctions;
        return (
            <Panel>
                <Panel.Heading>
                    <span className="bold-text"> Configurations </span>
                </Panel.Heading>
                <Panel.Body>
                    <a className="configurations__help-link" href={authenticationDocsLink}>
                        Get help with authentication
                    </a>

                    {(this.state.primaryModalOpen || this.state.secondaryModalOpen) && (
                        <DynamicConfigurationModal
                            modalType={
                                this.state.primaryModalOpen
                                    ? this.props.primaryProviders[this.state.addModalType]
                                    : this.props.secondaryProviders[this.state.addModalType]
                            }
                            stateSection={this.determineStateSection(this.state.addModalType)}
                            description={this.state.addModalType + ' Configuration'}
                            enabled={true}
                            canEdit={this.props.canEdit}
                            title={'New ' + this.state.addModalType + ' Authentication'}
                            provider={this.state.addModalType}
                            updateAuthRowsAfterSave={updateAuthRowsAfterSave}
                            closeModal={() => {
                                this.onToggleModal(
                                    this.state.primaryModalOpen ? 'primaryModalOpen' : 'secondaryModalOpen'
                                );
                                toggleSomeModalOpen(false);
                            }}
                        />
                    )}

                    <Tabs defaultActiveKey={1} id="tab-panel">
                        <Tab eventKey={1} title="Primary">
                            <div className="configurations__auth-tab">
                                {this.props.canEdit && (
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

                                {primaryTab_LoginForm}
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

                                {primaryTab_SSO}
                            </div>
                        </Tab>
                        <Tab eventKey={2} title="Secondary">
                            <div className="configurations__auth-tab">
                                {this.props.canEdit && (
                                    <div className="configurations__dropdown">
                                        <DropdownButton
                                            id="secondary-configurations-dropdown"
                                            title="Add New Secondary Configuration">
                                            {addNewSecondaryDropdown}
                                        </DropdownButton>
                                    </div>
                                )}

                                {secondaryTab}
                            </div>
                        </Tab>
                    </Tabs>
                </Panel.Body>
            </Panel>
        );
    }
}

interface ViewOnlyAuthConfigRows_Props {
    data?: AuthConfig[];
    primaryProviders?: Record<string, any>;
    secondaryProviders?: Record<string, any>;
}
class ViewOnlyAuthConfigRows extends PureComponent<ViewOnlyAuthConfigRows_Props> {
    render() {
        const { primaryProviders, secondaryProviders, data } = this.props;
        const providers = primaryProviders ? primaryProviders : secondaryProviders;

        return (
            <div>
                {data &&
                    data.map(item => (
                        <AuthRow
                            {...item}
                            key={item.configuration}
                            canEdit={false}
                            draggable={false}
                            modalType={providers && { ...providers[item.provider] }}
                        />
                    ))}
            </div>
        );
    }
}
