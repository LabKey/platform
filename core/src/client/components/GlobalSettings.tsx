import React, { PureComponent } from 'react';

import { Panel } from 'react-bootstrap';

import { LabelHelpTip } from '@labkey/components';

import FACheckBox from './FACheckBox';

const ROW_TEXTS = [
    {
        id: 'SelfRegistration',
        text: 'Allow self sign up',
        tip: 'Users are able to register for accounts when using database authentication. Use caution when enabling this if you have enabled sending email to non-users.',
    },
    {
        id: 'SelfServiceEmailChanges',
        text: 'Allow users to edit their own email addresses',
        tip: 'Users can change their own email address if their password is managed by LabKey Server.',
    },
    {
        id: 'AutoCreateAccounts',
        text: 'Auto-create authenticated users',
        tip: 'Accounts are created automatically when new users authenticate via LDAP or SSO.',
    },
];

interface Props {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean;

    canEdit: boolean;
    checkGlobalAuthBox: (id: string) => void;
    authCount: number;
}

export default class GlobalSettings extends PureComponent<Props, Props> {
    constructor(props) {
        super(props);
        this.state = {
            ...this.props,
        };
    }

    render() {
        let rowTexts = ROW_TEXTS;
        const { canEdit, authCount, checkGlobalAuthBox } = this.props;

        // If there are no user-created auth configs, there is no need to show the auto-create users checkbox
        if (authCount == 1) {
            rowTexts = ROW_TEXTS.slice(0, -1);
        }

        const rowTextComponents = rowTexts.map(text => (
            <div className="global-settings__text-row" key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={this.props[text.id]}
                    canEdit={canEdit}
                    onClick={() => checkGlobalAuthBox(text.id)}
                />

                <span className="global-settings__text">
                    {text.text}
                    <LabelHelpTip
                        title="Tip"
                        body={() => {
                            return <div> {text.tip} </div>;
                        }}
                    />
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
