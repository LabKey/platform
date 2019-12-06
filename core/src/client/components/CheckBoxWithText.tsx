import * as React from 'react'

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheckSquare } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';

// Todo:
// this might be a candidate for a labkey-component? Better style than regular checkbox
// figure out TS typing for a function (the onclick)

interface Props {
    checked: boolean
    onClick: any
    rowText: String
}

export default class CheckBoxWithText extends React.PureComponent<Props>{
    render(){
        let checkedOrNot = (this.props.checked
                ? <FontAwesomeIcon size='lg' icon={faCheckSquare} color={"#0073BB"} />
                : <FontAwesomeIcon size='lg' icon={faSquare} color={"#adadad"}/>
        );

        return(
            <div className={"bottom-margin"}>

                <span className="noHighlight clickable" onClick={() => this.props.onClick()}>
                    { checkedOrNot }
                </span>

                <span className={"left-margin"}> {this.props.rowText} </span>

            </div>
        )
    }
}