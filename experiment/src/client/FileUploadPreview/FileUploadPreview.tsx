import * as React from "react";
import {Panel} from "react-bootstrap";
import {DataUploadPanel} from "./DataUploadPanel";

type State = {
    //tbd
}
export class App extends React.Component<any, State> {

    render(): React.ReactNode {
        return (
            <Panel>
                <Panel.Heading>
                    <div className={"panel-title"}>File Upload and Preview</div>
                </Panel.Heading>
                <Panel.Body>
                    <DataUploadPanel />
                </Panel.Body>
            </Panel>
        );
    }
}