/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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

    // Pages can override the header, so the assistant button div may not be available
    if (!enabled || !buttonEl) return null;

    const button = (
        <AssistantButton
            className="sdms-assistant-button"
            id="sdms-assistant-button"
            label="LabKey Assistant"
            onClick={open}
        />
    );

    return createPortal(button, buttonEl);
};
LabKeyAssistantButton.displayName = 'LabKeyAssistantButton';

export const LabKeyAssistant: FC = () => {
    const [scrolled, setScrolled] = useState<boolean>(false);
    const className = classNames('sdms-labkey-assistant', { 'sdms-labkey-assistant--scrolled': scrolled });
    useEffect(() => {
        const onScroll = () => {
            setScrolled(window.scrollY > 111);
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
