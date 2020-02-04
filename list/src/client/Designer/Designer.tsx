/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import * as React from 'react'
import {Panel} from "react-bootstrap";
import {ActionURL, Security, Utils} from "@labkey/api";
import {Alert, LoadingSpinner, PermissionTypes, DomainFieldsDisplay , fetchProtocol} from "@labkey/components"; //TODO: include List model and panel added in labkey-ui-components

import "@labkey/components/dist/components.css"

type State = {
    listId: number,
    returnUrl: string,
    allowFileLinkProperties: 0,
    allowAttachmentProperties: 1,
    showDefaultValueSettings: 1,
    hasDesignListPermission?: boolean,
    isLoadingModel: boolean,
    message?: string,
    dirty: boolean,
    // model?:  //TODO: define list model in labkey-ui-components

    //TODO: Not sure if these are needed given hasDesignListPermission above, carried over from ListController.EditListDefinitionAction:
    // hasInsertPermission: boolean,
    // hasDeleteListPermission: boolean
}

export class App extends React.Component<any, State>
{
    constructor(props)
    {
        super(props);

        const { listId } = ActionURL.getParameters();

        let returnUrl = ActionURL.getParameter('returnUrl');

        this.state = {
            listId,
            isLoadingModel: true,
            returnUrl,
            dirty: false, //TODO : handle this correctly,
            allowFileLinkProperties: 0,
            allowAttachmentProperties: 1,
            showDefaultValueSettings: 1,
        };
    }

    render() {
        const { isLoadingModel, hasDesignListPermission, message } = this.state; //TODO: add model once its in labkey-ui-components

        return <Alert>New List Designer Page under construction</Alert>; //TODO: Remove

        //TODO : uncomment
        // if (message) {
        //     return <Alert>{message}</Alert>
        // }

        // set as loading until model is loaded and we know if the user has DesignListPerm
        if (isLoadingModel || hasDesignListPermission === undefined) {
            return <LoadingSpinner/>
        }

        // check if this is a create list case with a user that doesn't have permissions
        // if (model.isNew() && !hasDesignListPermission) { //TODO: use this when ListModel is in place in labkey-ui-components
        if (!hasDesignListPermission) {
            return <Alert>You do not have sufficient permissions to create a new list design.</Alert>
        }

        return (
            <>
                {hasDesignListPermission
                    ? this.renderDesignerView()
                    : this.renderReadOnlyView()
                }
            </>
        )
    }

    //TODO: revisit/uncomment once ListDesignerPanels is in place in labkey-ui-components

    renderDesignerView() {
        // const { model } = this.state;
        //
        // return (
        //     <ListDesignerPanels
        //         initModel={model}
        //         onCancel={this.onCancel}
        //         onComplete={this.onComplete}
        //         onChange={this.onChange}
        //         useTheme={true}
        //     />
        // )
    }

    renderReadOnlyView() {
        //TODO: is this relevant for List?
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('project', 'begin', LABKEY.container.path));
    };

    //TODO revisit/uncomment once List Model is in place in labkey-ui-components

    // onComplete = (model: ListModel) => {
    //     this.navigate(ActionURL.buildURL('list', '??', LABKEY.container.path, {rowId: model.??}));
    // };
    //
    // onChange = (model: ListModel) => {
    //     this.setState(() => ({dirty: true}));
    // };

    navigate(defaultUrl: string) {
        const { returnUrl } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }
}