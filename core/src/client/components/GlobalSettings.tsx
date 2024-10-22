import React, { ChangeEvent, FC, memo, useCallback } from 'react';
import { HelpLink, LabelHelpTip } from '@labkey/components';

import { GlobalSettingsOptions } from './models';

interface GlobalSettingFieldData {
    id: string;
    text: string;
    tip: string;
}

const FIELD_DATA: GlobalSettingFieldData[] = [
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

interface GlobalSettingProps extends GlobalSettingFieldData {
    canEdit: boolean;
    onChange: (id: string, value: boolean) => void;
    value: boolean;
}

const GlobalSetting: FC<GlobalSettingProps> = memo(({ canEdit, id, onChange, text, tip, value }) => {
    const onChange_ = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            onChange(id, event.target.checked);
        },
        [id, onChange]
    );

    return (
        <div className="global-settings__text-row">
            <label>
                <input checked={value} disabled={!canEdit} onChange={onChange_} type="checkbox" />
                {text}
                <LabelHelpTip title="Tip">
                    <div> {tip} </div>
                </LabelHelpTip>
            </label>
        </div>
    );
});

interface Props {
    authCount: number;
    canEdit: boolean;
    globalSettings: GlobalSettingsOptions;
    onChange: (id: string, value: boolean | string) => void;
}

export const GlobalSettings: FC<Props> = memo(({ canEdit, authCount, onChange, globalSettings }) => {
    let fieldData = FIELD_DATA;

    // If there are no user-created auth configs, there is no need to show the auto-create users checkbox
    if (authCount === 1) {
        fieldData = FIELD_DATA.slice(0, -1);
    }

    const onDefaultDomainChange = useCallback(
        (event: ChangeEvent<HTMLInputElement>) => {
            onChange('DefaultDomain', event.target.value);
        },
        [onChange]
    );

    return (
        <div className="panel panel-default">
            <div className="panel-heading">
                Global Settings
            </div>

            <div className="panel-body">
                {fieldData.map(data => (
                    <GlobalSetting
                        key={data.id}
                        canEdit={canEdit}
                        id={data.id}
                        onChange={onChange}
                        value={globalSettings[data.id]}
                        text={data.text}
                        tip={data.tip}
                    />
                ))}

                <div className="global-settings__default-domain">
                    <span>System Default Domain</span>

                    <LabelHelpTip title="Tip">
                        <div>
                            <div> Default domain for user sign in.</div>
                            <HelpLink topic="authenticationModule#dom">More info</HelpLink>
                        </div>
                    </LabelHelpTip>

                    <span className="global-settings__default-domain-field">
                        <input
                            disabled={!canEdit}
                            name="DefaultDomain"
                            type="text"
                            value={globalSettings?.DefaultDomain ?? ''}
                            onChange={onDefaultDomainChange}
                            className="form-control global-settings__default-domain-form"
                            placeholder="System default domain"
                        />
                    </span>
                </div>
            </div>
        </div>
    );
});
