/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { ChangeEventHandler, FC, memo, PropsWithChildren, ReactNode, useCallback, useMemo } from 'react';
import classNames from 'classnames';
import { HelpLink, LabelHelpTip } from '@labkey/components';

import { GlobalSettingsOptions } from './models';

interface GlobalSettingFieldData {
    name: string;
    text: string;
    tip: ReactNode;
}

const LOGIN_ATTEMPT_LIMIT_OPTIONS = ['3', '5', '10', '100'];
const LOGIN_ATTEMPT_PERIOD_OPTIONS = ['5', '15', '30', '60'];
const LOGIN_ATTEMPT_RESET_TIME_OPTIONS = ['5', '10', '30', '60'];

const FIELD_DATA: GlobalSettingFieldData[] = [
    {
        name: 'SelfRegistration',
        text: 'Allow self sign up',
        tip: 'Users are able to register for accounts when using database authentication. Use caution when enabling this if you have enabled sending email to non-users.',
    },
    {
        name: 'SelfServiceEmailChanges',
        text: 'Allow users to edit their own email addresses',
        tip: 'Users can change their own email address if their password is managed by LabKey Server.',
    },
    {
        name: 'AutoCreateAccounts',
        text: 'Auto-create authenticated users',
        tip: 'Accounts are created automatically when new users authenticate via LDAP or SSO.',
    },
];

interface GlobalSettingProps extends GlobalSettingFieldData, PropsWithChildren {
    canEdit: boolean;
    onChange: ChangeEventHandler<HTMLInputElement>;
    value: boolean;
}

const GlobalSetting: FC<GlobalSettingProps> = ({ canEdit, children, name, onChange, text, tip, value }) => (
    <div className="global-settings__text-row">
        <label>
            <input checked={value} disabled={!canEdit} name={name} onChange={onChange} type="checkbox" />
            {text}
            <LabelHelpTip title="Tip">
                <div>{tip}</div>
            </LabelHelpTip>
        </label>
        {children}
    </div>
);
GlobalSetting.displayName = 'GlobalSetting';

interface SelectProps {
    disabled: boolean;
    name: string;
    onChange: ChangeEventHandler<HTMLSelectElement>;
    options: string[];
    value: string;
}

const Select: FC<SelectProps> = ({ disabled, name, onChange, options, value }) => (
    <select disabled={disabled} name={name} onChange={onChange} value={value}>
        {options.map(v => (
            <option key={v} value={v}>
                {v}
            </option>
        ))}
    </select>
);
Select.displayName = 'Select';

interface Props {
    authCount: number;
    canEdit: boolean;
    globalSettings: GlobalSettingsOptions;
    onChange: (id: string, value: boolean | string) => void;
}

export const GlobalSettings: FC<Props> = memo(({ canEdit, authCount, onChange, globalSettings }) => {
    // If there are no user-created auth configs, there is no need to show the auto-create users checkbox
    const fieldData = useMemo(() => (authCount === 1 ? FIELD_DATA.slice(0, -1) : FIELD_DATA), [authCount]);

    const onChangeChecked = useCallback<ChangeEventHandler<HTMLInputElement>>(
        event => {
            onChange(event.target.name, event.target.checked);
        },
        [onChange]
    );

    const onChangeValue = useCallback<ChangeEventHandler<HTMLInputElement | HTMLSelectElement>>(
        event => {
            onChange(event.target.name, event.target.value);
        },
        [onChange]
    );

    const loginAttemptEnabled = !!globalSettings?.LoginAttemptEnabled;
    const loginAttemptLimit = globalSettings?.LoginAttemptLimit ?? '3';
    const loginAttemptPeriod = globalSettings?.LoginAttemptPeriod ?? '30';
    const loginAttemptResetTime = globalSettings?.LoginAttemptResetTime ?? '5';
    const loginAttemptsDisabled = !canEdit || !loginAttemptEnabled;

    return (
        <div className="panel panel-default">
            <div className="panel-heading">Global Settings</div>

            <div className="panel-body">
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
                            className="form-control global-settings__default-domain-form"
                            disabled={!canEdit}
                            name="DefaultDomain"
                            onChange={onChangeValue}
                            placeholder="System default domain"
                            type="text"
                            value={globalSettings?.DefaultDomain ?? ''}
                        />
                    </span>
                </div>

                <hr />

                {fieldData.map(data => (
                    <GlobalSetting
                        canEdit={canEdit}
                        key={data.name}
                        name={data.name}
                        onChange={onChangeChecked}
                        text={data.text}
                        tip={data.tip}
                        value={globalSettings[data.name]}
                    />
                ))}

                <GlobalSetting
                    canEdit={canEdit}
                    name="LoginAttemptEnabled"
                    onChange={onChangeChecked}
                    text="Limit unsuccessful login attempts"
                    tip={
                        <div>
                            This does not apply to site and application administrators.{' '}
                            <HelpLink topic="complianceSettings#Login">More info</HelpLink>
                        </div>
                    }
                    value={loginAttemptEnabled}
                >
                    <div
                        className={classNames('global-settings__text-row-section', { disabled: loginAttemptsDisabled })}
                    >
                        <span>Disable user login if </span>
                        <Select
                            disabled={loginAttemptsDisabled}
                            name="LoginAttemptLimit"
                            onChange={onChangeValue}
                            options={LOGIN_ATTEMPT_LIMIT_OPTIONS}
                            value={loginAttemptLimit}
                        />
                        <span> consecutive invalid logins are attempted in a </span>
                        <Select
                            disabled={loginAttemptsDisabled}
                            name="LoginAttemptPeriod"
                            onChange={onChangeValue}
                            options={LOGIN_ATTEMPT_PERIOD_OPTIONS}
                            value={loginAttemptPeriod}
                        />
                        <span> second period. Automatically allow users to login again after </span>
                        <Select
                            disabled={loginAttemptsDisabled}
                            name="LoginAttemptResetTime"
                            onChange={onChangeValue}
                            options={LOGIN_ATTEMPT_RESET_TIME_OPTIONS}
                            value={loginAttemptResetTime}
                        />
                        <span> minutes.</span>
                    </div>
                </GlobalSetting>
            </div>
        </div>
    );
});
GlobalSettings.displayName = 'GlobalSettings';
