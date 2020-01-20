import React, { PureComponent } from 'react';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCheckSquare } from '@fortawesome/free-solid-svg-icons';
import { faSquare } from '@fortawesome/free-regular-svg-icons';

interface Props {
    key?: string;
    checked?: boolean;
    canEdit?: boolean;
    onClick?: Function;
    name?: string;
}

export default class FACheckBox extends PureComponent<Props> {
    render() {
        const checkedOrNot = this.props.checked ? (
            <FontAwesomeIcon size="lg" icon={faCheckSquare} color="#0073BB" />
        ) : (
            <FontAwesomeIcon size="lg" icon={faSquare} color="#adadad" />
        );

        const classNames = this.props.canEdit ? 'no-highlight clickable ' : 'no-highlight ';

        return (
            <>
                <span className={classNames + this.props.name} onClick={() => this.props.onClick()}>
                    {checkedOrNot}
                </span>
            </>
        );
    }
}
