import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import { OntologyBrowserFilterPanel, } from '@labkey/components';
import classNames from 'classnames';

import './ConceptFilterView.scss';

type FilterValueListener = (newValue: string) => void;

export interface AppContext {
    ontologyId: string;
    initFilterValue?: string;
    subscribeFilterValue: (FilterValueListener) => void;
    unsubscribeFilterValue: (FilterValueListener) => void; //TODO change property name
    onFilterChange: (filterValue: string) => void;
}

interface Props {
    context: AppContext;
}

export const ConceptFilterView: FC<Props> = memo(props => {
    const {context} = props;
    const {initFilterValue, onFilterChange, ontologyId, subscribeFilterValue, unsubscribeFilterValue } = context;
    const [filterValue, setFilterValue] = useState(initFilterValue);
    const [collapsed, setHidden] = useState<boolean>(true);

    const clickHandler = useCallback(() => {
        setHidden(!collapsed);
    },[collapsed, setHidden]);

    useEffect(() => {
        const handleValueChange = (newValue: string) => {
            setFilterValue(newValue);
        };
        subscribeFilterValue(handleValueChange);
        return () => unsubscribeFilterValue(handleValueChange);

    },[setFilterValue, subscribeFilterValue, unsubscribeFilterValue]);

    return (
        <div className="concept-filter-view">
            <a className={classNames('show-toggle', {collapsed})} onClick={clickHandler}>{collapsed ? 'Find Concepts By Tree' : 'Close Browser'}</a>
            {!collapsed && <OntologyBrowserFilterPanel ontologyId={ontologyId} filterValue={filterValue} onFilterChange={onFilterChange} /> }
        </div>
    );
});
