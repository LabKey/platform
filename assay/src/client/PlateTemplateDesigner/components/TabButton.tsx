/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import classNames from 'classnames';

interface TabButtonProps {
    id: string;
    panelId: string;
    isActive: boolean;
    baseClass: string;
    extraClassName?: string;
    onClick: () => void;
    children: React.ReactNode;
}

/**
 * A single ARIA tab button. Handles `role="tab"`, `aria-controls`, `aria-selected`,
 * and the BEM `<baseClass>--active` modifier. Use inside a `role="tablist"` container.
 */
export function TabButton({ id, panelId, isActive, baseClass, extraClassName, onClick, children }: TabButtonProps): JSX.Element {
    return (
        <button
            id={id}
            role="tab"
            aria-controls={panelId}
            aria-selected={isActive}
            tabIndex={isActive ? 0 : -1}
            className={classNames(baseClass, { [`${baseClass}--active`]: isActive }, extraClassName)}
            onClick={onClick}
        >
            {children}
        </button>
    );
}
