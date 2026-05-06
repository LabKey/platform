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
    showWarningPanel: boolean;
    rightTab: RightTab;
    onRightTabChange: (tab: RightTab) => void;
    warnings: string[];
    activeGroup: WellGroup | null;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onDeleteProperty: (groupRowId: number, key: string) => void;
}

/** Right sidebar of the plate designer showing well group properties and, when validation warnings exist, a tabbed warnings panel. */
export const RightPanel: FC<RightPanelProps> = (props) => {
    const { showWarningPanel, rightTab, onRightTabChange, warnings, activeGroup, onPropertyChange, onDeleteProperty } = props;
    const warningCount = warnings.length;

    const handlePropertiesTabClick = useCallback(() => onRightTabChange(RIGHT_TAB_PROPERTIES), [onRightTabChange]);
    const handleWarningsTabClick = useCallback(() => onRightTabChange(RIGHT_TAB_WARNINGS), [onRightTabChange]);

    return (
        <div className="plate-template-designer__right">
            {showWarningPanel && ( // Only show the tabs if we are showing the warnings too. Otherwise, just show the properties
                <TabList className="right-panel-tabs">
                    <TabButton
                        id="right-tab-properties"
                        panelId="right-panel-properties"
                        isActive={rightTab === RIGHT_TAB_PROPERTIES}
                        baseClass="right-panel-tabs__tab"
                        onClick={handlePropertiesTabClick}
                    >
                        Well Group Properties
                    </TabButton>
                    <TabButton
                        id="right-tab-warnings"
                        panelId="right-panel-warnings"
                        isActive={rightTab === RIGHT_TAB_WARNINGS}
                        baseClass="right-panel-tabs__tab"
                        extraClassName={warningCount > 0 ? 'right-panel-tabs__tab--warn' : undefined}
                        onClick={handleWarningsTabClick}
                    >
                        {warningCount > 0 ? `Warnings (${warningCount})` : 'Warnings'}
                    </TabButton>
                </TabList>
            )}
            <div
                id={showWarningPanel ? 'right-panel-properties' : undefined}
                role={showWarningPanel ? 'tabpanel' : undefined}
                aria-labelledby={showWarningPanel ? 'right-tab-properties' : undefined}
                hidden={showWarningPanel && rightTab !== RIGHT_TAB_PROPERTIES}
            >
                <WellGroupProperties
                    activeGroup={activeGroup}
                    onPropertyChange={onPropertyChange}
                    onDeleteProperty={onDeleteProperty}
                />
            </div>
            {showWarningPanel && (
                <div
                    id="right-panel-warnings"
                    role="tabpanel"
                    aria-labelledby="right-tab-warnings"
                    hidden={rightTab !== RIGHT_TAB_WARNINGS}
                >
                    <WarningPanel warnings={warnings} />
                </div>
            )}
        </div>
    );
};
RightPanel.displayName = 'RightPanel';
