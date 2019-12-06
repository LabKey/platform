import * as React from 'react'

import {Button, Alert} from 'react-bootstrap'
import {Map, List, fromJS} from 'immutable';

import "./authenticationConfiguration.scss";
import "@glass/base/dist/base.css"
import GlobalAuthConfigs from '../components/GlobalAuthConfigs';
import AuthConfigMasterPanel from '../components/AuthConfigMasterPanel';
import { Ajax, ActionURL, Security } from '@labkey/api'

// Todo, meta:
// Finalize TS, Immutable

// Todo:
// Display error component upon ajax fail
// Q: How to specify specific component as TS type in a props interface

interface Props {} // Q: Is this how you specify no props?

interface State {
    canEdit: boolean
    globalAuthConfigs: Object
    addNew: Object
    primary: Array<Object>
    primaryLDAP: any
    secondary: any
}
export class App extends React.Component<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            canEdit: false,
            globalAuthConfigs: null,
            addNew: null,
            primary: null,
            primaryLDAP: null,
            secondary: null,
        };

        // Testing functions
        this.savePls = this.savePls.bind(this);
        this.cancelChanges = this.cancelChanges.bind(this);

        // For GlobalAuthConfigs
        this.checkGlobalAuthBox = this.checkGlobalAuthBox.bind(this);

        // For AuthConfigMasterPanel
        this.onDragEnd = this.onDragEnd.bind(this);
        this.reorder = this.reorder.bind(this);
        this.handleChangeToPrimary = this.handleChangeToPrimary.bind(this);
        this.handlePrimaryToggle = this.handlePrimaryToggle.bind(this);
    }

    componentDidMount() {
        Ajax.request({
            url: ActionURL.buildURL("login", "InitialMount"),
            method : 'GET',
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                let response = JSON.parse(result.response);
                this.setState({...response});
                console.log({...response});
            }
        })
    }

    savePls() {
        Ajax.request({
            url: ActionURL.buildURL("login", "setAuthenticationParameter"),
            method : 'POST',
            params: {parameter: "SelfRegistration", enabled:"true"},
            scope: this,
            failure: function(error){
                console.log("fail: ", error);
            },
            success: function(result){
                console.log("success: ", result);
                this.state({...result});
                console.log(this.state);
            }
        })
    }

    cancelChanges() {
        console.log(this.state);
        console.log("globalAuthConfig props: ",{...this.state.globalAuthConfigs});
    };

    // todo: use immutable?
    checkGlobalAuthBox(id: string) {
        let oldState = this.state.globalAuthConfigs[id];
        this.setState(prevState => ({
            ...prevState,
            globalAuthConfigs: {
                ...prevState.globalAuthConfigs,
                [id]: !oldState
            }
        }));
    }

    onDragEnd(result) {
        if (!result.destination)
        {
            return;
        }

        const items = this.reorder(
            this.state.primary,
            result.source.index,
            result.destination.index
        );

        this.setState({
            primary: items
        });
    }

    reorder = (list, startIndex, endIndex) => {
        const result = Array.from(list);
        const [removed] = result.splice(startIndex, 1);
        result.splice(endIndex, 0, removed);

        return result;
    };

    // rough
    handleChangeToPrimary(event) {
        let {name, value, id} = event.target;
        const l = List(this.state.primary);
        // console.log(l.toArray());
        const l2 = l.setIn([id, name], value);

        const thing = l2.toArray();
        // console.log(thing);

        this.setState(prevState => ({
            ...prevState,
            primary: thing
        }))
    }

    // rough
    handlePrimaryToggle(toggle, id){
        const l = List(this.state.primary);
        const l2 = l.setIn([id, 'enabled'], !toggle);
        const thing = l2.toArray();
        this.setState(prevState => ({
            ...prevState,
            primary: thing
        }))
    }

    render() {
        return(
            <div style={{minWidth:"1040px"}}>
                <GlobalAuthConfigs
                    {...this.state.globalAuthConfigs}
                    checkGlobalAuthBox={this.checkGlobalAuthBox}
                />

                <AuthConfigMasterPanel
                    addNew={this.state.addNew}
                    primary={this.state.primary}
                    primaryLDAP={this.state.primaryLDAP}
                    secondary={this.state.secondary}
                    onDragEnd={this.onDragEnd}
                    handleChangeToPrimary={this.handleChangeToPrimary}
                    handlePrimaryToggle={this.handlePrimaryToggle}
                />

                {false && <Alert>You have unsaved changes.</Alert>}

                <Button className={'labkey-button primary'} onClick={this.cancelChanges}>Save and Finish</Button>

                <Button className={'labkey-button'} onClick={this.cancelChanges} style={{marginLeft: '10px'}}>Cancel</Button>
            </div>
        );
    }
}
