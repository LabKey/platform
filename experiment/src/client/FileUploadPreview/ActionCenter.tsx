import * as React from "react";

interface ActionCenterProps {
    actions: string,
}

export const ActionCenter: React.SFC<ActionCenterProps> = (props) => {
    return ( props.actions &&
        <>
            <div className={"import-panel__action-center"}>
                <strong>{props.actions}</strong>
            </div>
        </>
    )
};
