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
import { ActionURL, PermissionTypes, Security } from '@labkey/api';
import {
    Alert,
    LoadingSpinner,
    ListDesignerPanels,
    ListModel,
    fetchListDesign,
    getListIdFromDomainId,
    BeforeUnload,
    withServerContext,
} from '@labkey/components';

import '../DomainDesigner.scss';

// eslint-disable-next-line @typescript-eslint/no-empty-interface
interface Props {}

interface State {
    hasDesignListPermission?: boolean;
    isLoadingModel: boolean;
    message?: string;
    model?: ListModel;
}

export class ListDesigner extends React.Component<Props, State> {
    private _dirty = false;

    readonly state: State = { isLoadingModel: true };

    componentDidMount(): void {
        const listId = ActionURL.getParameter('listId');

        Security.getUserPermissions({
            success: data => {
                this.setState({
                    hasDesignListPermission:
                        data.container.effectivePermissions.indexOf(PermissionTypes.DesignList) > -1,
                });
            },
            failure: error => {
                this.setState({
                    message: error.exception,
                    hasDesignListPermission: false,
                });
            },
        });

        fetchListDesign(listId)
            .then(model => {
                this.setState({ model, isLoadingModel: false });
            })
            .catch(error => {
                this.setState({ message: error.exception, isLoadingModel: false });
            });
    }

    handleWindowBeforeUnload = (event): void => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    onCancel = (): void => {
        this.navigate(() => Promise.resolve(ActionURL.buildURL('list', 'begin')));
    };

    onComplete = (model: ListModel): void => {
        this.navigate(async () => {
            if (model.listId > 0) {
                return ActionURL.buildURL('list', 'grid', undefined, { listId: model.listId });
            }

            try {
                // If the model comes back without the newly saved listId, query to get it
                const listId = await getListIdFromDomainId(model.domain.domainId);
                return ActionURL.buildURL('list', 'grid', undefined, { listId });
            } catch (e) {
                // Bail out and go to the list-begin page
            }

            return ActionURL.buildURL('list', 'begin');
        }, model);
    };

    onChange = (): void => {
        this._dirty = true;
    };

    navigate = async (returnUrlProvider: () => Promise<string>, model?: ListModel): Promise<void> => {
        this._dirty = false;

        window.location.href = this.getReturnUrl(model) ?? (await returnUrlProvider());
    };

    getReturnUrl = (model?: ListModel): string => {
        const returnUrl = ActionURL.getReturnUrl();
        if (!returnUrl || !model) return returnUrl;

        // Issue 47356: Rewrite returnURL in the event of a list name change
        const { action, containerPath, controller } = ActionURL.getPathFromLocation(returnUrl);
        if (controller?.toLowerCase() === 'list' && action?.toLowerCase() === 'grid') {
            const parameters = ActionURL.getParameters(returnUrl);
            if (parameters.hasOwnProperty('name') && model.name && parameters.name !== model.name) {
                parameters.name = model.name;
                return ActionURL.buildURL(controller, action, containerPath, parameters);
            }
        }

        return returnUrl;
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
                />
            </BeforeUnload>
        );
    }
}

export const App = withServerContext(ListDesigner);
