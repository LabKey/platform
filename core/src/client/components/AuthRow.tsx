import React, { PureComponent } from 'react';
import classNames from 'classnames';
import { Col, Modal, Button } from 'react-bootstrap';
import { DragDropHandle } from '@labkey/components';

import DynamicConfigurationModal from './DynamicConfigurationModal';
import DatabaseConfigurationModal from './DatabaseConfigurationModal';
import { AuthConfig, AuthConfigProvider } from './models';

interface Props {
    authConfig: AuthConfig;
    canEdit: boolean;
    configType?: string;
    draggable: boolean;
    index?: string;
    isDragging?: boolean;
    modalType?: AuthConfigProvider;
    onDelete?: Function;
    toggleModalOpen?: Function;
    updateAuthRowsAfterSave?: Function;
}

interface State {
    deleteModalOpen: boolean;
    editModalOpen: boolean;
}

export default class AuthRow extends PureComponent<Props, Partial<State>> {
    constructor(props) {
        super(props);
        this.state = {
            editModalOpen: false,
            deleteModalOpen: false,
        };
    }

    onToggleModal = (localModalType: string, modalOpen: boolean): void => {
        this.setState(() => ({
            [localModalType]: !modalOpen,
        }));
    };

    render() {
        const {
            authConfig,
            modalType,
            configType,
            canEdit,
            draggable,
            toggleModalOpen,
            updateAuthRowsAfterSave,
            onDelete,
            isDragging,
        } = this.props;
        const isDatabaseAuth = authConfig.provider === 'Database';

        const handle =
            draggable && canEdit ? (
                <DragDropHandle
                    highlighted={
                        isDragging
                            ? true
                            : undefined /* use undefined instead of false to allow for css to handle the highlight color for hover*/
                    }
                />
            ) : null;

        const enabledField = authConfig.enabled ? (
            <>
                <span className="fa fa-circle" style={{ color: '#75B666' }} /> &nbsp; Enabled
            </>
        ) : (
            <>
                <span className="fa fa-circle" style={{ color: '#999999' }} /> &nbsp; Disabled
            </>
        );

        const deleteIcon =
            canEdit && !isDatabaseAuth ? (
                <div
                    className="clickable deleteIcon"
                    onClick={() => {
                        this.setState({ deleteModalOpen: true });
                        toggleModalOpen(true);
                    }}
                >
                    <span className="fa fa-times-circle" />
                </div>
            ) : null;

        const editOrViewIcon = canEdit ? (
            <div
                className="clickable editOrViewIcon"
                onClick={() => {
                    this.onToggleModal('editModalOpen', this.state.editModalOpen);
                    toggleModalOpen(true);
                }}
            >
                <span className="fa fa-pencil" />
            </div>
        ) : (
            <div className="clickable" onClick={() => this.onToggleModal('editModalOpen', this.state.editModalOpen)}>
                <span className="fa fa-info-circle" />
            </div>
        );

        let modal;
        if (isDatabaseAuth) {
            modal = (
                <DatabaseConfigurationModal
                    closeModal={() => {
                        this.onToggleModal('editModalOpen', this.state.editModalOpen);
                        toggleModalOpen(false);
                    }}
                    canEdit={canEdit}
                />
            );
        } else {
            modal = (
                <DynamicConfigurationModal
                    authConfig={authConfig}
                    configType={configType}
                    modalType={modalType}
                    canEdit={canEdit}
                    closeModal={() => {
                        this.onToggleModal('editModalOpen', this.state.editModalOpen);
                        if (canEdit) {
                            toggleModalOpen(false);
                        }
                    }}
                    updateAuthRowsAfterSave={updateAuthRowsAfterSave}
                />
            );
        }

        const deleteModal = (
            <Modal
                show={true}
                onHide={() => {
                    this.onToggleModal('deleteModalOpen', this.state.deleteModalOpen);
                    toggleModalOpen(false);
                }}
            >
                <Modal.Header closeButton>
                    <Modal.Title>Permanently delete {authConfig.provider} configuration?</Modal.Title>
                </Modal.Header>
                <div className="auth-row__delete-modal">
                    <div className="auth-row__delete-modal__textBox modal-body">
                        <p>
                            Deleting this authentication configuration will remove all settings associated with it. To
                            enable it again, the authentication configuration will need to be re-configured.
                        </p>
                        <p>Deletion cannot be undone.</p>
                    </div>
                    <div className="auth-row__delete-modal-bottom">
                        <Button
                            className="labkey-button auth-row__delete-modal__cancel"
                            onClick={() => {
                                this.onToggleModal('deleteModalOpen', this.state.deleteModalOpen);
                                toggleModalOpen(false);
                            }}
                        >
                            Cancel
                        </Button>

                        <Button className="labkey-button primary auth-row__confirm-delete" onClick={onDelete}>
                            Yes, delete
                        </Button>
                    </div>
                </div>
            </Modal>
        );

        return (
            <div>
                <div
                    className={classNames('auth-row domain-field-row domain-row-border-default', {
                        dragging: isDragging,
                    })}
                >
                    <div className="domain-row-container">
                        <div className="domain-row-handle">{handle}</div>

                        <div className="domain-row-main">
                            <Col xs={9} className="domain-row-base-fields">
                                <Col xs={4} className="description auth-row__field">
                                    {authConfig.description}
                                </Col>
                                <Col xs={4} className="details auth-row__field">
                                    {authConfig.details}
                                </Col>
                                <Col xs={3} className="provider auth-row__field">
                                    {authConfig.provider}
                                </Col>
                            </Col>

                            <Col xs={1} />

                            <Col xs={2} className="domain-row-base-fields">
                                <Col xs={7} className="enabled auth-row__field">
                                    {enabledField}
                                </Col>

                                <Col xs={1} className="delete auth-row__field">
                                    {deleteIcon}
                                </Col>

                                <Col xs={3} className="editOrView auth-row__field">
                                    {editOrViewIcon}
                                </Col>
                            </Col>

                            {this.state.editModalOpen && modal}
                            {this.state.deleteModalOpen && deleteModal}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}
