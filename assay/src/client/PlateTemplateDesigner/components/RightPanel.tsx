/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback } from 'react';

import { WellGroup } from '../models';
import { TabButton } from './TabButton';
import { TabList } from './TabList';
import { WellGroupProperties } from './WellGroupProperties';
import { WarningPanel } from './WarningPanel';

export const RIGHT_TAB_PROPERTIES = 'properties' as const;
export const RIGHT_TAB_WARNINGS = 'warnings' as const;
export type RightTab = typeof RIGHT_TAB_PROPERTIES | typeof RIGHT_TAB_WARNINGS;

interface RightPanelProps {
    activeGroup: null | WellGroup;
    onDeleteProperty: (groupRowId: number, key: string) => void;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onRightTabChange: (tab: RightTab) => void;
    rightTab: RightTab;
    showWarningPanel: boolean;
    warnings: string[];
}

/** Right sidebar of the plate designer showing well group properties and, when validation warnings exist, a tabbed warnings panel. */
export const RightPanel: FC<RightPanelProps> = props => {
    const { showWarningPanel, rightTab, onRightTabChange, warnings, activeGroup, onPropertyChange, onDeleteProperty } =
        props;
    const warningCount = warnings.length;

    const handlePropertiesTabClick = useCallback(() => onRightTabChange(RIGHT_TAB_PROPERTIES), [onRightTabChange]);
    const handleWarningsTabClick = useCallback(() => onRightTabChange(RIGHT_TAB_WARNINGS), [onRightTabChange]);

    return (
        <div className="plate-template-designer__right">
            {showWarningPanel && ( // Only show the tabs if we are showing the warnings too. Otherwise, just show the properties
                <TabList className="right-panel-tabs">
                    <TabButton
                        baseClass="right-panel-tabs__tab"
                        id="right-tab-properties"
                        isActive={rightTab === RIGHT_TAB_PROPERTIES}
                        onClick={handlePropertiesTabClick}
                        panelId="right-panel-properties"
                    >
                        Well Group Properties
                    </TabButton>
                    <TabButton
                        baseClass="right-panel-tabs__tab"
                        extraClassName={warningCount > 0 ? 'right-panel-tabs__tab--warn' : undefined}
                        id="right-tab-warnings"
                        isActive={rightTab === RIGHT_TAB_WARNINGS}
                        onClick={handleWarningsTabClick}
                        panelId="right-panel-warnings"
                    >
                        {warningCount > 0 ? `Warnings (${warningCount})` : 'Warnings'}
                    </TabButton>
                </TabList>
            )}
            <div
                aria-labelledby={showWarningPanel ? 'right-tab-properties' : undefined}
                hidden={showWarningPanel && rightTab !== RIGHT_TAB_PROPERTIES}
                id={showWarningPanel ? 'right-panel-properties' : undefined}
                role={showWarningPanel ? 'tabpanel' : undefined}
            >
                <WellGroupProperties
                    activeGroup={activeGroup}
                    onDeleteProperty={onDeleteProperty}
                    onPropertyChange={onPropertyChange}
                />
            </div>
            {showWarningPanel && (
                <div
                    aria-labelledby="right-tab-warnings"
                    hidden={rightTab !== RIGHT_TAB_WARNINGS}
                    id="right-panel-warnings"
                    role="tabpanel"
                >
                    <WarningPanel warnings={warnings} />
                </div>
            )}
        </div>
    );
};
RightPanel.displayName = 'RightPanel';
