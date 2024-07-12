/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import React from 'react';
import { fromJS, List } from 'immutable';
import {
    AppContextProvider,
    initQueryGridState,
    SelectInput,
    SelectInputOption,
    ServerContext,
    ServerContextProvider,
    withAppUser,
    NotificationsContextProvider,
    GlobalStateContextProvider,
} from '@labkey/components';
import { getServerContext } from '@labkey/api';

import { EditableGridPage } from './EditableGridPage';
import { GridPanelPage } from './GridPanelPage';

const COMPONENT_NAMES = List<SelectInputOption>([{ value: 'EditableGridPanel' }, { value: 'GridPanel' }]);

type State = {
    selected: string;
    serverContext: ServerContext;
};

export class App extends React.Component<any, State> {
    constructor(props) {
        super(props);

        this.state = this.getInitialState();

        initQueryGridState(
            fromJS({
                schema: {
                    lists: {
                        queryDefaults: {
                            appEditableTable: true,
                        },
                    },
                },
            })
        );
    }

    getInitialState = (): State => {
        return {
            selected: undefined,
            serverContext: withAppUser(getServerContext()),
        };
    };

    onSelectionChange = (id, selected) => {
        this.setState({ ...this.getInitialState(), selected });
    };

    renderPanel(title, body) {
        return (
            <div className="panel panel-default">
                <div className="panel-heading">{title}</div>
                <div className="panel-body">{body}</div>
            </div>
        );
    }

    render() {
        const { selected, serverContext } = this.state;

        return (
            <ServerContextProvider initialContext={serverContext}>
                <AppContextProvider>
                    <GlobalStateContextProvider>
                        <NotificationsContextProvider>
                            <p>
                                This page is setup to show examples of shared React components from the{' '}
                                <a
                                    href="https://github.com/LabKey/labkey-ui-components"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    labkey-ui-components
                                </a>{' '}
                                repository. To find more information about any of the components, check the{' '}
                                <a
                                    href="https://github.com/LabKey/labkey-ui-components/blob/develop/packages/components/docs/public.md"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                >
                                    documentation
                                </a>{' '}
                                page.
                            </p>

                            <SelectInput
                                key="labkey-ui-components-select"
                                name="labkey-ui-components-select"
                                placeholder="Select a component..."
                                inputClass="col-xs-4"
                                value={selected}
                                valueKey="value"
                                labelKey="value"
                                onChange={this.onSelectionChange}
                                options={COMPONENT_NAMES.toArray()}
                            />

                            <br />

                            {selected === 'EditableGridPanel' && <EditableGridPage />}
                            {selected === 'GridPanel' && <GridPanelPage />}
                        </NotificationsContextProvider>
                    </GlobalStateContextProvider>
                </AppContextProvider>
            </ServerContextProvider>
        );
    }
}
