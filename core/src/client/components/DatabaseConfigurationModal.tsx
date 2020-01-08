import React, { PureComponent } from 'react';
import {Button, ButtonGroup, DropdownButton, FormControl, MenuItem, Modal, Panel} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import {ActionURL, Ajax} from "@labkey/api";


export default class DatabaseConfigurationModal extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            passwordRules: {
                Weak: "",
                Strong: ""
            }
        };
    }

    componentDidMount = () => {
        Ajax.request({
            url: ActionURL.buildURL("login", "getDbLoginProperties"),
            method : 'GET',
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                const response = JSON.parse(result.response);
                this.setState({...response}, () => {console.log(this.state)});
            }
        })
    };

    handleChange = (event) => {
        const {name, value} = event.target;
        this.setState((prevState) => ({
            ...prevState,
            currentSettings: {
                ...prevState.currentSettings,
                [name]: value
            }
        }));
    };

    saveChanges = () => {
        const {expiration, strength} = this.state.currentSettings;

        const form = {
            strength: strength,
            expiration: expiration
        };
        console.log(this.state);

        Ajax.request({
            url: ActionURL.buildURL("login", "SaveDbLoginProperties"),
            method : 'POST',
            jsonData: form,
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
                this.props.closeModal();
            }
        })
    };

    render () {
        const {canEdit} = this.props;
        const passwordStrength = (this.state.currentSettings && this.state.currentSettings.strength);
        const expiration = (this.state.currentSettings && this.state.currentSettings.expiration);
        const optionsMap = {
            Never: "Never",
            FiveSeconds:"Every five seconds — for testing",
            ThreeMonths:"Every three months",
            SixMonths:"Every six months",
            OneYear:"Every twelve months"
        };

        return (
            <Modal show={true} onHide={() => {}} >
                <Modal.Header>
                    <Modal.Title>
                        {"Configure Database Authentication"}
                        <FontAwesomeIcon
                            size='sm'
                            icon={faTimes}
                            style={{float: "right", marginTop: "5px"}}
                            onClick={this.props.closeModal}
                        />
                    </Modal.Title>
                </Modal.Header>

                <Modal.Body>
                    <strong> Weak </strong>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Weak }} />
                    </div>

                    <br/>

                    <strong> Strong </strong>
                    <div>
                        <div dangerouslySetInnerHTML={{ __html: this.state.passwordRules.Strong }} />
                    </div>


                    <div className="dbAuthRowHeights">
                        <span>
                            Password Strength:
                        </span>

                        <span className="dbAuthMoveRight">
                            <ButtonGroup onClick={this.handleChange}>
                                <Button data-key='1' value="Weak" name="strength" active={passwordStrength == "Weak"} disabled={!canEdit}>
                                    Weak
                                </Button>
                                <Button data-key='2' value="Strong" name="strength" active={passwordStrength == "Strong"} disabled={!canEdit}>
                                    Strong
                                </Button>
                            </ButtonGroup>
                        </span>
                    </div>

                    <div className="dbAuthRowHeights">
                        <span>
                            Password Expiration:
                        </span>

                        <span className="dbAuthMoveRight">
                            {canEdit ?
                                <FormControl
                                    componentClass="select"
                                    name="expiration"
                                    placeholder="select"
                                    onChange={this.handleChange}
                                    value={expiration}
                                    >
                                    <option value="Never">Never</option>
                                    <option value="FiveSeconds">Every five seconds — for testing</option>
                                    <option value="ThreeMonths">Every three months</option>
                                    <option value="SixMonths">Every six months</option>
                                    <option value="OneYear">Every twelve months</option>
                                </FormControl>
                                : optionsMap[expiration]
                            }


                        </span>
                    </div>

                    <hr/>

                    <div style={{float: "right"}}>
                        <a target="_blank" href={this.state.helpLink} style={{marginRight: "10px"}}> {"More about authentication"} </a>
                        {canEdit && <Button className={'labkey-button primary'} onClick={this.saveChanges}>Apply</Button>}
                    </div>

                    <Button
                        className={'labkey-button'}
                        onClick={this.props.closeModal}
                        style={{marginLeft: '10px'}}
                    >
                        {canEdit ? "Cancel" : "Close"}
                    </Button>
                </Modal.Body>

            </Modal>
        );
    }
}
