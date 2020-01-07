import React, { PureComponent } from 'react';

import { Panel, DropdownButton, MenuItem, Tab, Tabs} from 'react-bootstrap';

import {FontAwesomeIcon} from '@fortawesome/react-fontawesome';
import {faInfoCircle, faPencilAlt} from '@fortawesome/free-solid-svg-icons';

import { LabelHelpTip } from '@labkey/components';

import DragAndDropPane from './DragAndDropPane';
import EditableAuthRow from './EditableAuthRow';
import SimpleAuthRow from './SimpleAuthRow';
import AuthRow from './AuthRow';
import DynamicConfigurationModal from "./DynamicConfigurationModal";

// todo:
// add new configurations is not in order
// lol fix the 'get help with auth' href
// put in loading wheel

interface loginFormAuthObject {
    deleteUrl: string
    description: string
    enabled: boolean
    id: string
    name: string
    url: string
}
interface State {
    modalOpen: boolean
}
interface Props {
    primary: Object
    ssoConfigurations: Array<Object>
    loginFormAuth: Array<loginFormAuthObject>
    secondary: any
    onDragEnd: any
    canEdit: boolean
}

export default class AuthConfigMasterPanel extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            useWhichTab: 'Primary',
            primaryModalOpen: false,
            secondaryModalOpen: false,
            addModalType: null,
        };
    }

    useWhichTab = (key) => {
        const primaryOrSecondary = ((key == 1) ? 'Primary' : 'Secondary');
        this.setState({ useWhichTab: primaryOrSecondary })
    };

    onToggleModal = (toggled) => {
        this.setState(() => ({
            [toggled]: !this.state[toggled]
        }));
    };

    determineStateSection = (addModalType) => {
        let authType;
        if (addModalType in this.props.primaryProviders) {
            let authInfo = this.props.primaryProviders[addModalType];

            if (authInfo.sso) {
                authType = "ssoConfigurations";
            } else {
                authType = "formConfigurations";
            }
        } else {
            authType = "secondaryConfigurations";
        }
        return authType;
    };

    render(){
        const {primaryProviders, secondaryProviders, ssoConfigurations, formConfigurations, secondaryConfigurations} = this.props;

        const SSOTipText = 'These configurations let LabKey users authenticate against an external service such as a SAML identify provider or a CAS server. LabKey will render SSO logos in the header and on the login page in the order that the configurations are listed below.';
        const loginFormTipText = "These configurations make use of LabKey's login page to collect credentials and authenticate against either hashed credentials stored in the LabKey database or external LDAP servers. LabKey will attempt authenticating against each configuration in the order they are listed below.";
        const authenticationDocsLink = 'https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule'; // TODO: replace this with "helpLink" coming from initialMount props

        const addNewPrimaryDropdown = primaryProviders &&
            Object.keys(primaryProviders).map((authOption) => (
                <MenuItem key={authOption} onClick={() => this.setState({primaryModalOpen: true, addModalType:authOption})}>
                    {authOption} : {primaryProviders[authOption].description}
                </MenuItem>
            ));

        const addNewSecondaryDropdown = secondaryProviders &&
            Object.keys(secondaryProviders).map((authOption) => (
                <MenuItem key={authOption} onClick={() => this.setState({secondaryModalOpen: true, addModalType:authOption})}>
                    {authOption} : {secondaryProviders[authOption].description}
                </MenuItem>
            ));


        const dbAuth = (formConfigurations && formConfigurations.slice(-1)[0]);

        const primaryTab_LoginForm =
            (formConfigurations && this.props.canEdit)
                ?
                <div>
                    <DragAndDropPane
                        stateSection='formConfigurations'
                        rowInfo={formConfigurations.slice(0, -1)}
                        primaryProviders={primaryProviders}
                        isDragDisabled={this.props.isDragDisabled}
                        onDragEnd={this.props.onDragEnd}
                        deleteAction={this.props.deleteAction}
                        updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                        toggleSomeModalOpen={this.props.toggleSomeModalOpen}
                    />

                    <AuthRow
                        canEdit={true}
                        draggable={false}
                        field1={dbAuth.description}
                        field3={dbAuth.provider}
                        enabled={dbAuth.enabled}
                    />
                </div>
                : <ViewOnlyAuthConfigRows data={this.props.formConfigurations}/>;

        const primaryTab_SSO =
            (ssoConfigurations && this.props.canEdit)
                ? <DragAndDropPane
                    stateSection='ssoConfigurations'
                    rowInfo={ssoConfigurations}
                    primaryProviders={primaryProviders}
                    isDragDisabled={this.props.isDragDisabled}
                    onDragEnd={this.props.onDragEnd}
                    deleteAction={this.props.deleteAction}
                    updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                    toggleSomeModalOpen={this.props.toggleSomeModalOpen}
                />
                : <ViewOnlyAuthConfigRows data={ssoConfigurations}/>;


        const secondaryTab =
            (secondaryConfigurations && this.props.canEdit)
                ? <DragAndDropPane
                    stateSection='secondaryConfigurations'
                    rowInfo={secondaryConfigurations}
                    // primaryProviders={primaryProviders}
                    secondaryProviders={secondaryProviders}
                    isDragDisabled={this.props.isDragDisabled}
                    onDragEnd={this.props.onDragEnd}
                    deleteAction={this.props.deleteAction}
                    updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                    toggleSomeModalOpen={this.props.toggleSomeModalOpen}
                />
                : <ViewOnlyAuthConfigRows data={secondaryConfigurations}/>;

        return(
            <Panel>
                <Panel.Heading> <span className='boldText'> Configurations </span> </Panel.Heading>
                <Panel.Body>
                    <a style={{float: 'right'}} href={authenticationDocsLink} > Get help with authentication </a>

                    { (this.state.useWhichTab == 'Primary') ?
                        <>
                            <span className='boldText'> Login Form Configurations </span>
                            <LabelHelpTip title={'Tip'} body={() => {
                                return (<div> {loginFormTipText} </div>)
                            }}/>
                        </>
                        : <span className='boldText'> Secondary Configurations </span>
                    }
                    { (this.state.primaryModalOpen || this.state.secondaryModalOpen) &&
                        <DynamicConfigurationModal
                            modalType={this.state.primaryModalOpen
                                ? this.props.primaryProviders[this.state.addModalType]
                                : this.props.secondaryProviders[this.state.addModalType]
                            }
                            stateSection={this.determineStateSection(this.state.addModalType)}
                            description={this.state.addModalType + " Configuration"}
                            enabled={true}
                            title={"New " + this.state.addModalType + " Authentication"}
                            updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                            closeModal={() => {
                                this.onToggleModal(this.state.primaryModalOpen ? "primaryModalOpen" : "secondaryModalOpen");
                                this.props.toggleSomeModalOpen(false);
                            }}
                        />
                    }

                    <br/><br/>

                    <Tabs defaultActiveKey={1} id='tab-panel' onSelect={(key) => {this.useWhichTab(key)}}>
                        <Tab eventKey={1} title='Primary' >
                            <div className='auth-tab'>
                                <DropdownButton id='dropdown-basic-button' title={'Add New Primary Configuration'}>
                                    {this.props.canEdit && addNewPrimaryDropdown}
                                </DropdownButton>

                                <br/><br/>

                                {primaryTab_LoginForm}
                            </div>

                            <div>
                                <hr/>

                                <span className='boldText'> Single Sign On Configurations </span>

                                <LabelHelpTip title={'Tip'} body={() => {
                                    return (<div> {SSOTipText} </div>)
                                }}/> <br/><br/>

                                {primaryTab_SSO}
                            </div>
                        </Tab>
                        <Tab eventKey={2} title='Secondary'>

                            <div className={'auth-tab'}>
                                <DropdownButton id='dropdown-basic-button' title={'Add New Secondary Configuration'}>
                                    {this.props.canEdit && addNewSecondaryDropdown}
                                </DropdownButton>

                                <br/><br/>

                                {secondaryTab}
                            </div>

                        </Tab>
                    </Tabs>
                </Panel.Body>
            </Panel>
        )
    }
}

class ViewOnlyAuthConfigRows extends PureComponent<any, any> {
    render(){
        const data = this.props.data;
        const moreInfoIcon = <FontAwesomeIcon size='1x' icon={faInfoCircle}/>;

        return(
            <div>
                {data && data.map((item) => (
                    <SimpleAuthRow
                        description={item.description}
                        name={item.provider}
                        enabled={item.enabled ? 'Enabled' : 'Disabled'}
                        editIcon={moreInfoIcon}
                    />
                ))}
            </div>
        );
    }
}
