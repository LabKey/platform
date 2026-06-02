/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { List } from 'immutable';
import { AppContexts, initQueryGridState, SelectInput, SelectInputOption } from '@labkey/components';

import { EditableGridPage } from './EditableGridPage';
import { GridPanelPage } from './GridPanelPage';

const COMPONENT_NAMES = List<SelectInputOption>([{ value: 'EditableGridPanel' }, { value: 'GridPanel' }]);

type State = {
    selected: string;
};

export class App extends React.Component<any, State> {
    constructor(props) {
        super(props);

        this.state = this.getInitialState();

        initQueryGridState({
            schema: {
                lists: {
                    queryDefaults: {
                        appEditableTable: true,
                    },
                },
            },
        });
    }

    getInitialState = (): State => {
        return { selected: undefined };
    };

    onSelectionChange = (id, selected) => {
        this.setState({ ...this.getInitialState(), selected });
    };

    render() {
        const { selected } = this.state;

        return (
            <AppContexts>
                <p>
                    This page is setup to show examples of shared React components from the{' '}
                    <a href="https://github.com/LabKey/labkey-ui-components" rel="noopener noreferrer" target="_blank">
                        labkey-ui-components
                    </a>{' '}
                    repository. To find more information about any of the components, check the{' '}
                    <a
                        href="https://github.com/LabKey/labkey-ui-components/blob/develop/packages/components/docs/public.md"
                        rel="noopener noreferrer"
                        target="_blank"
                    >
                        documentation
                    </a>{' '}
                    page.
                </p>

                <SelectInput
                    inputClass="col-xs-4"
                    key="labkey-ui-components-select"
                    labelKey="value"
                    name="labkey-ui-components-select"
                    onChange={this.onSelectionChange}
                    options={COMPONENT_NAMES.toArray()}
                    placeholder="Select a component..."
                    value={selected}
                    valueKey="value"
                />

                <br />

                {selected === 'EditableGridPanel' && <EditableGridPage />}
                {selected === 'GridPanel' && <GridPanelPage />}
            </AppContexts>
        );
    }
}
