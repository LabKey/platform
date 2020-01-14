import React, { PureComponent } from 'react';

import { Panel } from 'react-bootstrap';

import FACheckBox from './FACheckBox';
import {LabelHelpTip} from "@labkey/components";

let ROW_TEXTS = [
    {
        id: 'SelfRegistration',
        text: 'Allow self sign up',
        tip: "Users are able to register for accounts when using database authentication. Use caution when enabling this if you have enabled sending email to non-users.",
    },
    {
        id: 'SelfServiceEmailChanges',
        text: 'Allow users to edit their own email addresses',
        tip: "Users can change their own email address if their password is managed by LabKey Server.",
    },
    {
        id: 'AutoCreateAccounts',
        text: 'Auto-create authenticated users',
        tip: 'Accounts are created automatically when new users authenticate via LDAP or SSO.',
    }
];

interface Props {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean;

    canEdit?: boolean;
    checkGlobalAuthBox?: Function;
    authCount?: number;
}
interface State {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean | null;

    canEdit?: boolean;
    checkGlobalAuthBox?: Function;
    authCount?: number;
}

export default class GlobalSettings extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            ...this.props,
        };
    }

    render() {
        let rowTexts = ROW_TEXTS;
        if (this.props.authCount == 1){
            rowTexts = ROW_TEXTS.slice(0,-1);
        }
        const {canEdit} = this.props;

        const rowTextComponents = rowTexts.map(text => (
            <div className="global-settings__text-row" key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={this.props[text.id]}
                    canEdit={this.props.canEdit}
                    onClick={
                        this.props.canEdit
                            ? () => { this.props.checkGlobalAuthBox(text.id) }
                            : () => {}
                    }
                />

                <span className="global-settings__text">
                    {text.text}
                    <LabelHelpTip title={'Tip'} body={() => {
                        return (<div> {text.tip} </div>)
                    }}/>
                </span>
            </div>
        ));

        return (
            <Panel>
                <Panel.Heading>
                    <span className="bold-text">Global Settings</span>
                </Panel.Heading>

                <Panel.Body>
                    <div className="bold-text global-settings__title-text"> Sign up and email options</div>

                    {rowTextComponents}

                </Panel.Body>
            </Panel>
        );
    }
}
