import React, { PureComponent } from 'react';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheckSquare } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';

// Todo:
// this might be a candidate for a labkey-component? Better style than regular checkbox
// figure out TS typing for a function (the onclick)

interface Props {
    checked: boolean
    onClick: any
}

export default class FACheckBox extends PureComponent<any> {
    render(){
        let checkedOrNot = (this.props.checked
                ? <FontAwesomeIcon size='lg' icon={faCheckSquare} color={"#0073BB"} />
                : <FontAwesomeIcon size='lg' icon={faSquare} color={"#adadad"}/>
        );

        return(
            <>
                <span className="noHighlight clickable" style={{}} onClick={() => this.props.onClick()}>
                    { checkedOrNot }
                </span>
            </>
        )
    }
}
