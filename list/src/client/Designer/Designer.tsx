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
import * as React from 'react'
import {ActionURL, Security, Ajax} from "@labkey/api";
import {
    Alert,
    LoadingSpinner,
    PermissionTypes,
    ListDesignerPanels,
    ListModel,
    fetchListDesign,
    createListDesign,
} from "@labkey/components";

import "@labkey/components/dist/components.css"

type State = {
    listId: number,
    returnUrl: string,
    hasDesignListPermission?: boolean,
    isLoadingModel: boolean,
    message?: string,
    dirty: boolean,
    model?: ListModel,
    domainId?: number;
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
            dirty: false, //TODO : handle this correctly,
        };
    }

    componentDidMount() {
        const {listId} = this.state;
        Security.getUserPermissions({
            containerPath: LABKEY.container.path,
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
    }

    loadExistingList = () => {
        // Retrieve domainId, given listId
        Ajax.request({
            url: ActionURL.buildURL('list', 'GetListProperties'),
            method: 'GET',
            params: { listId: this.state.listId },
            scope: this,
            failure: function(error) {
                this.setState(() => ({
                    message: error,
                    isLoadingModel: false
                }));
            },
            success: function(result) {
                const response = JSON.parse(result.response);

                // Retrieve model, given domainId
                fetchListDesign(response.domainId)
                    .then((model) => {
                        this.setState(() => ({
                                model,
                                isLoadingModel: false
                            })
                            , () => {console.log("loadExistingList", this.state)}
                        )
                    })
                    .catch((error) => {
                        this.setState(() => ({
                            message: error.exception,
                            isLoadingModel: false
                        }));
                    })
            },
        });
    };

    createNewListTemplate = () => {
        createListDesign()
            .then((model: ListModel) => {
                this.setState(() => ({
                        model,
                        isLoadingModel: false
                    })
                    , () => {console.log("createNewListTemplate", this.state)}
                )
            })
            .catch((error) => {
                this.setState(() => ({
                    message: error.exception,
                    isLoadingModel: false
                }));
            })
    };

    onCancel = () => {
        this.navigate(ActionURL.buildURL('project', 'begin', LABKEY.container.path));
    };

    // onComplete = (model: ListModel) => {
    //     this.navigate(ActionURL.buildURL('list', '??', LABKEY.container.path, {rowId: model.??}));
    // };
    //
    // onChange = (model: ListModel) => {
    //     this.setState(() => ({dirty: true}));
    // };

    navigate(defaultUrl: string) {
        const { returnUrl } = this.state;

        this.setState(() => ({dirty: false}), () => {
            window.location.href = returnUrl || defaultUrl;
        });
    }

    render() {
        const { isLoadingModel, hasDesignListPermission, message, model } = this.state; //TODO: add model once its in labkey-ui-components

        if (message) {
            return <Alert>{message}</Alert>
        }

        // set as loading until model is loaded and we know if the user has DesignListPerm
        if (isLoadingModel || hasDesignListPermission === undefined) {
            return <LoadingSpinner/>
        }

        if (!hasDesignListPermission) {
            return <Alert>You do not have sufficient permissions to create or view a list design.</Alert>
        }

        return (
            <>
                {hasDesignListPermission &&
                    <ListDesignerPanels
                        model={model}
                        onCancel={this.onCancel}
                    />
                }
            </>
        );
    }
}