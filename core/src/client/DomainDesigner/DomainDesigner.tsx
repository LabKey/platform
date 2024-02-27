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

import React from 'react';
import { Panel } from 'react-bootstrap';
import { ActionURL, getServerContext } from '@labkey/api';
import {
    LoadingSpinner,
    Alert,
    DomainForm,
    DomainDesign,
    fetchDomain,
    FormButtons,
    saveDomain,
    BeforeUnload,
    resolveErrorMessage,
    DomainException,
    Modal,
} from '@labkey/components';

import '../DomainDesigner.scss';

interface IAppState {
    domain: DomainDesign;
    message?: string;
    showConfirm: boolean;
    submitting: boolean;
    includeWarnings: boolean;
    showWarnings: boolean;
    badDomain: DomainDesign;
}

export class App extends React.PureComponent<any, Partial<IAppState>> {
    private _dirty: boolean = false;

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

        const returnUrl = ActionURL.getReturnUrl();
        window.location.href = returnUrl || ActionURL.buildURL('project', 'begin', getServerContext().container.path);
    };

    renderWarningConfirm() {
        const { badDomain } = this.state;
        const errors = badDomain.domainException.errors;
        const question = <p> There are issues with the following fields that you may wish to resolve: </p>;
        const warnings = errors.map(error => {
            return <li> {error.message} </li>;
        });

        // TODO this doc link is specimen specific, we should find a way to pass this in via the domain kind or something like that
        const rollupURI = getServerContext().helpLinkPrefix + 'specimenCustomProperties';
        const suggestion = (
            <p>
                See the following documentation page for further details: <br />
                <a href={rollupURI} target="_blank" rel="noopener noreferrer">
                    {' '}
                    Specimen properties and rollup rules
                </a>
            </p>
        );

        return (
            <Modal
                title="Save without resolving issues?"
                confirmClass="btn-primary"
                onConfirm={this.confirmWarningAndNavigate}
                onCancel={this.onSubmitWarningsCancel}
                cancelText="No, edit and resolve issues"
                confirmText="Yes, save changes"
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
                            title="Keep unsaved changes?"
                            confirmClass="btn-primary"
                            onConfirm={this.submitAndNavigate}
                            onCancel={this.navigate}
                            cancelText="No, Discard Changes"
                            confirmText="Yes, Save Changes"
                        >
                            You have made changes to this domain that have not yet been saved. Do you want to save these
                            changes before leaving?
                        </Modal>
                    )}
                    {showWarnings && this.renderWarningConfirm()}
                    {domain && domain.instructions && (
                        <Panel>
                            <Panel.Heading>Instructions</Panel.Heading>
                            <Panel.Body>{domain.instructions}</Panel.Body>
                        </Panel>
                    )}
                    {domain && (
                        <DomainForm
                            headerTitle="Fields"
                            domain={domain}
                            domainFormDisplayOptions={{
                                hideInferFromFile: true,
                            }}
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
