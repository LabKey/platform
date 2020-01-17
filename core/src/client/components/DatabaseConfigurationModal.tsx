import React, { PureComponent } from 'react';
import { Button, ButtonGroup, FormControl, Modal } from 'react-bootstrap';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faTimes } from '@fortawesome/free-solid-svg-icons';
import { ActionURL, Ajax } from '@labkey/api';


interface Props extends AuthConfig {
    closeModal: Function;
    canEdit: boolean;
}

interface State {
    passwordRules: Record<string, string>;
    helpLink: string;
    currentSettings: Record<string, string>;
}

export default class DatabaseConfigurationModal extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            passwordRules: {
                Weak: '',
                Strong: '',
            },
            helpLink: null,
            currentSettings: {strength: "", expiration: ""}
        };
    }

    componentDidMount = (): void => {
        Ajax.request({
            url: ActionURL.buildURL('login', 'getDbLoginProperties'),
            method: 'GET',
            scope: this,
            failure: function(error) {
                alert('Error: ' + error);
            },
            success: function(result) {
                const response = JSON.parse(result.response);
                this.setState({ ...response });
            },
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
        const { expiration, strength } = this.state.currentSettings;

        const form = {
            strength,
            expiration,
        };

        Ajax.request({
            url: ActionURL.buildURL('login', 'SaveDbLoginProperties'),
            method: 'POST',
            jsonData: form,
            scope: this,
            failure: function(error) {
                alert('Error: ' + error);
            },
            success: function(result) {
                this.props.closeModal();
            },
        });
    };

    render() {
        const { canEdit } = this.props;
        const passwordStrength = this.state.currentSettings && this.state.currentSettings.strength;
        const expiration = this.state.currentSettings && this.state.currentSettings.expiration;
        const optionsMap = {
            Never: 'Never',
            FiveSeconds: 'Every five seconds — for testing',
            ThreeMonths: 'Every three months',
            SixMonths: 'Every six months',
            OneYear: 'Every twelve months',
        };

        return (
            <Modal show={true} onHide={() => {}}>
                <Modal.Header>
                    <Modal.Title>
                        Configure Database Authentication
                        <FontAwesomeIcon
                            size="sm"
                            icon={faTimes}
                            className="modal__close-icon"
                            onClick={() => this.props.closeModal()}
                        />
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <div className="bold-text"> Weak </div>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Weak }} />
                    </div>

                    <br />

                    <div className="bold-text"> Strong </div>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Strong }} />
                    </div>

                    <div className="database-modal__field-row">
                        <span>Password Strength:</span>

                        <span className="database-modal__field">
                            <ButtonGroup onClick={this.handleChange}>
                                <Button
                                    data-key="1"
                                    value="Weak"
                                    name="strength"
                                    active={passwordStrength == 'Weak'}
                                    disabled={!canEdit}>
                                    Weak
                                </Button>
                                <Button
                                    data-key="2"
                                    value="Strong"
                                    name="strength"
                                    active={passwordStrength == 'Strong'}
                                    disabled={!canEdit}>
                                    Strong
                                </Button>
                            </ButtonGroup>
                        </span>
                    </div>



                    <div className="database-modal__field-row">
                        <span>Password Expiration:</span>

                        <span className="database-modal__field">
                            {canEdit ? (
                                <FormControl
                                    componentClass="select"
                                    name="expiration"
                                    placeholder="select"
                                    onChange={this.handleChange}
                                    value={expiration}>
                                    <option value="Never">Never</option>
                                    <option value="FiveSeconds">Every five seconds — for testing</option>
                                    <option value="ThreeMonths">Every three months</option>
                                    <option value="SixMonths">Every six months</option>
                                    <option value="OneYear">Every twelve months</option>
                                </FormControl>
                            ) : (
                                optionsMap[expiration]
                            )}
                        </span>
                    </div>

                    <div className="database-modal__bottom">
                        <div className="modal__bottom-buttons">
                            <a target="_blank" href={this.state.helpLink} className="modal__help-link">
                                {'More about authentication'}
                            </a>
                            {canEdit && (
                                <Button className="labkey-button primary" onClick={this.saveChanges}>
                                    Apply
                                </Button>
                            )}
                        </div>

                        <Button className="labkey-button modal__save-button" onClick={this.props.closeModal}>
                            {canEdit ? 'Cancel' : 'Close'}
                        </Button>
                    </div>
                </Modal.Body>
            </Modal>
        );
    }
}
