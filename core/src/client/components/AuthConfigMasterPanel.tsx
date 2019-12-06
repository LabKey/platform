import * as React from 'react'

import { Panel, DropdownButton, MenuItem, Tab, Tabs} from 'react-bootstrap'

import { LabelHelpTip } from '@glass/base';
import DragAndDropPane from "./DragAndDropPane";
import DraggableAuthRow from "./DraggableAuthRow";
import SimpleAuthRow from "./SimpleAuthRow";





// todo:
// add new configurations is not in order
// lol fix the 'get help with auth' href
// put in loading wheel
// where is your hr?

interface Props {
    addNew: Object
    primary: Array<Object>
    primaryLDAP: Array<Object>
    secondary: any
    onDragEnd: any
    handleChangeToPrimary: any
    handlePrimaryToggle: any
}
export default class AuthConfigMasterPanel extends React.Component<Props>{
    render(){
        let addNew = this.props.addNew;
        let primary = this.props.primary;
        let primaryLDAP = this.props.primaryLDAP;
        let secondary = this.props.secondary;

        return(
            <Panel>
                <Panel.Heading> <strong>Authentication Configurations </strong> </Panel.Heading>
                <Panel.Body>
                    <DropdownButton id="dropdown-basic-button" title="Add New Primary">
                        {this.props.addNew &&
                        Object.keys(addNew).map((authOption) => (
                            <MenuItem key={authOption} href={addNew[authOption].configLink}>
                                {authOption} : {addNew[authOption].description}
                            </MenuItem>
                        ))}
                    </DropdownButton>

                    <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                    <hr/>

                    <strong> Labkey Login Form Authentications </strong>
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> Authentications in this group make use of LabKey's login form. During login, LabKey will attempt validation in the order that the configurations below are listed. </div>)
                    }}/>

                    <br/><br/>

                    <Tabs defaultActiveKey={1} id="uncontrolled-tab-example">
                        <Tab eventKey={1} title="Primary">
                            <div className={"auth-tab"}>
                                {primary &&
                                    <DragAndDropPane
                                            className={"auth-tab"}
                                            rowInfo={primary}
                                            onDragEnd={this.props.onDragEnd}
                                            handleChangeToPrimary={this.props.handleChangeToPrimary}
                                            handlePrimaryToggle={this.props.handlePrimaryToggle}
                                />}
                            </div>

                            <hr/>

                            <strong> Single Sign On Authentications </strong>


                            <LabelHelpTip title={'test'} body={() => {
                                return (<div> Tip 2: text </div>)
                            }}/>

                            <br/><br/>

                            {primaryLDAP &&
                            <DragAndDropPane
                                    className={"auth-tab"}
                                    rowInfo={primaryLDAP}
                                    onDragEnd={this.props.onDragEnd}
                                    handleChangeToPrimary={this.props.handleChangeToPrimary}
                                    handlePrimaryToggle={this.props.handlePrimaryToggle}
                            />}

                        </Tab>
                        <Tab eventKey={2} title="Secondary">

                            <div className={"auth-tab"}>

                                {/*{secondary && <SimpleAuthRow*/}
                                {/*        handle={null}*/}
                                {/*        description={dataBaseConfig.description}*/}
                                {/*        name={dataBaseConfig.name}*/}
                                {/*        enabled={(dataBaseConfig.enabled) ? "Enabled" : "Disabled"}*/}
                                {/*/>}*/}

                                {secondary && secondary.map((item) => (
                                    <SimpleAuthRow
                                        handle={null}
                                        description={item.name}
                                        name={""}
                                        enabled={(item.enabled) ? "Enabled" : "Disabled"}
                                    />
                                ))}


                                {/*<DraggableAuthRow />*/}

                            </div>

                            <br/><br/><br/><br/><br/><br/><br/>

                        </Tab>
                    </Tabs>

                </Panel.Body>
            </Panel>
        )
    }
}