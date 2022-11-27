/*
 * Copyright (c) 2019-2021 LabKey Corporation
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
import React from 'react';
import { Panel } from 'react-bootstrap';
import { ActionURL, Security, Utils, getServerContext, PermissionTypes } from '@labkey/api';
import {
    Alert,
    AssayDesignerPanels,
    AssayProtocolModel,
    BeforeUnload,
    DomainFieldsDisplay,
    fetchProtocol,
    LoadingSpinner,
    ServerContext,
    ServerContextProvider,
    withAppUser,
} from '@labkey/components';

import "../DomainDesigner.scss"

type State = {
    copy?: boolean;
    hasDesignAssayPerm?: boolean;
    isLoadingModel: boolean;
    message?: string;
    model?: AssayProtocolModel;
    protocolId: number;
    providerName?: string;
    returnUrl: string;
    serverContext: ServerContext;
}

export class App extends React.Component<any, State> {

    private _dirty: boolean = false;

    constructor(props) {
        super(props);

        const { rowId, copy, providerName } = ActionURL.getParameters();

        // hack, if the returnUrl has stripped off the rowId because of encoding/decoding issues (see TODO in AbstractAssayProvider.getManageMenuNavTree()) add it back on
        let returnUrl = ActionURL.getReturnUrl();
        if (rowId !== undefined && returnUrl && returnUrl.indexOf('rowId') === returnUrl.length - 5) {
            returnUrl = returnUrl + '=' + rowId
        }

        // default to dirty state for assay copy case
        this._dirty = copy || false;

        this.state = {
            protocolId: rowId,
            providerName,
            copy,
            isLoadingModel: true,
            returnUrl,
            serverContext: withAppUser(getServerContext()),
        };
    }

    componentDidMount() {
        const { protocolId, providerName, copy } = this.state;

        // query to find out if the user has permission to save assay designs
        Security.getUserPermissions({
            success: (data) => {
                this.setState({
                    hasDesignAssayPerm: data.container.effectivePermissions.indexOf(PermissionTypes.DesignAssay) > -1,
                });
            },
            failure: (error) => {
                this.setState({ hasDesignAssayPerm: false, message: error.exception });
            }
        });

        // if URL has a protocol RowId, look up the assay design info. otherwise use the providerName to get the template
        if (protocolId || providerName) {
            fetchProtocol(protocolId, providerName, copy)
                .then((model) => {
                    this.setState({ isLoadingModel: false, model });
                })
                .catch((error) => {
                    this.setState({ isLoadingModel: false, message: error.exception });
                });
        }
        else {
            this.setState({ isLoadingModel: false, message: 'Missing required parameter: rowId or providerName' });
        }
    }

    handleWindowBeforeUnload = (event) => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    navigate(defaultUrl: string) {
        this._dirty = false;

        window.location.href = this.state.returnUrl || defaultUrl;
    }

    onCancel = (): void => {
        this.navigate(ActionURL.buildURL('project', 'begin'));
    };

    onComplete = (model: AssayProtocolModel): void => {
        this.navigate(ActionURL.buildURL('assay', 'assayBegin', undefined, { rowId: model.protocolId }));
    };

    onChange = (model: AssayProtocolModel): void => {
        this._dirty = true;
    };

    // TODO: Convert this into a React component
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
            );
        }
    }

    render() {
        const { isLoadingModel, hasDesignAssayPerm, message, model, serverContext } = this.state;

        if (message) {
            return <Alert>{message}</Alert>;
        }

        // set as loading until model is loaded and we know if the user has DesignAssayPerm
        if (isLoadingModel || hasDesignAssayPerm === undefined) {
            return <LoadingSpinner />;
        }

        // check if this is a create assay case with a user that doesn't have permissions
        if (model.isNew() && !hasDesignAssayPerm) {
            return <Alert>You do not have sufficient permissions to create a new assay design.</Alert>;
        }

        return (
            <ServerContextProvider initialContext={serverContext}>
                <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                    {hasDesignAssayPerm && (
                        <AssayDesignerPanels
                            initModel={model}
                            onChange={this.onChange}
                            onCancel={this.onCancel}
                            onComplete={this.onComplete}
                            successBsStyle="primary"
                            useTheme
                        />
                    )}
                    {!hasDesignAssayPerm && (
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
                    )}
                </BeforeUnload>
            </ServerContextProvider>
        );
    }
}