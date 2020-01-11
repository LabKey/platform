import React, { PureComponent } from 'react';

import { Panel } from 'react-bootstrap';

import FACheckBox from './FACheckBox';

let ROW_TEXTS = [
    { id: 'SelfRegistration', text: 'Allow self sign up' },
    { id: 'SelfServiceEmailChanges', text: 'Allow users to edit their own email addresses' },
    { id: 'AutoCreateAccounts', text: 'Auto-create authenticated users' },
];

interface Props {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean;

    canEdit?: boolean;
    checkGlobalAuthBox?: Function;
    authCount?: number;
}
interface State {
    SelfRegistration?: boolean;
    SelfServiceEmailChanges?: boolean;
    AutoCreateAccounts?: boolean | null;

    canEdit?: boolean;
    checkGlobalAuthBox?: Function;
    authCount?: number;
}

export default class GlobalSettings extends PureComponent<Props, State> {
    constructor(props) {
        super(props);
        this.state = {
            ...this.props,
        };
    }

    render() {
        let rowTexts = ROW_TEXTS;
        if (this.props.authCount == 1){
            rowTexts = ROW_TEXTS.slice(0,-1);
        }
        const {canEdit} = this.props;

        const rowTextComponents = rowTexts.map(text => (
            <div className="global-settings__text-row" key={text.id}>
                <FACheckBox
                    key={text.id}
                    checked={this.props[text.id]}
                    canEdit={this.props.canEdit}
                    onClick={
                        this.props.canEdit
                            ? () => { this.props.checkGlobalAuthBox(text.id) }
                            : () => {}
                    }
                />

                <span className="global-settings__text"> {text.text} </span>
            </div>
        ));

        return (
            <Panel>
                <Panel.Heading>
                    <span className="bold-text">Global Settings</span>
                </Panel.Heading>

                <Panel.Body>
                    <div className="bold-text global-settings__title-text"> Sign up and email options</div>

                    {rowTextComponents}

                </Panel.Body>
            </Panel>
        );
    }
}
