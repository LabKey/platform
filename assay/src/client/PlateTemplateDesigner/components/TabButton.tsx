/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC } from 'react';
import classNames from 'classnames';

interface TabButtonProps {
    baseClass: string;
    children: React.ReactNode;
    extraClassName?: string;
    id: string;
    isActive: boolean;
    onClick: () => void;
    panelId: string;
}

/**
 * A single ARIA tab button. Handles `role="tab"`, `aria-controls`, `aria-selected`,
 * and the BEM `<baseClass>--active` modifier. Use inside a `role="tablist"` container.
 */
export const TabButton: FC<TabButtonProps> = ({
    id,
    panelId,
    isActive,
    baseClass,
    extraClassName,
    onClick,
    children,
}) => {
    return (
        <button
            aria-controls={panelId}
            aria-selected={isActive}
            className={classNames(baseClass, { [`${baseClass}--active`]: isActive }, extraClassName)}
            id={id}
            onClick={onClick}
            role="tab"
            tabIndex={isActive ? 0 : -1}
        >
            {children}
        </button>
    );
};
TabButton.displayName = 'TabButton';
