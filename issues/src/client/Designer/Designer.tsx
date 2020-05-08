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
import React from 'react'
import { ActionURL, getServerContext } from "@labkey/api";
import {
    Alert,
    LoadingSpinner,
    PermissionTypes,
    IssuesListDefModel,
    BeforeUnload,
    IssuesListDefDesignerPanels,
    fetchIssuesListDefDesign
} from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    isLoadingModel: boolean,
    message?: string,
    model?: IssuesListDefModel,
    hasDesignIssuesAdminPermission?: boolean
}

export class App extends React.Component<{}, State> {

    private _dirty: boolean = false;

    constructor(props) {
        super(props);

        this.state = {
            isLoadingModel: true,
            hasDesignIssuesAdminPermission: true //TODO: remove once Security.getUserPermissions(..) is fixed in componentDidMount()
        };
    }

    componentDidMount() {
        const issueDefName = ActionURL.getParameter('issueDefName');

        //TODO: this throws error, need to investigate
        // Security.getUserPermissions({
        //     containerPath: getServerContext().container.path,
        //     success: (data) => {
        //         this.setState(() => ({
        //             hasDesignIssuesAdminPermission: data.container.effectivePermissions.indexOf(PermissionTypes.Admin) > -1
        //         }));
        //     },
        //     failure: (error) => {
        //         this.setState(() => ({
        //             message: error.exception,
        //             hasDesignIssuesAdminPermission: false
        //         }));
        //     }
        // });

        fetchIssuesListDefDesign(issueDefName)
            .then((model: IssuesListDefModel) => {
                this.setState(() => ({model, isLoadingModel: false}));
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            });
    }

    handleWindowBeforeUnload = (event) => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };


    onCancel = () => {
        this.navigate(ActionURL.buildURL('issues', 'begin', getServerContext().container.path));
    };

    onChange = (model: IssuesListDefModel) => {
        this._dirty = true;
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        // const returnUrl = ActionURL.getParameter('returnUrl');
        // window.location.href = returnUrl || defaultUrl;
    }

    onComplete = (model: IssuesListDefModel, fileImportError?: string) => {
        if (fileImportError) {
            this.setState(() => ({model}));
        }
        // else {
        //     this.navigateOnComplete(model);
        // }
    };

    render() {

        const { isLoadingModel, message, model, hasDesignIssuesAdminPermission } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoadingModel || hasDesignIssuesAdminPermission === undefined) {
            return <LoadingSpinner/>
        }

        if (!hasDesignIssuesAdminPermission) {
            return <Alert>You do not have sufficient permissions to create or edit a issues list definition design.</Alert>
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                {hasDesignIssuesAdminPermission &&
                <IssuesListDefDesignerPanels
                        initModel={model}
                        onCancel={this.onCancel}
                        onComplete={this.onComplete}
                        onChange={this.onChange}
                        useTheme={true}
                        successBsStyle={'primary'}
                />
                }
            </BeforeUnload>
        );
    }
}