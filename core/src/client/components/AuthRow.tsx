import React, { PureComponent } from 'react';
import {Button, ButtonGroup, Col, DropdownButton, FormControl, MenuItem, Modal, Panel} from "react-bootstrap";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faPencilAlt, faInfoCircle, faTimesCircle, faGripVertical, faCircle} from "@fortawesome/free-solid-svg-icons";
import DynamicConfigurationModal from "./DynamicConfigurationModal";
import ReactBootstrapToggle from 'react-bootstrap-toggle';
import DatabaseConfigurationModal from "./DatabaseConfigurationModal";

interface Props {
    canEdit: boolean
    enabled: boolean
    draggable: boolean
}
export default class AuthRow extends PureComponent<any, any> {
    constructor(props) {
        super(props);
        this.state = {
            color: false,
            modalOpen: false,
        };
    }

    onToggleClicked = () => {
        this.props.handlePrimaryToggle(this.props.enabled, this.props.index, this.props.stateSection);
    };

    onToggleModal = (toggled) => {
        this.setState(() => ({
            [toggled]: !this.state[toggled]
        }));
    };

    onDeleteClick = () => {
        this.props.deleteAction(this.props.configuration, this.props.stateSection);
    };

    render () {
        const {canEdit, enabled, draggable, field3 } = this.props;
        const isDatabaseAuth = (field3 == "Database");

        const handle = (draggable) ?
            <LightupHandle highlight={this.state.highlight}/>
            : null;

        const enabledField = (this.props.enabled)
            ? <> <FontAwesomeIcon icon={faCircle} color={"#75B666"} /> &nbsp; Enabled </>
            : <> <FontAwesomeIcon icon={faCircle} color={"#999999"} /> &nbsp; Disabled </>;

        const deleteIcon = (canEdit) ?
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onDeleteClick()}>
                <FontAwesomeIcon icon={faTimesCircle} color={"#d9534f"} />
            </div>
            : null;

        const editIcon = (canEdit) ? // shape this up
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onToggleModal("modalOpen")}>
                <FontAwesomeIcon size='1x' icon={faPencilAlt}/>
            </div>
            :
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onToggleModal("modalOpen")}>
                <FontAwesomeIcon size='1x' icon={faInfoCircle}/>
            </div>;

        const dynamicModal = (this.state.modalOpen &&
                <DynamicConfigurationModal
                    {...this.props}
                    type={this.props.modalType}
                    closeModal={() => {this.onToggleModal("modalOpen")}}
                    updateAuthRowsAfterSave={this.props.updateAuthRowsAfterSave}
                />);
        const databaseModal =
            <DatabaseConfigurationModal
                closeModal={() => {this.onToggleModal("modalOpen")}}
            />;

        const modal = (this.state.modalOpen &&
            (isDatabaseAuth) ? databaseModal : dynamicModal
        );

        return (
            <div style={{ paddingBottom: '20px' }}>
                <div
                    className="domain-field-row domain-row-border-default"
                    onMouseOver={() => {this.setState({ color: true });}}
                    onMouseOut={() => {this.setState({ color: false });}}
                >
                    <div className="domain-row-container row">
                        <div className="domain-row-handle">{this.props.handle}</div>

                        <div className="domain-row-main">
                            <Col xs={9} className="domain-row-base-fields">
                                <Col xs={4} className="down">
                                    {this.props.field1}
                                </Col>
                                <Col xs={4} className="down">
                                    {this.props.field2}
                                </Col>
                                <Col xs={1} className="down">
                                    {this.props.field3}
                                </Col>
                            </Col>

                            <Col xs={1} />

                            <Col xs={2} className="domain-row-base-fields">
                                <Col xs={7} >{enabledField}</Col>

                                <Col xs={1}>{deleteIcon}</Col>

                                <Col xs={3}>{editIcon}</Col>
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
    highlight: boolean
}

class LightupHandle extends PureComponent<HandleProps> {
    render(){
        const HIGHLIGHT_BLUE = '#2980B9';
        const NOT_HIGHLIGHT_GRAY = '#999999';

        return(
            <div>
                <FontAwesomeIcon size='lg' color={(this.props.highlight) ? HIGHLIGHT_BLUE : NOT_HIGHLIGHT_GRAY} icon={faGripVertical}/>
            </div>
        )
    }
}