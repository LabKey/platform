import React, { PureComponent } from 'react';
import {Button, ButtonGroup, DropdownButton, FormControl, MenuItem, Modal, Panel} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faTimes} from "@fortawesome/free-solid-svg-icons";
import {ActionURL, Ajax} from "@labkey/api";


export default class DatabaseConfigurationModal extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            passwordStrength: "Weak",
            passwordExpiry: "Never"
        };
    }

    handleDropdownChange = (val) => {
        this.setState({ passwordExpiry: val });
    };

    selectRadio = (e) => {
        const passwordStrength = e.target.value;
        this.setState({passwordStrength});
    };

    saveChanges = () => {
        const form = {
            strength: this.state.passwordStrength,
            expiration: this.state.passwordExpiry
        };
        console.log(this.state);

        Ajax.request({
            url: ActionURL.buildURL("login", "ConfigureDbLogin"),
            method : 'POST',
            jsonData: form,
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
            }
        })
    };

    render () {
        const {passwordStrength} = this.state;

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
                        Passwords must be six non-whitespace characters or more and must not match your email address.
                    </div>

                    <strong> Strong </strong>
                    <div>
                    Passwords follow these rules:
                    </div>
                    <ul>
                        <li>Must be eight characters or more.</li>
                        <li>{"Must contain three of the following: lowercase letter (a-z), uppercase letter (A-Z), digit (0-9), or symbol (e.g., ! # $ % & / < = > ? @)."}</li>
                        <li>Must not contain a sequence of three or more characters from your email address, display name, first name, or last name.</li>
                        <li>Must not match any of your 10 previously used passwords.</li>
                    </ul>

                    <div className="dbAuthRowHeights">
                        <span>
                            Password Strength:
                        </span>

                        <span className="dbAuthMoveRight">
                            <ButtonGroup onClick={this.selectRadio}>
                                <Button data-key='1' value="Weak" active={passwordStrength == "Weak"}>
                                    Weak
                                </Button>
                                <Button data-key='2' value="Strong" active={passwordStrength == "Strong"}>
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
                            <DropdownButton id='dropdown-basic-button' title="Never" onSelect={this.handleDropdownChange}>

                                <MenuItem eventKey="Never">Never</MenuItem>
                                <MenuItem eventKey="FiveSeconds">Every five seconds — for testing purposes only</MenuItem>
                                <MenuItem eventKey="ThreeMonths">Every three months</MenuItem>
                                <MenuItem eventKey="SixMonths">Every six months</MenuItem>
                                <MenuItem eventKey="OneYear">Every twelve months</MenuItem>

                            </DropdownButton>
                        </span>
                    </div>

                    <hr/>

                    <div style={{float: "right"}}>
                        <a href={""} style={{marginRight: "10px"}}> {"More about authentication"} </a>
                        <Button className={'labkey-button primary'} onClick={this.saveChanges}>Apply</Button>
                    </div>

                    <Button
                        className={'labkey-button'}
                        onClick={this.props.closeModal}
                        style={{marginLeft: '10px'}}
                    >
                        Cancel
                    </Button>
                </Modal.Body>

            </Modal>
        );
    }
}
