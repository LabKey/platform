import * as React from 'react'

import { Panel, DropdownButton, MenuItem, Tab, Tabs} from 'react-bootstrap'

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
// where is your hr?

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
    addNewPrimary: Object
    singleSignOnAuth: Array<Object>
    loginFormAuth: Array<loginFormAuthObject>
    secondary: any
    onDragEnd: any
    handleChangeToPrimary: any
    handlePrimaryToggle: any
    canEdit: boolean
}
export default class AuthConfigMasterPanel extends React.PureComponent<Props, State>{
    constructor(props) {
        super(props);
        this.state = {
            addNewAuthWhichDropdown: "Primary"
        };
        this.addNewAuthWhichDropdown = this.addNewAuthWhichDropdown.bind(this);
    }

    addNewAuthWhichDropdown(key){
        const whichAuthType = ((key == 1) ? "Primary" : "Secondary");
        this.setState({addNewAuthWhichDropdown: whichAuthType})
    }

    render(){
        let addNewPrimary = this.props.addNewPrimary;
        let singleSignOnAuth = this.props.singleSignOnAuth;
        let loginFormAuth = this.props.loginFormAuth;
        let secondary = this.props.secondary;

        return(
            <Panel>
                <Panel.Heading> <strong>Authentication Configurations </strong> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title={"Add New " + this.state.addNewAuthWhichDropdown}>

                        {this.props.canEdit &&
                            ((this.state.addNewAuthWhichDropdown == "Primary")
                            ? this.props.addNewPrimary &&
                                Object.keys(addNewPrimary).map((authOption) => (
                                    <MenuItem key={authOption} href={addNewPrimary[authOption].configLink}>
                                        {authOption} : {addNewPrimary[authOption].description}
                                    </MenuItem>
                                ))
                            : "Secondary (in progress)"
                        )}

                    </DropdownButton>

                    <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                    <hr/>

                    <strong> Labkey Login Form Authentications </strong>
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> Authentications in this group make use of LabKey's login form. During login, LabKey will attempt validation in the order that the configurations below are listed. </div>)
                    }}/>

                    <br/><br/>


                    <Tabs defaultActiveKey={1} id="tab-panel" onSelect={(key) => {this.addNewAuthWhichDropdown(key)}}>
                        <Tab eventKey={1} title="Primary" >
                            <div className={"auth-tab"}>
                                {(loginFormAuth && this.props.canEdit)
                                    ?
                                    <div>
                                        <DragAndDropPane
                                            className={"auth-tab"}
                                            rowInfo={loginFormAuth.slice(0, -1)}
                                            onDragEnd={this.props.onDragEnd}
                                            handleChangeToPrimary={this.props.handleChangeToPrimary}
                                            handlePrimaryToggle={this.props.handlePrimaryToggle}
                                            stateSection="loginFormAuth"
                                        />

                                        <SimpleAuthRow
                                            handle={null}
                                            description={loginFormAuth.slice(-1)[0].description}
                                            name={loginFormAuth.slice(-1)[0].name}
                                            enabled={(loginFormAuth.slice(-1)[0].enabled) ? "Enabled" : "Disabled"}
                                            // url={(loginFormAuth.slice(-1)[0].url)}
                                        />
                                    </div>

                                    : <ViewOnlyAuthConfigRows data={this.props.loginFormAuth}/>
                                }

                            </div>
                            <hr/>
                            <strong> Single Sign On Authentications </strong>

                            <LabelHelpTip title={'Tip'} body={() => {
                                return (<div> Single Sign On Authentications (SSOs) allow the use of one set of login credentials that are authenticated by the third party service provider (e.g. Google or Github). </div>)
                            }}/>

                            <br/><br/>

                            {(singleSignOnAuth && this.props.canEdit)
                                ? <DragAndDropPane
                                            className={"auth-tab"}
                                            rowInfo={singleSignOnAuth}
                                            onDragEnd={this.props.onDragEnd}
                                            handleChangeToPrimary={this.props.handleChangeToPrimary}
                                            handlePrimaryToggle={this.props.handlePrimaryToggle}
                                            stateSection="singleSignOnAuth"
                                    />
                                : <ViewOnlyAuthConfigRows data={singleSignOnAuth}/>
                            }

                        </Tab>
                        <Tab eventKey={2} title="Secondary">

                            <div className={"auth-tab"}>

                                {(secondary && this.props.canEdit)
                                    ?
                                    secondary.map((item, index) => (
                                        <EditableAuthRow
                                            id={index.toString()}
                                            rowId={index.toString()}
                                            authName={""}
                                            url={item.description.slice(0,34) + "..."}
                                            enabled={item.enabled}
                                            description={item.name}
                                            handleChangeToPrimary={this.props.handleChangeToPrimary}
                                            handlePrimaryToggle={this.props.handlePrimaryToggle}
                                            stateSection="secondaryAuth"
                                            noHandleIcon={true}
                                        />
                                    ))
                                    : <ViewOnlyAuthConfigRows data={secondary}/>
                                }

                            </div>

                        </Tab>
                    </Tabs>

                </Panel.Body>
            </Panel>
        )
    }
}

class ViewOnlyAuthConfigRows extends React.PureComponent<any, any>
{
    render(){
        let data = this.props.data;
        let moreInfoIcon = <FontAwesomeIcon size='1x' icon={faInfoCircle}/>;

        return(
            <div>

                {data && data.map((item) => (
                    <SimpleAuthRow
                        description={item.description}
                        name={item.name}
                        enabled={item.enabled ? "Enabled" : "Disabled"}
                        edit={moreInfoIcon}
                    />
                ))}

            </div>
        );
    }
}