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
import {ActionURL} from "@labkey/api";
import {DomainFieldsDisplay, AssayProtocolModel, AssayDesignerPanels, fetchProtocol} from "@glass/domainproperties";
import {Alert, LoadingSpinner} from "@glass/base";

type State = {
    protocolId: number,
    returnUrl: string,
    model?: AssayProtocolModel,
    isLoading: boolean,
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
            isLoading: true,
            returnUrl,
            dirty: false // TODO need handler to toggle this to true on changes to AssayDesignerPanels component
        };
    }

    componentDidMount() {
        const { protocolId } = this.state;

        // if URL has a protocol RowId, look up the assay design info
        if (protocolId) {
            fetchProtocol(protocolId)
                .then((model) => {
                    this.setState({
                        model,
                        isLoading: false
                    });
                })
                .catch((error) => {
                    this.setState({
                        message: error.exception,
                        isLoading: false
                    });
                });
        }
        // else we are on this page to create a new assay design
        else {
            this.setState(() => ({isLoading: false}));
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
        // this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        // });
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('project', 'begin'));
    };

    onComplete = (model: AssayProtocolModel) => {
        this.navigate(ActionURL.buildURL('assay', 'assayBegin', null, {rowId: model.protocolId}));
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
            />
        )
    }

    render() {
        const { isLoading, message } = this.state;
        const readOnly = false;// TODO show this read only view for users without DesignAssayPermission

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        if (readOnly) {
            return <>{this.renderReadOnlyView()}</>
        }

        return (
            <>{this.renderDesignerView()}</>
        )
    }
}

