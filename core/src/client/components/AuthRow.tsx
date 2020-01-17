import React, { PureComponent } from 'react';

import { Col } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faPencilAlt, faInfoCircle, faTimesCircle, faGripVertical, faCircle } from '@fortawesome/free-solid-svg-icons';

import DynamicConfigurationModal from './DynamicConfigurationModal';
import DatabaseConfigurationModal from './DatabaseConfigurationModal';

interface Props extends AuthConfig {
    index?: string;
    modalType?: Record<string, any>;
    stateSection?: string;
    canEdit?: boolean;
    draggable?: boolean;
    deleteAction?: Function;
    toggleSomeModalOpen?: Function;
    updateAuthRowsAfterSave?: Function;
}

interface State {
    color?: boolean;
    modalOpen?: boolean;
    highlight?: boolean;
}

export default class AuthRow extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            color: false,
            modalOpen: false,
        };
    }

    onToggleModal = (toggled: string): void => {
        this.setState(() => ({
            [toggled]: !this.state[toggled],
            highlight: false,
        }));
    };

    onDeleteClick = (): void => {
        const response = confirm(
            'Are you sure you want to delete ' + this.props.description + ' authentication configuration?'
        );
        if (response) {
            this.props.deleteAction(this.props.configuration, this.props.stateSection);
        }
    };

    setHighlight = (isHighlighted: boolean): void => {
        if (this.state.modalOpen) {
            return;
        }
        this.setState({ highlight: isHighlighted });
    };

    render() {
        const { canEdit, draggable, provider, toggleSomeModalOpen } = this.props;
        const isDatabaseAuth = provider == 'Database';

        const handle = draggable && canEdit ? <LightupHandle highlight={this.state.highlight} /> : null;

        const enabledField = this.props.enabled ? (
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
                <div className="clickable" onClick={() => this.onDeleteClick()}>
                    <FontAwesomeIcon icon={faTimesCircle} color="#d9534f" />
                </div>
            ) : null;

        const editOrViewIcon = canEdit ? (
            <div
                className="clickable"
                onClick={() => {
                    this.onToggleModal('modalOpen');
                    toggleSomeModalOpen(true);
                }}>
                <FontAwesomeIcon size="1x" icon={faPencilAlt} />
            </div>
        ) : (
            <div className="clickable" onClick={() => this.onToggleModal('modalOpen')}>
                <FontAwesomeIcon size="1x" icon={faInfoCircle} color="#999999" />
            </div>
        );

        const dynamicModal = this.state.modalOpen && (
            <DynamicConfigurationModal
                {...this.props}
                type={this.props.modalType}
                closeModal={() => {
                    this.onToggleModal('modalOpen');
                    canEdit && toggleSomeModalOpen(false);
                }}
                updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
            />
        );

        const databaseModal = (
            <DatabaseConfigurationModal
                closeModal={() => {
                    this.onToggleModal('modalOpen');
                    toggleSomeModalOpen(false);
                }}
                canEdit={canEdit}
            />
        );

        const modal = this.state.modalOpen && isDatabaseAuth ? databaseModal : dynamicModal;

        return (
            <div className="row-container">
                <div
                    className="auth-row"
                    onMouseOver={() => {
                        this.setHighlight(true);
                    }}
                    onMouseOut={() => {
                        this.setHighlight(false);
                    }}>
                    <div className="domain-row-container">
                        <div className="domain-row-handle">{handle}</div>

                        <div className="domain-row-main">
                            <Col xs={9} className="domain-row-base-fields">
                                <Col xs={4} className="description auth-row__field">
                                    {this.props.description}
                                </Col>
                                <Col xs={4} className="details auth-row__field">
                                    {this.props.details}
                                </Col>
                                <Col xs={3} className="provider auth-row__field">
                                    {this.props.provider}
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

                            {modal}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}

interface HandleProps {
    highlight: boolean;
}

class LightupHandle extends PureComponent<HandleProps> {
    render() {
        const HIGHLIGHT_BLUE = '#2980B9';
        const NOT_HIGHLIGHT_GRAY = '#999999';

        return (
            <div>
                <FontAwesomeIcon
                    size="lg"
                    color={this.props.highlight ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY}
                    icon={faGripVertical}
                />
            </div>
        );
    }
}
