/*
 * Copyright (c) 2019-2026 LabKey Corporation
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

import React, { FC } from 'react';
import { ActionURL, getServerContext } from '@labkey/api';
import {
    Alert,
    AppContexts,
    BeforeUnload,
    DomainDesign,
    DomainException,
    DomainForm,
    fetchDomain,
    FormButtons,
    LoadingSpinner,
    Modal,
    resolveErrorMessage,
    saveDomain,
} from '@labkey/components';

import '../DomainDesigner.scss';

interface IAppState {
    badDomain: DomainDesign;
    domain: DomainDesign;
    includeWarnings: boolean;
    message?: string;
    showConfirm: boolean;
    showWarnings: boolean;
    submitting: boolean;
}

class DomainDesigner extends React.PureComponent<any, Partial<IAppState>> {
    private _dirty = false;

    constructor(props) {
        super(props);

        const { domainId, schemaName, queryName } = ActionURL.getParameters();
        let message;
        if ((!schemaName || !queryName) && !domainId) {
            message = 'Missing required parameter: domainId or schemaName and queryName.';
        }

        this.state = {
            message,
            submitting: false,
            showConfirm: false,
            includeWarnings: true,
        };
    }

    componentDidMount(): void {
        const { domainId, schemaName, queryName } = ActionURL.getParameters();

        if ((schemaName && queryName) || domainId) {
            fetchDomain(domainId, schemaName, queryName)
                .then(domain => {
                    this.setState(() => ({ domain }));
                })
                .catch(error => {
                    this.setState(() => ({ message: error.exception }));
                });
        }
    }

    handleWindowBeforeUnload = (event): void => {
        if (this._dirty) {
            event.returnValue = 'Changes you made may not be saved.';
        }
    };

    submitHandler = (): void => {
        const { domain, submitting, includeWarnings } = this.state;

        if (submitting) {
            return;
        }

        this.setState(() => ({ submitting: true }));

        saveDomain({ domain, options: { domainId: domain.domainId }, includeWarnings })
            .then(savedDomain => {
                this.setState(() => ({
                    domain: savedDomain,
                    submitting: false,
                }));

                this.navigate();
            })
            .catch(response => {
                const exception = resolveErrorMessage(response);
                const badDomain = exception
                    ? domain.set('domainException', DomainException.create({ exception }, 'Error'))
                    : response;

                // if there are only warnings then show ConfirmModel
                if (badDomain.domainException.severity === 'Warning') {
                    this.setState(() => ({
                        showWarnings: true,
                        badDomain,
                    }));
                } else {
                    this.setState(() => ({
                        domain: badDomain,
                        submitting: false,
                    }));
                }
            });
    };

    submitAndNavigate = (): void => {
        this.submitHandler();
    };

    confirmWarningAndNavigate = (): void => {
        this.setState(
            () => ({
                includeWarnings: false,
                showWarnings: false,
                submitting: false,
            }),
            () => {
                this.submitHandler();
            }
        );
    };

    onSubmitWarningsCancel = (): void => {
        this.setState(() => ({
            showWarnings: false,
            submitting: false,
        }));
    };

    onChangeHandler = (newDomain, dirty): void => {
        this._dirty = this._dirty || dirty; // if the state is already dirty, leave it as such
        this.setState(() => ({ domain: newDomain }));
    };

    onCancelBtnHandler = (): void => {
        if (this._dirty) {
            this.setState(() => ({ showConfirm: true }));
        } else {
            this.navigate();
        }
    };

    navigate = (): void => {
        this._dirty = false;
        const redirectUrl = ActionURL.getReturnUrl() || ActionURL.buildURL('project', 'begin', getServerContext().container.path);
        window.location.href = ActionURL.buildURL('core', 'safeRedirect', undefined, { returnUrl: redirectUrl });
    };

    renderWarningConfirm() {
        const { badDomain } = this.state;
        const errors = badDomain.domainException.errors;
        const question = <p> There are issues with the following fields that you may wish to resolve: </p>;
        const warnings = errors
            .map(error => {
                return <li> {error.message} </li>;
            })
            .toArray();

        // TODO this doc link is specimen specific, we should find a way to pass this in via the domain kind or something like that
        const rollupURI = getServerContext().helpLinkPrefix + 'specimenCustomProperties';
        const suggestion = (
            <p>
                See the following documentation page for further details: <br />
                <a href={rollupURI} rel="noopener noreferrer" target="_blank">
                    {' '}
                    Specimen properties and rollup rules
                </a>
            </p>
        );

        return (
            <Modal
                cancelText="No, edit and resolve issues"
                confirmClass="btn-primary"
                confirmText="Yes, save changes"
                onCancel={this.onSubmitWarningsCancel}
                onConfirm={this.confirmWarningAndNavigate}
                title="Save without resolving issues?"
            >
                {question}
                <ul>{warnings}</ul>
                {suggestion}
            </Modal>
        );
    }

    render() {
        const { domain, message, showConfirm, showWarnings, submitting } = this.state;
        const isLoading = domain === undefined && message === undefined;

        if (isLoading) {
            return <LoadingSpinner />;
        }

        return (
            <BeforeUnload beforeunload={this.handleWindowBeforeUnload}>
                <div className="domain-designer">
                    {showConfirm && (
                        <Modal
                            cancelText="No, Discard Changes"
                            confirmClass="btn-primary"
                            confirmText="Yes, Save Changes"
                            onCancel={this.navigate}
                            onConfirm={this.submitAndNavigate}
                            title="Keep unsaved changes?"
                        >
                            You have made changes to this domain that have not yet been saved. Do you want to save these
                            changes before leaving?
                        </Modal>
                    )}
                    {showWarnings && this.renderWarningConfirm()}
                    {domain && domain.instructions && (
                        <div className="panel panel-default">
                            <div className="panel-heading">Instructions</div>
                            <div className="panel-body">{domain.instructions}</div>
                        </div>
                    )}
                    {domain && (
                        <DomainForm
                            domain={domain}
                            domainFormDisplayOptions={{
                                hideInferFromFile: true,
                            }}
                            headerTitle="Fields"
                            onChange={this.onChangeHandler}
                        />
                    )}
                    {message && <Alert bsStyle="danger">{message}</Alert>}
                    {domain && (
                        <FormButtons sticky={false}>
                            <button
                                className="cancel-button btn btn-default"
                                onClick={this.onCancelBtnHandler}
                                type="button"
                            >
                                Cancel
                            </button>
                            <button
                                className="save-button btn btn-primary"
                                disabled={submitting}
                                onClick={this.submitAndNavigate}
                                type="button"
                            >
                                Save
                            </button>
                        </FormButtons>
                    )}
                </div>
            </BeforeUnload>
        );
    }
}

export const App: FC = () => (
    <AppContexts>
        <DomainDesigner />
    </AppContexts>
);
