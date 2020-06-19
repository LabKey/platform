import React, { PureComponent } from 'react';
import { Button, ButtonGroup, FormControl, Modal } from 'react-bootstrap';
import { ActionURL, Ajax } from '@labkey/api';
import { DatabasePasswordRules, DatabasePasswordSettings } from "./models";

const OPTIONS_MAP = {
    Never: 'Never',
    FiveSeconds: 'Every five seconds — for testing',
    ThreeMonths: 'Every three months',
    SixMonths: 'Every six months',
    OneYear: 'Every twelve months',
};

interface Props {
    closeModal: Function;
    canEdit: boolean;
}

interface State {
    passwordRules: DatabasePasswordRules;
    currentSettings: DatabasePasswordSettings;
    helpLink: string;
}

export default class DatabaseConfigurationModal extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            passwordRules: { Weak: '', Strong: '' },
            helpLink: null,
            currentSettings: { strength: '', expiration: '' },
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
        Ajax.request({
            url: ActionURL.buildURL('login', 'SaveDbLoginProperties'),
            method: 'POST',
            jsonData: this.state.currentSettings,
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
        const { currentSettings } = this.state;
        const { strength, expiration } = currentSettings;

        return (
            <Modal show={true} onHide={this.props.closeModal}>
                <Modal.Header closeButton>
                    <Modal.Title>
                        Configure Database Authentication
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <div className="database-modal__field-row">
                        <span>Password Strength:</span>

                        <span className="database-modal__field">
                            <ButtonGroup onClick={this.handleChange}>
                                <Button
                                    value="Weak"
                                    name="strength"
                                    active={strength == 'Weak'}
                                    disabled={!canEdit}>
                                    Weak
                                </Button>
                                <Button
                                    value="Strong"
                                    name="strength"
                                    active={strength == 'Strong'}
                                    disabled={!canEdit}>
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
                            {canEdit ? (
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
