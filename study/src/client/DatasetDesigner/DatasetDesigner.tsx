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

import React, {PureComponent} from "react";
import {Alert, DatasetDesignerPanels, fetchDatasetDesign, getDatasetProperties, LoadingSpinner} from "@labkey/components";
import { ActionURL, getServerContext } from "@labkey/api";
import {DatasetModel} from "@labkey/components/dist/components/domainproperties/dataset/models";

import "@labkey/components/dist/components.css"

interface State {
    datasetId: number,
    model: DatasetModel,
    isLoadingModel: boolean,
    message?: string,
    dirty: boolean,
    hasDatasetDesignPermission?: boolean,
    returnUrl: string,
}

export class App extends PureComponent<any, State> {
    constructor(props) {
        super(props);

        const { datasetId } = ActionURL.getParameters();

        this.state = {
            model: undefined,
            datasetId : datasetId,
            returnUrl : undefined,
            isLoadingModel: true,
            dirty: false
        };
    }

    componentDidMount() {
        const { datasetId } = this.state;

        if (datasetId) {
            this.loadExistingDataset();
        }
        else {
            this.createNewDataset();
        }
    }

    handleWindowBeforeUnload = (event) => {
        if (this.state.dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    loadExistingDataset() {
        const { datasetId } = this.state;

        fetchDatasetDesign(datasetId)
            .then((model: DatasetModel) => {
                this.setState(() => ({model, isLoadingModel: false}));
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            });
    }

    createNewDataset() {
        getDatasetProperties()
            .then((model: DatasetModel) => {
                this.setState(() => ({model, isLoadingModel: false}))
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            })
    }

    navigate(defaultUrl: string) {
        const { returnUrl } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('study', 'begin', getServerContext().container.path));
    };

    render() {
        const { isLoadingModel, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        if (isLoadingModel) {
            return <LoadingSpinner/>
        }

        return (
            <>
                <DatasetDesignerPanels
                    initModel={model}
                    onCancel={this.onCancel}
                    showDataSpace={false}
                    showVisitDate={true}
                    useTheme={true}
                />
            </>
        )
    }
}