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
import {DomainFieldsDisplay} from "@glass/domainproperties";
import {Alert, LoadingSpinner} from "@glass/base";

import {fetchProtocol} from "./actions";
import {AssayProtocolModel} from "./models";

type State = {
    protocolId: number,
    protocol?: AssayProtocolModel,
    message?: string
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        const protocolId = ActionURL.getParameter('rowId');

        this.state = {
            protocolId,
            message: protocolId ? undefined : 'No protocolId parameter provided.'
        };
    }

    componentDidMount() {
        const { protocolId } = this.state;

        fetchProtocol(protocolId)
            .then(protocol => {
                this.setState({protocol});
            })
            .catch(error => {
                this.setState({message: error})
            });
    }

    renderDomains() {
        const { protocol } = this.state;

        return (
            <>
                {protocol.domains.map((domain, index) => (
                    <DomainFieldsDisplay key={index} domain={domain} />
                ))}
            </>
        )
    }

    render() {
        const { protocol, message } = this.state;
        const isLoading = protocol === undefined && message === undefined;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <>
                <Panel>
                    <Panel.Heading>
                        <div className={"panel-title"}>{protocol.name}</div>
                    </Panel.Heading>
                    <Panel.Body>
                        <p>Provider: {protocol.providerName}</p>
                        <p>Description: {protocol.description}</p>
                    </Panel.Body>
                </Panel>
                {this.renderDomains()}
            </>
        )
    }
}

