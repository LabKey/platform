import React, { PureComponent } from 'react';
import { Col, Modal, Button } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
    faPencilAlt,
    faInfoCircle,
    faTimesCircle,
    faGripVertical,
    faCircle
} from '@fortawesome/free-solid-svg-icons';

import DynamicConfigurationModal from './DynamicConfigurationModal';
import DatabaseConfigurationModal from './DatabaseConfigurationModal';
import {AuthConfig, AuthConfigProvider} from "../AuthenticationConfiguration/models";

interface Props{
    authConfig: AuthConfig;
    index?: string;
    modalType?: AuthConfigProvider;
    configType?: string;
    canEdit: boolean;
    draggable: boolean;
    onDelete?: Function;
    toggleModalOpen?: Function;
    updateAuthRowsAfterSave?: Function;
}

interface State {
    editModalOpen: boolean;
    deleteModalOpen: boolean;
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
            onDelete
        } = this.props;
        const isDatabaseAuth = authConfig.provider == 'Database';

        const handle = draggable && canEdit ? <LightupHandle /> : null;

        const enabledField = authConfig.enabled ? (
            <>
                <FontAwesomeIcon icon={faCircle} color="#75B666" /> &nbsp; Enabled
            </>
        ) : (
            <>
                <FontAwesomeIcon icon={faCircle} color="#999999" /> &nbsp; Disabled
            </>
        );

        const deleteIcon =
            canEdit && !isDatabaseAuth ? (
                <div className="clickable deleteIcon" onClick={() => this.setState({deleteModalOpen: true})}>
                    <FontAwesomeIcon icon={faTimesCircle} />
                </div>
            ) : null;

        const editOrViewIcon = canEdit ? (
            <div
                className="clickable editOrViewIcon"
                onClick={() => {
                    this.onToggleModal("editModalOpen", this.state.editModalOpen);
                    toggleModalOpen(true);
                }}>
                <FontAwesomeIcon size="1x" icon={faPencilAlt} />
            </div>
        ) : (
            <div className="clickable" onClick={() => this.onToggleModal("editModalOpen", this.state.editModalOpen)}>
                <FontAwesomeIcon size="1x" icon={faInfoCircle} />
            </div>
        );

        let modal;
        if (isDatabaseAuth) {
            modal =
                <DatabaseConfigurationModal
                    closeModal={() => {
                        this.onToggleModal("editModalOpen", this.state.editModalOpen);
                        toggleModalOpen(false);
                    }}
                    canEdit={canEdit}
                />
        } else {

            modal =
                <DynamicConfigurationModal
                    authConfig={authConfig}
                    configType={configType}
                    modalType={modalType}
                    canEdit={canEdit}
                    closeModal={() => {
                        this.onToggleModal("editModalOpen", this.state.editModalOpen);
                        if (canEdit) {
                            toggleModalOpen(false);
                        }
                    }}
                    updateAuthRowsAfterSave={updateAuthRowsAfterSave}
                />
        }

        const deleteModal =
            <Modal show={true} onHide={() => this.onToggleModal("deleteModalOpen", this.state.deleteModalOpen)}>
                <Modal.Header closeButton>
                    <Modal.Title>
                        Warning
                    </Modal.Title>
                </Modal.Header>
                <div className={"auth-row__delete-modal"}>
                    <div>
                        {`Are you sure you want to delete authentication configuration ${authConfig.description}?`}
                    </div>

                    <Button
                        className="labkey-button primary auth-row__confirm-delete"
                        onClick={onDelete}
                    >
                        Yes
                    </Button>
                </div>
            </Modal>;

        return (
            <div className="row-container">
                <div className="auth-row">
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

class LightupHandle extends PureComponent {
    render() {
        return (
            <div>
                <FontAwesomeIcon
                    size="lg"
                    icon={faGripVertical}
                />
            </div>
        );
    }
}
