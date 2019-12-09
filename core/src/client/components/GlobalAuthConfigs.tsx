import * as React from 'react'

import { Panel, FormControl, Button } from 'react-bootstrap'
import { Ajax, ActionURL } from '@labkey/api'

import CheckBoxWithText from './CheckBoxWithText';


// Todo:
// Interface
// use Immutable in handleCheckbox
// move render const into a const folder?
// might be a better way to do the rowTexts thing you're doing
// hook up default email domain
interface Props {
    checkGlobalAuthBox: any
    autoCreateAuthUsers: boolean
    selfSignUp: boolean
    userEmailEdit: boolean
}
interface State {
    selfSignUpCheckBox: boolean
    userEmailEditCheckbox: boolean
    autoCreateAuthUsersCheckbox: boolean
    defaultEmailDomainTextField: ""
    what: any
}
export default class GlobalAuthConfigs extends React.Component<any, State>{
    constructor(props) {
        super(props);
        this.state = {

            selfSignUpCheckBox: false,
            userEmailEditCheckbox: false,
            autoCreateAuthUsersCheckbox: false,
            defaultEmailDomainTextField: "",

            what: this.props.autoCreateAuthUsers,
        };
        // this.handleChange = this.handleChange.bind(this);
        this.saveGlobalAuthConfigs = this.saveGlobalAuthConfigs.bind(this);
    }

    // handleChange(event) {
    //     let {value} = event.target;
    //     this.setState(prevState => ({
    //         ...prevState,
    //         defaultEmailDomainTextField: value
    //     }))
    // }

    saveGlobalAuthConfigs(parameter, enabled){
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"),
            method : 'POST',
            params: {parameter: parameter, enabled:enabled},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    }

    render() {
        const rowTexts = [
            {id: "SelfRegistration", text: "Allow self sign up"},
            {id: "SelfServiceEmailChanges", text: "Allow users to edit their own email addresses"},
            {id: "AutoCreateAccounts", text: "Auto-create authenticated users"}];

        return(
            <Panel>
                <Panel.Heading>
                    <strong>Global Authentication Configurations</strong>
                </Panel.Heading>

                <Panel.Body>

                <strong> Sign up and email options</strong>

                <br/><br/>

                {rowTexts.map((text) => (
                    <CheckBoxWithText
                    key={text.id}
                    rowText={text.text}
                    checked={this.props[text.id]}
                    onClick={() => {this.props.checkGlobalAuthBox(text.id)}}
                    />
                ))}

                {/*<div className={"form-inline globalAuthConfig-leftMargin"}>*/}
                {/*    Default email domain:*/}
                {/*    <FormControl*/}
                {/*        className={"globalAuthConfig-textInput globalAuthConfig-leftMargin"}*/}
                {/*        type="text"*/}
                {/*        value={this.state.defaultEmailDomainTextField}*/}
                {/*        placeholder="Enter text"*/}
                {/*        onChange={(e) => this.handleChange(e)}*/}
                {/*        style ={{borderRadius: "5px"}}*/}
                {/*    />*/}
                {/*</div>*/}

                {/* For testing, to delete */}
                <Button style={{marginLeft: "2em"}} className={'labkey-button'} onClick={() => {() => {console.log(this.props)}}}>
                    Save and Finish
                </Button>
                <br/>

                </Panel.Body>
        </Panel>
        )
    }
}