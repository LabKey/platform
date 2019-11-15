import * as React from 'react'
import { Panel, Button, DropdownButton, MenuItem, Alert } from 'react-bootstrap'

type State = {
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props)
    }

    cancelChanges = () => {
        console.log("to do")
    };

    render() {
        // dummy variables
        let authOptions = [
            {name: "CAS", description:"cas description", id:1},
            {name: "LDAP", description:"ldap description", id:2},
            {name:"SAML", description:"saml description", id:3}];


        return(
            <div>

                <Panel>
                    <Panel.Heading> <strong>Authentication Configurations</strong> </Panel.Heading>
                    <Panel.Body>
                        <DropdownButton id="dropdown-basic-button" title="Add New">
                            {authOptions.map((authOption) => (
                                <MenuItem key={authOption.id}> {authOption.name} : {authOption.description} </MenuItem>
                            ))}
                        </DropdownButton>

                        <a style={{float: "right"}} href={"https://www.labkey.org/Documentation/wiki-page.view?name=authenticationModule&_docid=wiki%3A32d70b80-ed56-1034-b734-fe851e088836"} > Get help with authentication </a>

                        <br></br><br></br><br></br><br></br><br></br>

                        Big grid here!

                        <br></br><br></br><br></br><br></br><br></br>

                        <hr></hr>

                        {/* New component below */}
                        <strong> Selected Provider Configuration Settings </strong>

                        <div style={{float: "right"}}>
                            <Button className={'labkey-button'} onClick={this.cancelChanges}>Configure</Button>

                            <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Delete</Button>
                        </div>

                    </Panel.Body>
                </Panel>

                <Alert>You have unsaved changes.</Alert>

                <Button className={'labkey-button primary'} onClick={this.cancelChanges}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>

            </div>
        );
    }

}
