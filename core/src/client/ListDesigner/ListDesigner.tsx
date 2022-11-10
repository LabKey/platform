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
import React, { ReactNode } from 'react';
import { ActionURL, getServerContext, PermissionTypes, Security } from '@labkey/api';
import {
    Alert,
    LoadingSpinner,
    ListDesignerPanels,
    ListModel,
    fetchListDesign,
    getListIdFromDomainId,
    BeforeUnload,
} from '@labkey/components';

import '../DomainDesigner.scss';

// eslint-disable-next-line @typescript-eslint/no-empty-interface
interface Props {}

interface State {
    hasDesignListPermission?: boolean;
    isLoadingModel: boolean;
    message?: string;
    model?: ListModel;
};

export class App extends React.Component<Props, State> {
    private _dirty = false;

    constructor(props) {
        super(props);

        this.state = {
            isLoadingModel: true,
        };
    }

    componentDidMount(): void {
        const listId = ActionURL.getParameter('listId');

        Security.getUserPermissions({
            containerPath: getServerContext().container.path,
            success: data => {
                this.setState(() => ({
                    hasDesignListPermission:
                        data.container.effectivePermissions.indexOf(PermissionTypes.DesignList) > -1,
                }));
            },
            failure: error => {
                this.setState(() => ({
                    message: error.exception,
                    hasDesignListPermission: false,
                }));
            },
        });

        fetchListDesign(listId)
            .then((model: ListModel) => {
                this.setState(() => ({ model, isLoadingModel: false }));
            })
            .catch(error => {
                this.setState(() => ({ message: error.exception, isLoadingModel: false }));
            });
    }

    handleWindowBeforeUnload = (event): void => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    onCancel = (): void => {
        this.navigate(ActionURL.buildURL('list', 'begin', getServerContext().container.path));
    };

    onComplete = (model: ListModel): void => {
        this.navigateOnComplete(model);
    };

    navigateOnComplete(model: ListModel): void {
        // if the model comes back to here without the newly saved listId, query to get it
        if (model.listId && model.listId > 0) {
            this.navigate(
                ActionURL.buildURL('list', 'grid', getServerContext().container.path, { listId: model.listId })
            );
        } else {
            getListIdFromDomainId(model.domain.domainId)
                .then(listId => {
                    this.navigate(ActionURL.buildURL('list', 'grid', getServerContext().container.path, { listId }));
                })
                .catch(error => {
                    // bail out and go to the list-begin page
                    this.navigate(ActionURL.buildURL('list', 'begin', getServerContext().container.path));
                });
        }
    }

    onChange = (): void => {
        this._dirty = true;
    };

    navigate = (defaultUrl: string): void => {
        this._dirty = false;

        const returnUrl = ActionURL.getReturnUrl();
        window.location.href = returnUrl || defaultUrl;
    };

    render(): ReactNode {
        const { isLoadingModel, hasDesignListPermission, message, model } = this.state;

        if (message) {
            return <Alert>{message}</Alert>;
        }

        // set as loading until model is loaded and we know if the user has DesignListPerm
        if (isLoadingModel || hasDesignListPermission === undefined) {
            return <LoadingSpinner />;
        }

        if (!hasDesignListPermission) {
            return <Alert>You do not have sufficient permissions to create or edit a list design.</Alert>;
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                <ListDesignerPanels
                    initModel={model}
                    onCancel={this.onCancel}
                    onComplete={this.onComplete}
                    onChange={this.onChange}
                    useTheme
                    successBsStyle="primary"
                />
            </BeforeUnload>
        );
    }
}
