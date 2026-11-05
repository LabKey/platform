import React, { FC, useEffect, useMemo, useState } from 'react';
import classNames from 'classnames';
import { createPortal } from 'react-dom';
import { getServerContext } from '@labkey/api';
import {
    AssistantButton,
    AssistantPanel,
    AssistantService,
    ServerContextProvider,
    useAssistantService,
    withAppUser,
} from '@labkey/components';

import './assistant.scss';

const BUTTON_ID = 'sdms-navbar-assistant-button';

const LabKeyAssistantButton: FC = () => {
    const { enabled, open } = useAssistantService();
    const buttonEl = useMemo<HTMLElement>(() => document.getElementById(BUTTON_ID), []);
    const button = enabled ? (
        <AssistantButton className="sdms-assistant-button" label="LabKey Assistant" onClick={open} />
    ) : undefined;
    return createPortal(button, buttonEl);
};
LabKeyAssistantButton.displayName = 'LabKeyAssistantButton';

export const LabKeyAssistant: FC = () => {
    const [scrolled, setScrolled] = useState<boolean>(false);
    const className = classNames('sdms-labkey-assistant', { 'sdms-labkey-assistant--scrolled': scrolled });
    useEffect(() => {
        const onScroll = () => {
            setScrolled(window.pageYOffset > 111);
        };
        document.addEventListener('scroll', onScroll);
        return () => document.removeEventListener('scroll', onScroll);
    }, []);
    return (
        <div className={className}>
            <ServerContextProvider initialContext={withAppUser(getServerContext())}>
                <AssistantService>
                    <LabKeyAssistantButton />
                    <AssistantPanel />
                </AssistantService>
            </ServerContextProvider>
        </div>
    );
};
LabKeyAssistant.displayName = 'LabKeyAssistant';
