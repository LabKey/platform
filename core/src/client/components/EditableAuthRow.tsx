import React, { PureComponent } from 'react';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faGripVertical, faPencilAlt, faTimesCircle} from "@fortawesome/free-solid-svg-icons";
import ReactBootstrapToggle from 'react-bootstrap-toggle';

import SimpleAuthRow from "./SimpleAuthRow";
import DynamicConfigurationModal from "./DynamicConfigurationModal";

// Todo:
// don't use the style to round the corners
// maybe be handleChange be a const
// figure out TS typing for a function

interface Props {
    id: string
    rowId: string
    authName: string
    url: string
    enabled: boolean
    description: string
    handlePrimaryToggle: any
    stateSection: String
    noHandleIcon?: boolean
}
interface State {
    modalOpen?: boolean
    highlight?: boolean
}

export default class EditableAuthRow extends PureComponent<any, State> {
    constructor(props){
        super(props);
        this.state = {
            modalOpen: false,
            highlight: false
        };
    }

    onToggleModal = (toggled) => {
        this.setState(() => ({
            [toggled]: !this.state[toggled]
        }));
    };

    onToggleClicked = () => {
        this.props.handlePrimaryToggle(this.props.enabled, this.props.id, this.props.stateSection);
    };

    onDeleteClicked = () => {
        this.props.deleteAction(this.props.rowId, this.props.stateSection);
    };

    render(){
        const enabled =
            <ReactBootstrapToggle
                onClick={() => this.onToggleClicked()}
                on="Enabled"
                off="Disabled"
                onstyle={"primary"}
                active={this.props.enabled}
                style={{width: "90px", height: "28px"}}
            />;

        const deleteIcon =
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onDeleteClicked()}>
                <FontAwesomeIcon icon={faTimesCircle} color={"#d9534f"} />
            </div>;

        const editIcon =
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onToggleModal("modalOpen")}>
                <FontAwesomeIcon size='1x' icon={faPencilAlt}/>
            </div>;

        const dynamicModal = (this.state.modalOpen &&
                <DynamicConfigurationModal
                    type={this.props.modalType}
                    closeModal={() => {this.onToggleModal("modalOpen")}}
                    {...this.props}
                />);

        return(
            <div
                onMouseOver={() => {this.setState({highlight: true})}}
                onMouseOut={() => {this.setState({highlight: false})}}
            >
                <SimpleAuthRow
                    handle = {this.props.noHandleIcon ? null : <LightupHandle highlight={this.state.highlight}/>}
                    description = {this.props.description}
                    url = {this.props.url || "http://labkey/login-configure.view?"}
                    name = {this.props.authName}
                    enabled = {enabled}
                    delete = {deleteIcon}
                    editIcon = {editIcon}
                    modal = {dynamicModal}
                />
            </div>
        )
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
