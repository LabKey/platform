/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import classNames from 'classnames';

import { WellGroup } from '../models';
import { WellGroupProperties } from './WellGroupProperties';
import { WarningPanel } from './WarningPanel';

interface RightPanelProps {
    showWarningPanel: boolean;
    rightTab: 'properties' | 'warnings';
    onRightTabChange: (tab: 'properties' | 'warnings') => void;
    warnings: string[];
    activeGroup: WellGroup | null;
    onPropertyChange: (groupRowId: number, key: string, value: string) => void;
    onDeleteProperty: (groupRowId: number, key: string) => void;
}

export function RightPanel(props: RightPanelProps): JSX.Element {
    const { showWarningPanel, rightTab, onRightTabChange, warnings, activeGroup, onPropertyChange, onDeleteProperty } = props;
    const warningCount = warnings.length;

    return (
        <div className="plate-template-designer__right">
            {showWarningPanel && (
                <div className="right-panel-tabs" role="tablist">
                    <button
                        id="right-tab-properties"
                        role="tab"
                        aria-controls="right-panel-properties"
                        aria-selected={rightTab === 'properties'}
                        className={classNames('right-panel-tabs__tab', {
                            'right-panel-tabs__tab--active': rightTab === 'properties',
                        })}
                        onClick={() => onRightTabChange('properties')}
                    >
                        Well Group Properties
                    </button>
                    <button
                        id="right-tab-warnings"
                        role="tab"
                        aria-controls="right-panel-warnings"
                        aria-selected={rightTab === 'warnings'}
                        className={classNames('right-panel-tabs__tab', {
                            'right-panel-tabs__tab--active': rightTab === 'warnings',
                            'right-panel-tabs__tab--warn': warningCount > 0,
                        })}
                        onClick={() => onRightTabChange('warnings')}
                    >
                        {warningCount > 0 ? `Warnings (${warningCount})` : 'Warnings'}
                    </button>
                </div>
            )}
            <div
                id={showWarningPanel ? 'right-panel-properties' : undefined}
                role={showWarningPanel ? 'tabpanel' : undefined}
                aria-labelledby={showWarningPanel ? 'right-tab-properties' : undefined}
                hidden={showWarningPanel && rightTab !== 'properties'}
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
                    hidden={rightTab !== 'warnings'}
                >
                    <WarningPanel warnings={warnings} />
                </div>
            )}
        </div>
    );
}
