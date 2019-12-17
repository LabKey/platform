import React, { PureComponent } from 'react';

import { Panel } from 'react-bootstrap';
import { Ajax, ActionURL } from '@labkey/api';

import FACheckBox from './FACheckBox';

const ROW_TEXTS = [
    {id: "SelfRegistration", text: "Allow self sign up"},
    {id: "SelfServiceEmailChanges", text: "Allow users to edit their own email addresses"},
    {id: "AutoCreateAccounts", text: "Auto-create authenticated users"},
];

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
    // globalAuthSettings: any
}

export default class GlobalAuthSettings extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            ...this.props
        };
    }

    // todo: use immutable?
    checkGlobalAuthBox = (id: string) => {
        const oldState = this.state[id];
        this.setState(() => ({
            [id]: !oldState
        }), () => this.props.checkDirty(this.state, this.props));
    };

    saveGlobalAuthConfigs = (parameter, enabled) => {
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
    };



    render() {
        const rowTexts = ROW_TEXTS.map((text) => (
            <div className={"bottom-margin"} key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={this.state[text.id]}
                    onClick={(this.props.canEdit) ? (() => {this.checkGlobalAuthBox(text.id)}) : (() => {})} // empty function might be bad style, here?
                />

                <span style={{marginLeft:"15px"}}>
                    {text.text}
                </span>
            </div>
        ));


        return(
            <Panel>
                <Panel.Heading>
                    <span className="boldText">Global Authentication Configurations</span>
                </Panel.Heading>

                <Panel.Body>

                <span className="boldText"> Sign up and email options</span>

                <br/><br/>

                {rowTexts}


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
