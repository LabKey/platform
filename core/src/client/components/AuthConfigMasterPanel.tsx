import * as React from 'react'

import { Panel, DropdownButton, MenuItem, Tab, Tabs} from 'react-bootstrap'
import ReactBootstrapToggle from 'react-bootstrap-toggle';

import { LabelHelpTip } from '@glass/base';
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
}
export default class AuthConfigMasterPanel extends React.Component<Props, State>{
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



        // let primaryDropdownOptions =
        //     Object.keys(addNewPrimary).map((authOption) => (
        //         <MenuItem key={authOption} href={addNewPrimary[authOption].configLink}>
        //             {authOption} : {addNewPrimary[authOption].description}
        //         </MenuItem>
        //     ));

        // if (loginFormAuth){
        //     const primaryConfigsWithoutDatabase = loginFormAuth.slice(0, -1);
        //     const dataBaseConfig = loginFormAuth.slice(-1)[0];
        //
        //     console.log("loginFormAuth ", loginFormAuth);
        //     console.log("primaryConfigsWithoutDatabase ", primaryConfigsWithoutDatabase);
        // }

        return(
            <Panel>
                <Panel.Heading> <strong>Authentication Configurations </strong> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title={"Add New " + this.state.addNewAuthWhichDropdown}>

                        {((this.state.addNewAuthWhichDropdown == "Primary")
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
                                {loginFormAuth &&
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
                                }


                            </div>

                            <hr/>

                            <strong> Single Sign On Authentications </strong>


                            <LabelHelpTip title={'test'} body={() => {
                                return (<div> Tip 2: text </div>)
                            }}/>

                            <br/><br/>

                            {singleSignOnAuth &&
                                <DragAndDropPane
                                        className={"auth-tab"}
                                        rowInfo={singleSignOnAuth}
                                        onDragEnd={this.props.onDragEnd}
                                        handleChangeToPrimary={this.props.handleChangeToPrimary}
                                        handlePrimaryToggle={this.props.handlePrimaryToggle}
                                        stateSection="singleSignOnAuth"
                                />
                            }

                        </Tab>
                        <Tab eventKey={2} title="Secondary">

                            <div className={"auth-tab"}>

                                {/*{secondary && <SimpleAuthRow*/}
                                {/*        handle={null}*/}
                                {/*        description={dataBaseConfig.description}*/}
                                {/*        name={dataBaseConfig.name}*/}
                                {/*        enabled={(dataBaseConfig.enabled) ? "Enabled" : "Disabled"}*/}
                                {/*/>}*/}

                                {/*<SimpleAuthRow*/}
                                {/*    handle={null}*/}
                                {/*    description={item.name}*/}
                                {/*    url={item.description}*/}
                                {/*    name={""}*/}
                                {/*    enabled={(item.enabled) ? "Enabled" : "Disabled"}*/}
                                {/*/>*/}

                                {secondary && secondary.map((item, index) => (

                                    <EditableAuthRow
                                        id={index.toString()}
                                        rowId={index.toString()}
                                        authName={""}
                                        url={item.description.slice(0,50) + "..."}
                                        enabled={item.enabled}
                                        description={item.name}
                                        handleChangeToPrimary={this.props.handleChangeToPrimary}
                                        handlePrimaryToggle={this.props.handlePrimaryToggle}
                                        stateSection="secondaryAuth"
                                        noHandleIcon={true}
                                    />
                                ))}


                                {/*<DraggableAuthRow />*/}

                            </div>

                        </Tab>
                    </Tabs>

                </Panel.Body>
            </Panel>
        )
    }
}