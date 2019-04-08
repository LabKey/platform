import * as React from "react";
import {Panel} from "react-bootstrap";
import {DataUploadPanel} from "./DataUploadPanel";

type State = {
    errorMessage?: string
}

export class App extends React.Component<any, State> {

    constructor(props) {
        super(props);
        this.state = {
            errorMessage: null
        };
        this.handleErrors = this.handleErrors.bind(this);
    }

    handleErrors(incomingError?: string) {
        this.setState({
            errorMessage: incomingError
        })
    }

    render(): React.ReactNode {
        const { errorMessage } = this.state;

        return (
            <>
                {errorMessage && <div className={'alert alert-danger'}>{errorMessage + ' You may want to try a different file.'}</div>}
                <Panel>
                    <Panel.Heading>
                        <div className={"panel-title"}>File Upload and Preview</div>
                    </Panel.Heading>
                    <Panel.Body>
                        <DataUploadPanel
                            handleErrors={this.handleErrors}
                        />
                    </Panel.Body>
                </Panel>
            </>
        );
    }
}