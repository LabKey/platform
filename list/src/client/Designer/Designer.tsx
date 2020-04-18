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
import { ActionURL, Security, Domain, getServerContext } from "@labkey/api";
import {
    Alert,
    LoadingSpinner,
    PermissionTypes,
    ListDesignerPanels,
    ListModel,
    fetchListDesign,
    getListProperties,
    ConfirmModal
} from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    listId: number,
    returnUrl: string,
    hasDesignListPermission?: boolean,
    isLoadingModel: boolean,
    message?: string,
    dirty: boolean,
    model?: ListModel
    fileImportError: string
}

export class App extends React.Component<{}, State>
{
    constructor(props) {
        super(props);

        const { listId, returnUrl } = ActionURL.getParameters();

        this.state = {
            listId,
            returnUrl,
            isLoadingModel: true,
            dirty: false,
            fileImportError: undefined
        };
    }

    componentDidMount() {
        const { listId } = this.state;

        Security.getUserPermissions({
            containerPath: getServerContext().container.path,
            success: (data) => {
                this.setState(() => ({
                    hasDesignListPermission: data.container.effectivePermissions.indexOf(PermissionTypes.DesignList) > -1
                }));
            },
            failure: (error) => {
                this.setState(() => ({
                    message: error.exception,
                    hasDesignListPermission: false
                }));
            }
        });

        if (listId) {
            this.loadExistingList();
        } else {
            this.createNewListTemplate();
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

    loadExistingList() {
        fetchListDesign(this.state.listId)
            .then((model: ListModel) => {
                this.setState(() => ({model, isLoadingModel: false}));
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            });
    }

    createNewListTemplate() {
        getListProperties()
            .then((model: ListModel) => {
                this.setState(() => ({model, isLoadingModel: false}))
            })
            .catch((error) => {
                this.setState(() => ({message: error.exception, isLoadingModel: false}));
            })
    }

    onCancel = () => {
        this.navigate(ActionURL.buildURL('list', 'begin', getServerContext().container.path));
    };

    onComplete = (model: ListModel, fileImportError?: string) => {
        if (fileImportError) {
            this.setState(() => ({fileImportError, model}));
        }
        else {
            this.navigateOnComplete(model);
        }
    };

    navigateOnComplete(model: ListModel) {
        // if the model comes back to here without the newly saved listId, query to get it
        if (model.listId && model.listId > 0) {
            this.navigate(ActionURL.buildURL('list', 'grid', getServerContext().container.path, {listId: model.listId}));
        }
        else {
            Domain.getDomainDetails({
                containerPath: getServerContext().container.path,
                domainId: model.domain.domainId,
                success: (data) => {
                    const newModel = ListModel.create(data);
                    this.navigate(ActionURL.buildURL('list', 'grid', getServerContext().container.path, {listId: newModel.listId}));
                },
                failure: (error) => {
                    // bail out and go to the list-begin page
                    this.navigate(ActionURL.buildURL('list', 'begin', getServerContext().container.path));
                }
            });
        }
    }

    onChange = (model: ListModel) => {
        this.setState(() => ({dirty: true}));
    };

    navigate(defaultUrl: string) {
        const { returnUrl } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }

    renderFileImportErrorConfirm() {
        return (
            <ConfirmModal
                title='Error Importing File'
                msg={<>
                    <p>There was an error while trying to import the selected file. Please review the error below and go to the newly created list's import data page to try again.</p>
                    <ul><li>{this.state.fileImportError}</li></ul>
                </>}
                confirmVariant='primary'
                onConfirm={() => this.navigateOnComplete(this.state.model)}
                confirmButtonText='OK'
            />
        )
    }

    render() {
        const { isLoadingModel, hasDesignListPermission, message, model, fileImportError } = this.state;

        if (message) {
            return <Alert>{message}</Alert>
        }

        // set as loading until model is loaded and we know if the user has DesignListPerm
        if (isLoadingModel || hasDesignListPermission === undefined) {
            return <LoadingSpinner/>
        }

        if (!hasDesignListPermission) {
            return <Alert>You do not have sufficient permissions to create or edit a list design.</Alert>
        }

        return (
            <>
                {fileImportError && this.renderFileImportErrorConfirm()}
                {hasDesignListPermission &&
                    <ListDesignerPanels
                        initModel={model}
                        onCancel={this.onCancel}
                        onComplete={this.onComplete}
                        onChange={this.onChange}
                        useTheme={true}
                        successBsStyle={'primary'}
                    />
                }
            </>
        );
    }
}