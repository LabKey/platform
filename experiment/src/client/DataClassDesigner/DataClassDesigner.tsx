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
import { ActionURL } from "@labkey/api";
import { Alert, DataClassDesigner, DataClassModel, fetchDataClass, LoadingSpinner } from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    model?: DataClassModel,
    isLoading: boolean,
    message?: string
    dirty: boolean
}

export class App extends React.Component<any, State> {

    constructor(props)
    {
        super(props);

        this.state = {
            isLoading: true,
            dirty: false
        };
    }

    componentDidMount() {
        // if URL has a name, look up the data class info for the edit case
        // else we are in the create new data class case
        const { name } = ActionURL.getParameters();
        if (name) {
            fetchDataClass(name)
                .then((model: DataClassModel) => {
                    this.setState(() => ({model, isLoading: false}));
                })
                .catch((error) => {
                    this.setState(() => ({message: error.exception, isLoading: false}));
                });
        }
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
        const returnUrl = ActionURL.getParameter('returnUrl');

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('experiment', 'listDataClass', LABKEY.container.path));
    };

    onComplete = (model: DataClassModel) => {
        this.navigate(ActionURL.buildURL('experiment', 'showDataClass', LABKEY.container.path, {name: model.name}));
    };

    onChange = (model: DataClassModel) => {
        this.setState(() => ({dirty: true}));
    };

    render() {
        const { isLoading, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoading) {
            return <LoadingSpinner/>
        }

        return (
            <DataClassDesigner
                initModel={model}
                onCancel={this.onCancel}
                onComplete={this.onComplete}
                onChange={this.onChange}
            />
        )
    }
}

