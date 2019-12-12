import * as React from 'react'

import { Panel } from 'react-bootstrap'
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
    // autoCreateAuthUsers: boolean
    selfSignUp: boolean
    userEmailEdit: boolean
}
interface State {
    SelfRegistration: boolean
    SelfServiceEmailChanges: boolean
    AutoCreateAccounts: boolean
    what: any
    // globalAuthConfigs: any
}
export default class GlobalAuthConfigs extends React.PureComponent<any, any>{
    constructor(props) {
        super(props);
        this.state = {
            ...this.props
        };
        this.saveGlobalAuthConfigs = this.saveGlobalAuthConfigs.bind(this);
        this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);
    }

    // todo: use immutable?
    checkGlobalAuthBox(id: string) {
        let oldState = this.state[id];
        this.setState(prevState => ({
            ...prevState,
            [id]: !oldState
        }), () => this.props.checkDirty(this.state, this.props));
    }

    saveGlobalAuthConfigs(parameter, enabled){
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"),
            method : 'POST',
            params: {parameter: parameter, enabled:enabled},
            scope: this,
            failure: function(error){
                console.log("fail: ", error); // fill out error
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
                        checked={this.state[text.id]}
                        onClick={(this.props.canEdit) ? (() => {this.checkGlobalAuthBox(text.id)}) : (() => {})} // empty function might be bad style, here?
                    />
                ))}

                {/* For testing, to delete */}
                {/*<Button*/}
                {/*    style={{marginLeft: "2em"}}*/}
                {/*    className={'labkey-button'}*/}
                {/*    onClick={() => {console.log(this.props)}}*/}
                {/*>*/}
                {/*    Save and Finish*/}
                {/*</Button>*/}
                {/*<br/>*/}

                </Panel.Body>
        </Panel>
        )
    }
}