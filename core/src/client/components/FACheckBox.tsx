import React, { FC, memo, useCallback } from 'react';
import classNames from 'classnames';

interface Props {
    canEdit: boolean;
    checked: boolean;
    name?: string;
    onClick?: () => void;
}

export const FACheckBox: FC<Props> = memo(({ canEdit, checked, name, onClick }) => {
    const wrapperClassName = classNames(name, 'no-highlight', { clickable: canEdit });
    const iconClassName = classNames('fa', 'fa-lg', { 'fa-check-square': checked, 'fa-square': !checked });
    const onClick_ = useCallback(() => {
        if (canEdit) onClick();
    }, [canEdit, onClick]);

    return (
        <span className={wrapperClassName + (name ? name : '')} onClick={onClick_}>
            <span className={iconClassName} style={{ color: checked ? '#0073BB' : '#ADADAD' }} />
        </span>
    );
});
