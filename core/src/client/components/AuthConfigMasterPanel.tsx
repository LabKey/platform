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
    useWhichDropdown: string
    modalOpen: boolean
}
interface Props {
    primary: Object
    ssoConfigurations: Array<Object>
    loginFormAuth: Array<loginFormAuthObject>
    secondary: any
    onDragEnd: any
    handlePrimaryToggle: any
    canEdit: boolean
}

export default class AuthConfigMasterPanel extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            useWhichDropdown: 'Primary',
            primaryModalOpen: false,
            secondaryModalOpen: false,
            addModalType: null,
        };
    }

    useWhichDropdown = (key) => {
        const primaryOrSecondary = ((key == 1) ? 'Primary' : 'Secondary');
        this.setState({ useWhichDropdown: primaryOrSecondary })
    };

    onToggleModal = (toggled) => {
        this.setState(() => ({
            [toggled]: !this.state[toggled]
        }));
    };

    render(){
        const {primaryProviders, secondaryProviders, ssoConfigurations, formConfigurations, secondaryConfigurations} = this.props;

        const SSOTipText = 'Single Sign On Authentications (SSOs) allow the use of one set of login credentials that are authenticated by the third party service provider (e.g. Google or Github).';
        const loginFormTipText = "Authentications in this group make use of LabKey's login form. During login, LabKey will attempt validation in the order that the configurations below are listed.";
        const authenticationDocsLink = 'https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836';

        const addNewPrimaryDropdown = primaryProviders &&
            Object.keys(primaryProviders).map((authOption) => (
                <MenuItem key={authOption} onClick={() => this.setState({primaryModalOpen: true, addModalType:authOption})}>
                    {authOption} : {primaryProviders[authOption].description}
                </MenuItem>
            ));

        const addNewSecondaryDropdown = secondaryProviders &&
            Object.keys(secondaryProviders).map((authOption) => (
                <MenuItem key={authOption} onClick={() => this.setState({secondaryModalOpen: true, addModalType:authOption})}>
                    {secondaryProviders[authOption].description}
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
                        handlePrimaryToggle={this.props.handlePrimaryToggle}
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
                    handlePrimaryToggle={this.props.handlePrimaryToggle}
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
                    primaryProviders={primaryProviders}
                    isDragDisabled={this.props.isDragDisabled}
                    onDragEnd={this.props.onDragEnd}
                    handlePrimaryToggle={this.props.handlePrimaryToggle}
                    deleteAction={this.props.deleteAction}
                    updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                    toggleSomeModalOpen={this.props.toggleSomeModalOpen}
                />
                : <ViewOnlyAuthConfigRows data={secondaryConfigurations}/>;

        return(
            <Panel>
                <Panel.Heading> <span className='boldText'>Authentication Configurations </span> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id='dropdown-basic-button' title={'Add New ' + this.state.useWhichDropdown}>

                        {this.props.canEdit &&
                            ((this.state.useWhichDropdown == 'Primary')
                            ? addNewPrimaryDropdown
                            : addNewSecondaryDropdown)
                        }

                    </DropdownButton>

                    <a style={{float: 'right'}} href={authenticationDocsLink} > Get help with authentication </a>

                    <hr/>

                    { (this.state.useWhichDropdown == 'Primary') ?
                        <>
                            <span className='boldText'> Labkey Login Form Authentications </span>
                            <LabelHelpTip title={'Tip'} body={() => {
                                return (<div> {loginFormTipText} </div>)
                            }}/>
                        </>
                        : <span className='boldText'> Labkey Secondary Authentications </span>
                    }
                    { (this.state.primaryModalOpen || this.state.secondaryModalOpen) &&
                        <DynamicConfigurationModal
                            modalType={this.state.primaryModalOpen
                                ? this.props.primaryProviders[this.state.addModalType]
                                : this.props.secondaryProviders[this.state.addModalType]
                            }

                            title={"New " + this.state.addModalType + " Authentication"}
                            closeModal={() => {
                                this.onToggleModal("primaryModalOpen");
                                this.props.toggleSomeModalOpen(false);
                            }}
                        />
                    }


                    <br/><br/>

                    <Tabs defaultActiveKey={1} id='tab-panel' onSelect={(key) => {this.useWhichDropdown(key)}}>
                        <Tab eventKey={1} title='Primary' >
                            <div className='auth-tab'>
                                {primaryTab_LoginForm}
                            </div>

                            <div>
                                <hr/>

                                <span className='boldText'> Single Sign On Authentications </span>

                                <LabelHelpTip title={'Tip'} body={() => {
                                    return (<div> {SSOTipText} </div>)
                                }}/> <br/><br/>

                                {primaryTab_SSO}
                            </div>
                        </Tab>
                        <Tab eventKey={2} title='Secondary'>

                            <div className={'auth-tab'}>
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
