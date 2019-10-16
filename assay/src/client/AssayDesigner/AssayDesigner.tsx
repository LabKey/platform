/*
 * Copyright (c) 2019 LabKey Corporation
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
import {ActionURL, Security} from "@labkey/api";
import {DomainFieldsDisplay, AssayProtocolModel, AssayDesignerPanels, fetchProtocol} from "@glass/domainproperties";
import { Alert, LoadingSpinner, PermissionTypes } from "@glass/base";

import "@glass/base/dist/base.css"
import "@glass/domainproperties/dist/domainproperties.css"

type State = {
    protocolId: number,
    returnUrl: string,
    model?: AssayProtocolModel,
    isLoadingModel: boolean,
    hasDesignAssayPerm?: boolean
    message?: string
    dirty: boolean
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        const { rowId, returnUrl } = ActionURL.getParameters();

        this.state = {
            protocolId: rowId,
            isLoadingModel: true,
            returnUrl,
            dirty: false
        };
    }

    componentDidMount() {
        const { protocolId } = this.state;

        // query to find out if the user has permission to save assay designs
        Security.getUserPermissions({
            success: (data) => {
                this.setState(() => ({
                    hasDesignAssayPerm: data.container.effectivePermissions.indexOf(PermissionTypes.DesignAssay) > -1
                }));
            },
            failure: (error) => {
                this.setState(() => ({
                    message: error.exception,
                    hasDesignAssayPerm: false
                }));
            }
        });

        // if URL has a protocol RowId, look up the assay design info
        if (protocolId) {
            fetchProtocol(protocolId)
                .then((model) => {
                    this.setState(() => ({
                        model,
                        isLoadingModel: false
                    }));
                })
                .catch((error) => {
                    this.setState(() => ({
                        message: error.exception,
                        isLoadingModel: false
                    }));
                });
        }
        // else we are on this page to create a new assay design
        else {
            this.setState(() => ({isLoadingModel: false}));
        }

        window.addEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    componentWillUnmount() {
        window.removeEventListener("beforeunload", this.handleWindowBeforeUnload);
    }

    handleWindowBeforeUnload = (event) => {
        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    navigate(defaultUrl: string) {
        const { returnUrl } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('project', 'begin'));
    };

    onComplete = (model: AssayProtocolModel) => {
        this.navigate(ActionURL.buildURL('assay', 'assayBegin', null, {rowId: model.protocolId}));
    };

    onChange = (model: AssayProtocolModel) => {
        this.setState(() => ({dirty: true}));
    };

    renderReadOnlyView() {
        const { model } = this.state;

        return (
            <>
                <Panel>
                    <Panel.Heading>
                        <div className={"panel-title"}>{model.name}</div>
                    </Panel.Heading>
                    <Panel.Body>
                        <p>Provider: {model.providerName}</p>
                        <p>Description: {model.description}</p>
                    </Panel.Body>
                </Panel>
                {model.domains.map((domain, index) => (
                    <DomainFieldsDisplay key={index} domain={domain} />
                ))}
            </>
        )
    }

    renderDesignerView() {
        const { model } = this.state;

        return (
            <AssayDesignerPanels
                initModel={model}
                onCancel={this.onCancel}
                onComplete={this.onComplete}
                onChange={this.onChange}
            />
        )
    }

    render() {
        const { isLoadingModel, hasDesignAssayPerm, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        // set as loading until model is loaded and we know if the user has DesignAssayPerm
        if (isLoadingModel || hasDesignAssayPerm === undefined) {
            return <LoadingSpinner/>
        }

        // check if this is a create assay case with a user that doesn't have permissions
        if (model === undefined && !hasDesignAssayPerm) {
            return <Alert>You do not have sufficient permissions to create a new assay design.</Alert>
        }

        return (
            <>
                {hasDesignAssayPerm
                    ? this.renderDesignerView()
                    : this.renderReadOnlyView()
                }
            </>
        )
    }
}

