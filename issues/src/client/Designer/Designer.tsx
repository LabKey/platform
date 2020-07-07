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
import {ActionURL, getServerContext} from "@labkey/api";
import {
    Alert,
    BeforeUnload,
    fetchIssuesListDefDesign,
    IssuesListDefDesignerPanels,
    IssuesListDefModel,
    LoadingSpinner,
} from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    isLoadingModel: boolean,
    message?: string,
    model?: IssuesListDefModel
}

export class App extends React.Component<{}, State> {

    private _dirty: boolean = false;

    constructor(props) {
        super(props);

        this.state = {
            isLoadingModel: true,
        };
    }

    componentDidMount() {
        const issueDefName = ActionURL.getParameter('issueDefName');

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
        this.navigate(this.getIssuesListUrl(this.state.model));
    };

    onChange = (model: IssuesListDefModel) => {
        this._dirty = true;
    };

    getIssuesListUrl(model?: IssuesListDefModel): string {
        if (model && model.issueDefName) {
            return ActionURL.buildURL('issues', 'list', getServerContext().container.path, {issueDefName: model.issueDefName});
        }

        return ActionURL.buildURL('issues', 'begin', getServerContext().container.path);
    }

    navigate = (defaultUrl: string) => {
        this._dirty = false;

        const returnUrl = ActionURL.getParameter('returnUrl');
        window.location.href = returnUrl || defaultUrl;
    };

    onComplete = (model: IssuesListDefModel) => {
        this.navigate(this.getIssuesListUrl(model));
    };

    renderContainerAlert() {
        const { model } = this.state;
        const domainContainer = model.domain.getDomainContainer();
        const sourceUrl = ActionURL.buildURL('issues', 'list', domainContainer, {
            issueDefName: model.issueDefName
        });

        return (
            <Alert bsStyle={'info'}>
                The fields definition for this issues list comes from a shared domain in another container and will
                not be updatable from this page. To manage the fields definition for this shared issues list,
                go to the <a href={sourceUrl}><b>source container</b></a>.
            </Alert>
        )
    }

    render() {
        const { isLoadingModel, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoadingModel) {
            return <LoadingSpinner/>
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                {model.domain.isSharedDomain() && this.renderContainerAlert()}
                <IssuesListDefDesignerPanels
                    initModel={model}
                    onCancel={this.onCancel}
                    onComplete={this.onComplete}
                    onChange={this.onChange}
                    useTheme={true}
                    successBsStyle={'primary'}
                />
            </BeforeUnload>
        );
    }
}