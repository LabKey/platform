import React, { PureComponent } from 'react';
import { Panel, FormControl } from 'react-bootstrap';
import { helpLinkNode, LabelHelpTip } from '@labkey/components';

import FACheckBox from './FACheckBox';
import { GlobalSettingsOptions } from './models';

const ROW_TEXTS = [
    {
        id: 'SelfRegistration',
        text: 'Allow self sign up',
        tip:
            'Users are able to register for accounts when using database authentication. Use caution when enabling this if you have enabled sending email to non-users.',
    },
    {
        id: 'SelfServiceEmailChanges',
        text: 'Allow users to edit their own email addresses',
        tip: 'Users can change their own email address if their password is managed by LabKey Server.',
    },
    {
        id: 'DisableHeaderAuthLinks',
        text: 'Do not show header links for SSO auth providers',
        tip: 'By default, each SSO will have a login link in the header area. If checked, these links will not be shown.',
    },
    {
        id: 'AutoCreateAccounts',
        text: 'Auto-create authenticated users',
        tip: 'Accounts are created automatically when new users authenticate via LDAP or SSO.',
    },
];

interface DefaultDomainProps {
    defaultDomain: string;
    globalAuthOnChange: (id: string, value: any) => void;
}

class DefaultDomainField extends PureComponent<DefaultDomainProps> {
    render() {
        const { globalAuthOnChange, defaultDomain } = this.props;

        return (
            <div className="global-settings__default-domain">
                System Default Domain
                <LabelHelpTip title="Tip">
                    <div>
                        <div> Default domain for user sign in.</div>
                        {helpLinkNode('authenticationModule#dom', 'More info')}
                    </div>
                </LabelHelpTip>
                <span className="global-settings__default-domain-field">
                    <FormControl
                        name="DefaultDomain"
                        type="input"
                        value={defaultDomain}
                        onChange={e => {
                            globalAuthOnChange('DefaultDomain', e.target.value);
                        }}
                        className="form-control global-settings__default-domain-form"
                        placeholder="System default domain"
                    />
                </span>
            </div>
        );
    }
}

interface Props {
    globalSettings: GlobalSettingsOptions;
    canEdit: boolean;
    globalAuthOnChange: (id: string, value: any) => void;
    authCount: number;
}

export default class GlobalSettings extends PureComponent<Props> {
    render() {
        let rowTexts = ROW_TEXTS;
        const { canEdit, authCount, globalAuthOnChange, globalSettings } = this.props;

        // If there are no user-created auth configs, there is no need to show the auto-create users checkbox
        if (authCount == 1) {
            rowTexts = ROW_TEXTS.slice(0, -1);
        }

        const rowTextComponents = rowTexts.map(text => (
            <div className="global-settings__text-row" key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={globalSettings[text.id]}
                    canEdit={canEdit}
                    onClick={() => globalAuthOnChange(text.id, !globalSettings[text.id])}
                />

                <span className="global-settings__text">
                    {text.text}
                    <LabelHelpTip title="Tip">
                        <div> {text.tip} </div>
                    </LabelHelpTip>
                </span>
            </div>
        ));

        return (
            <Panel>
                <Panel.Heading>
                    <span className="bold-text">Global Settings</span>
                </Panel.Heading>

                <Panel.Body>
                    {rowTextComponents}

                    <DefaultDomainField
                        defaultDomain={globalSettings?.DefaultDomain}
                        globalAuthOnChange={globalAuthOnChange}
                    />
                </Panel.Body>
            </Panel>
        );
    }
}
