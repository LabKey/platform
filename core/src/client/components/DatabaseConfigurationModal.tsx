import React, { PureComponent } from 'react';
import { Alert, Button, ButtonGroup, FormControl, Modal } from 'react-bootstrap';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { resolveErrorMessage } from '@labkey/components';

import { DatabasePasswordRules, DatabasePasswordSettings } from './models';

const OPTIONS_MAP = {
    Never: 'Never',
    FiveSeconds: 'Every five seconds — for testing',
    ThreeMonths: 'Every three months',
    SixMonths: 'Every six months',
    OneYear: 'Every twelve months',
};

interface Props {
    closeModal: () => void;
    canEdit: boolean;
}

interface State {
    error: string;
    initError: string;
    passwordRules: DatabasePasswordRules;
    currentSettings: DatabasePasswordSettings;
    helpLink: string;
}

export default class DatabaseConfigurationModal extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            error: undefined,
            initError: undefined,
            passwordRules: { Weak: '', Strong: '' },
            helpLink: null,
            currentSettings: { strength: '', expiration: '' },
        };
    }

    componentDidMount = (): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'getDbLoginProperties.api'),
            success: Utils.getCallbackWrapper(response => {
                this.setState({ ...response });
            }),
            failure: Utils.getCallbackWrapper(error => {
                console.error('Failed to get login properties', error);
                this.setState({
                    initError: resolveErrorMessage(error),
                });
            }, undefined, true),
        });
    };

    handleChange = event => {
        const { name, value } = event.target;
        this.setState(prevState => ({
            ...prevState,
            currentSettings: {
                ...prevState.currentSettings,
                [name]: value,
            },
        }));
    };

    saveChanges = (): void => {
        if (this.state.error) {
            this.setState({ error: undefined });
        }

        Ajax.request({
            url: ActionURL.buildURL('login', 'saveDbLoginProperties.api'),
            method: 'POST',
            jsonData: this.state.currentSettings,
            success: Utils.getCallbackWrapper(() => {
                this.props.closeModal();
            }),
            failure: Utils.getCallbackWrapper(error => {
                console.error('Failed to save login properties', error);
                this.setState({
                    error: resolveErrorMessage(error),
                });
            }, undefined, true),
        });
    };

    render() {
        const { canEdit } = this.props;
        const { currentSettings, error, initError } = this.state;
        const { strength, expiration } = currentSettings;
        const hasError = error !== undefined || initError !== undefined;
        const allowEdit = canEdit && initError === undefined;

        return (
            <Modal show={true} onHide={this.props.closeModal}>
                <Modal.Header closeButton>
                    <Modal.Title>
                        Configure Database Authentication
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    {hasError && <Alert bsStyle="danger">{error || initError}</Alert>}

                    <div className="database-modal__field-row">
                        <span>Password Strength:</span>

                        <span className="database-modal__field">
                            <ButtonGroup onClick={this.handleChange}>
                                <Button
                                    value="Weak"
                                    name="strength"
                                    active={strength == 'Weak'}
                                    disabled={!allowEdit}>
                                    Weak
                                </Button>
                                <Button
                                    value="Strong"
                                    name="strength"
                                    active={strength == 'Strong'}
                                    disabled={!allowEdit}>
                                    Strong
                                </Button>
                            </ButtonGroup>
                        </span>
                    </div>

                    {/* this.state.passwordRules.Weak is safe server-generated HTML */}
                    <div className="bold-text"> Weak </div>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Weak }} />
                    </div>

                    <br/>

                    {/* this.state.passwordRules.Strong is safe server-generated HTML */}
                    <div className="bold-text"> Strong </div>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Strong }} />
                    </div>

                    <br/>

                    <div className="database-modal__field-row">
                        <span>Password Expiration:</span>

                        <span className="database-modal__field">
                            {allowEdit ? (
                                <FormControl
                                    componentClass="select"
                                    name="expiration"
                                    placeholder="select"
                                    onChange={this.handleChange}
                                    value={expiration}
                                >
                                    {Object.keys(OPTIONS_MAP).map((option) =>
                                        <option value={option} key={option}>
                                            {OPTIONS_MAP[option]}
                                        </option>
                                    )}
                                </FormControl>
                            ) : (
                                OPTIONS_MAP[expiration]
                            )}
                        </span>
                    </div>

                    <div className="database-modal__bottom">
                        <div className="modal__bottom-buttons">
                            <a target="_blank" href={this.state.helpLink} className="modal__help-link" rel="noopener noreferrer">
                                More about authentication
                            </a>
                            {allowEdit && (
                                <Button className="labkey-button primary" onClick={this.saveChanges}>
                                    Apply
                                </Button>
                            )}
                        </div>

                        <Button className="labkey-button modal__save-button" onClick={this.props.closeModal}>
                            {allowEdit ? 'Cancel' : 'Close'}
                        </Button>
                    </div>
                </Modal.Body>
            </Modal>
        );
    }
}
