import * as React from 'react'

import { Panel, FormControl } from 'react-bootstrap'
import { Ajax, ActionURL } from '@labkey/api'

import CheckBoxWithText from './CheckBoxWithText';


// Todo:
// Interface
// use Immutable in handleCheckbox
// move render const into a const folder?
// bubble up form elements into app component
// might be a better way to do the rowTexts thing you're doing
// hook up default email domain!
interface GACProps {
    preSaveConfigState: any
    currentConfigState: any
    what: any
}
export default class GlobalAuthConfigs extends React.Component<any, GACProps>{
    constructor(props) {
        super(props);
        this.state = {

            preSaveConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            },
            currentConfigState: {
                selfSignUpCheckBox: false,
                userEmailEditCheckbox: false,
                autoCreateAuthUsersCheckbox: false,
                defaultEmailDomainTextField: "",
            },


            what: this.props.autoCreateAuthUsers,
        };
        this.handleChange = this.handleChange.bind(this);
        this.saveGlobalAuthConfigs = this.saveGlobalAuthConfigs.bind(this);
        this.getPermissions = this.getPermissions.bind(this);
    }

    handleChange(event) {
        let {value} = event.target;
        let newState = {...this.state};
        newState.currentConfigState.defaultEmailDomainTextField = value;
        this.setState(newState);

        // let oldState1 = Immutable.Map(this.state);
        // let newState1 = oldState1.setIn(["currentConfigState", "defaultEmailDomainTextField"], value);
        // this.setState(newState1.toObject());
        // console.log(newState1.toObject());

        // this.setState(({currentConfigState}) => ({
        //     currentConfigState: currentConfigState.update()
        // }));
    }

    saveGlobalAuthConfigs(parameter, enabled){
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"), //generate this url
            method : 'POST',
            params: {parameter: "SelfRegistration", enabled:"true"},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    }

    getPermissions(){
        // let myContainer = Security.currentContainer;
        // // console.log("mycontainer: ", myContainer);
        // let info;
        //
        //
        // Security.getUserPermissions({
        //     success: (data) => { console.log(data)}
        // });
        console.log(this.props);
    }

    render() {
        const rowTexts = [
            {id: "selfSignUp", text: "Allow self sign up"},
            {id: "userEmailEdit", text: "Allow users to edit their own email addresses"},
            {id: "autoCreateAuthUsers", text: "Auto-create authenticated users"}];

        return(
            <Panel>
                <Panel.Heading>
                    <strong>Global Authentication Configurations</strong>
                </Panel.Heading>

                <Panel.Body>

                <strong> Sign up and email options</strong>

                <br/><br/>

                {rowTexts.map((text) =>
                        (<CheckBoxWithText
                            key={text.id}
                            rowText={text.text}
                            checked={this.props[text.id]}
                            onClick={() => {this.props.checkGlobalAuthBox(text.id)}}
                />))}

                <div className={"form-inline globalAuthConfig-leftMargin"}>
                    Default email domain:
                    <FormControl
                        className={"globalAuthConfig-textInput globalAuthConfig-leftMargin"}
                        type="text"
                        value={this.state.currentConfigState.defaultEmailDomainTextField}
                        placeholder="Enter text"
                        onChange={(e) => this.handleChange(e)}
                        style ={{borderRadius: "5px"}}
                    />
                </div>

                <br/>
                {/* For testing, to delete */}
                {/*<Button className={'labkey-button primary'} onClick={() => {this.getPermissions()}}>Save and Finish</Button>*/}

                </Panel.Body>
        </Panel>
        )
    }
}