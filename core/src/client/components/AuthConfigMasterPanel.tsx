import React, { PureComponent } from 'react';

import { Panel, DropdownButton, MenuItem, Tab, Tabs} from 'react-bootstrap';

import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faInfoCircle} from "@fortawesome/free-solid-svg-icons";

import { LabelHelpTip } from '@labkey/components';

import DragAndDropPane from "./DragAndDropPane";
import EditableAuthRow from "./EditableAuthRow";
import SimpleAuthRow from "./SimpleAuthRow";

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
    addNewAuthWhichDropdown: string
}
interface Props {
    primary: Object
    singleSignOnAuth: Array<Object>
    loginFormAuth: Array<loginFormAuthObject>
    secondary: any
    onDragEnd: any
    handlePrimaryToggle: any
    canEdit: boolean
}

export default class AuthConfigMasterPanel extends PureComponent<any, State> {
    constructor(props) {
        super(props);
        this.state = {
            addNewAuthWhichDropdown: "Primary"
        };
    }

    addNewAuthWhichDropdown = (key) => {
        const addNewAuthWhichDropdown = ((key == 1) ? "Primary" : "Secondary");
        this.setState({ addNewAuthWhichDropdown })
    };

    render(){
        const primary = this.props.primary;
        const singleSignOnAuth = this.props.singleSignOnAuth;
        const loginFormAuth = this.props.loginFormAuth;
        const secondary = this.props.secondary;

        const SSOTipText = "Single Sign On Authentications (SSOs) allow the use of one set of login credentials that are authenticated by the third party service provider (e.g. Google or Github).";
        const loginFormTipText = "Authentications in this group make use of LabKey's login form. During login, LabKey will attempt validation in the order that the configurations below are listed.";
        const authenticationDocsLink = "https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836";

        const addNewPrimaryDropdown = primary &&
            Object.keys(primary).map((authOption) => (
                <MenuItem key={authOption} href={primary[authOption].configLink}>
                    {authOption} : {primary[authOption].description}
                </MenuItem>
            ));

        const primaryAuthTab =
            (loginFormAuth && this.props.canEdit)
                ?
                <div>
                    <DragAndDropPane
                        className={"auth-tab"}
                        rowInfo={loginFormAuth.slice(0, -1)}
                        onDragEnd={this.props.onDragEnd}
                        handlePrimaryToggle={this.props.handlePrimaryToggle}
                        stateSection="loginFormAuth"
                        deleteAction={this.props.deleteAction}
                    />

                    <SimpleAuthRow
                        handle={null}
                        description={loginFormAuth.slice(-1)[0].description}
                        name={loginFormAuth.slice(-1)[0].name}
                        enabled={(loginFormAuth.slice(-1)[0].enabled) ? "Enabled" : "Disabled"}
                        // url={(loginFormAuth.slice(-1)[0].url)}
                    />
                </div>
                : <ViewOnlyAuthConfigRows data={this.props.loginFormAuth}/>;

        const secondaryAuthTab =
            (secondary && this.props.canEdit)
                ?
                secondary.map((item, index) => (
                    <EditableAuthRow
                        id={index.toString()}
                        rowId={index.toString()}
                        authName={""}
                        url={item.description.slice(0,34) + "..."} // fix this guy up
                        enabled={item.enabled}
                        description={item.name}
                        handlePrimaryToggle={this.props.handlePrimaryToggle}
                        stateSection="secondaryAuth"
                        noHandleIcon={true}
                        key={index}
                    />
                ))
                : <ViewOnlyAuthConfigRows data={secondary}/>;

        return(
            <Panel>
                <Panel.Heading> <span className="boldText">Authentication Configurations </span> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title={"Add New " + this.state.addNewAuthWhichDropdown}>

                        {this.props.canEdit &&
                            ((this.state.addNewAuthWhichDropdown == "Primary")
                            ? addNewPrimaryDropdown
                            : "Secondary (in progress)")
                        }

                    </DropdownButton>

                    <a style={{float: "right"}} href={authenticationDocsLink} > Get help with authentication </a>

                    <hr/>

                    <span className="boldText"> Labkey Login Form Authentications </span>
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {loginFormTipText} </div>)
                    }}/>

                    <br/><br/>

                    <Tabs defaultActiveKey={1} id="tab-panel" onSelect={(key) => {this.addNewAuthWhichDropdown(key)}}>
                        <Tab eventKey={1} title="Primary" >
                            <div className="auth-tab">
                                {primaryAuthTab}
                            </div>

                            <hr/>
                            <span className="boldText"> Single Sign On Authentications </span>

                            <LabelHelpTip title={'Tip'} body={() => {
                                return (<div> {SSOTipText} </div>)
                            }}/>

                            <br/><br/>

                            {(singleSignOnAuth && this.props.canEdit)
                                ? <DragAndDropPane
                                            className={"auth-tab"}
                                            rowInfo={singleSignOnAuth}
                                            onDragEnd={this.props.onDragEnd}
                                            handlePrimaryToggle={this.props.handlePrimaryToggle}
                                            stateSection="singleSignOnAuth"
                                            deleteAction={this.props.deleteAction}
                                            primary={this.props.primary}
                                />
                                : <ViewOnlyAuthConfigRows data={singleSignOnAuth}/>
                            }

                        </Tab>
                        <Tab eventKey={2} title="Secondary">

                            <div className={"auth-tab"}>
                                {secondaryAuthTab}
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
                        name={item.name}
                        enabled={item.enabled ? "Enabled" : "Disabled"}
                        editIcon={moreInfoIcon}
                    />
                ))}
            </div>
        );
    }
}
