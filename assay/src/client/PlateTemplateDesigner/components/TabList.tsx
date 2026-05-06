/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback } from 'react';

interface TabListProps {
    children: React.ReactNode;
    className?: string;
}

/** A `role="tablist"` container with built-in ArrowLeft/ArrowRight keyboard navigation between tabs. */
export const TabList: FC<TabListProps> = ({ className, children }) => {
    const handleKeyDown = useCallback((e: React.KeyboardEvent<HTMLDivElement>) => {
        if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
        const tabs = Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]'));
        const currentIndex = tabs.findIndex(t => t === document.activeElement);
        if (currentIndex === -1) return;
        e.preventDefault();
        const next =
            e.key === 'ArrowLeft' ? (currentIndex - 1 + tabs.length) % tabs.length : (currentIndex + 1) % tabs.length;
        tabs[next].click();
        tabs[next].focus();
    }, []);

    return (
        <div className={className} onKeyDown={handleKeyDown} role="tablist">
            {children}
        </div>
    );
};
TabList.displayName = 'TabList';
