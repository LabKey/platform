import * as React from "react";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {faGripVertical, faPencilAlt} from "@fortawesome/free-solid-svg-icons";
import ReactBootstrapToggle from 'react-bootstrap-toggle';

import SimpleAuthRow from "./SimpleAuthRow";
import ConfigurationModal from "./ConfigurationModal";
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
    handleChangeToPrimary: any
    handlePrimaryToggle: any
    stateSection: String
    noHandleIcon?: boolean
}
interface State {
    modalOpen: boolean
    highlight: boolean
}
export default class EditableAuthRow extends React.Component<Props, State>{
    constructor(props){
        super(props);
        this.state = {
            modalOpen: false,
            highlight: false
        };
        this.onToggle = this.onToggle.bind(this);
    }

    onToggle(toggled) {
        this.setState(prevState => ({
            ...prevState,
            [toggled]: !this.state[toggled]
        }));
    }

    render(){
        const enabled =
            <ReactBootstrapToggle
                onClick={() => this.props.handlePrimaryToggle(this.props.enabled, this.props.id, this.props.stateSection)}
                on="Enabled"
                off="Disabled"
                onstyle={"primary"}
                active={this.props.enabled}
                style={{width: "90px", height: "28px"}}
            />;

        const edit =
            <div className={"clickable"} style={{marginTop: "5px"}}  onClick={() => this.onToggle("modalOpen")}>
                <FontAwesomeIcon size='1x' icon={faPencilAlt}/>
            </div>;

        // const modal = (this.state.modalOpen &&  <ConfigurationModal {...this.props} closeModal={() => {this.onToggle("modalOpen")}} />);
        const dynamicModal = (this.state.modalOpen && <DynamicConfigurationModal {...this.props} closeModal={() => {this.onToggle("modalOpen")}}/>)

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
                    edit = {edit}
                    modal = {dynamicModal}
                />
            </div>
        )
    }
}

interface HandleProps {
    highlight: boolean
}
interface HandleState { }

class LightupHandle extends React.Component<HandleProps, HandleState>{
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