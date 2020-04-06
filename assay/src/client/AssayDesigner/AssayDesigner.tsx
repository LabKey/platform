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
import React from 'react'
import {Panel} from "react-bootstrap";
import {ActionURL, Security, Utils, getServerContext} from "@labkey/api";
import {Alert, LoadingSpinner, PermissionTypes, DomainFieldsDisplay, AssayProtocolModel, AssayDesignerPanels, fetchProtocol} from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    protocolId: number,
    providerName?: string,
    copy?: boolean,
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

        const { rowId, copy, providerName } = ActionURL.getParameters();

        // hack, if the returnUrl has stripped off the rowId because of encoding/decoding issues (see TODO in AbstractAssayProvider.getManageMenuNavTree()) add it back on
        let returnUrl = ActionURL.getParameter('returnUrl');
        if (rowId !== undefined && returnUrl && returnUrl.indexOf('rowId') === returnUrl.length - 5) {
            returnUrl = returnUrl + '=' + rowId
        }

        this.state = {
            protocolId: rowId,
            providerName,
            copy,
            isLoadingModel: true,
            returnUrl,
            dirty: copy || false // default to dirty state for assay copy case
        };
    }

    componentDidMount() {
        const { protocolId, providerName, copy } = this.state;

        // query to find out if the user has permission to save assay designs
        Security.getUserPermissions({
            containerPath: getServerContext().container.path,
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

        // if URL has a protocol RowId, look up the assay design info. otherwise use the providerName to get the template
        if (protocolId || providerName) {
            fetchProtocol(protocolId, providerName, copy)
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
        else {
            this.setState(() => ({
                message: 'Missing required parameter: rowId or providerName',
                isLoadingModel: false
            }));
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
        this.navigate(ActionURL.buildURL('project', 'begin', getServerContext().container.path));
    };

    onComplete = (model: AssayProtocolModel) => {
        this.navigate(ActionURL.buildURL('assay', 'assayBegin', getServerContext().container.path, {rowId: model.protocolId}));
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
                        <table>
                            {this.renderReadOnlyProperty('Provider', model.providerName)}
                            {this.renderReadOnlyProperty('Description', model.description)}
                            {this.renderReadOnlyProperty('Plate Template', model.selectedPlateTemplate)}
                            {this.renderReadOnlyProperty('Detection Method', model.selectedDetectionMethod)}
                            {this.renderReadOnlyProperty('Metadata Input Format', model.selectedMetadataInputFormat)}
                            {this.renderReadOnlyProperty('QC States', model.qcEnabled)}
                            {this.renderReadOnlyProperty('Auto-Copy Data to Study', model.autoCopyTargetContainer ? model.autoCopyTargetContainer['path'] : undefined)}
                            {this.renderReadOnlyProperty('Import in Background', model.backgroundUpload)}
                            {this.renderReadOnlyProperty('Transform Scripts', model.protocolTransformScripts, model.protocolTransformScripts.size === 0)}
                            {this.renderReadOnlyProperty('Save Script Data for Debugging', model.saveScriptFiles)}
                            {this.renderReadOnlyProperty('Module-Provided Scripts', model.moduleTransformScripts, model.moduleTransformScripts.size === 0)}
                            {this.renderReadOnlyProperty('Editable Runs', model.editableRuns)}
                            {this.renderReadOnlyProperty('Editable Results', model.editableResults)}
                        </table>
                    </Panel.Body>
                </Panel>
                {model.domains.map((domain, index) => (
                    <DomainFieldsDisplay key={index} domain={domain} />
                ))}
            </>
        )
    }

    renderReadOnlyProperty(label: string, value: any, hide?: boolean) {
        if (value && !hide) {
            return (
                <tr>
                    <td style={{verticalAlign: 'top', padding: '3px'}}>{label}:&nbsp;&nbsp;&nbsp;</td>
                    <td>
                        {Utils.isString(value) && value}
                        {Utils.isBoolean(value) && value.toString()}
                        {typeof value === 'object' && value.map((val) => <div>{val}</div>)}
                    </td>
                </tr>
            )
        }
    }

    renderDesignerView() {
        const { model } = this.state;

        return (
            <AssayDesignerPanels
                initModel={model}
                onCancel={this.onCancel}
                onComplete={this.onComplete}
                onChange={this.onChange}
                useTheme={true}
                successBsStyle={'primary'}
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
        if (model.isNew() && !hasDesignAssayPerm) {
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