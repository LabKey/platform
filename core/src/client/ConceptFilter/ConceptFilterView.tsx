import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import { OntologyBrowserFilterPanel, } from '@labkey/components';
import classNames from 'classnames';

import './ConceptFilterView.scss';
import { Filter, } from '@labkey/api';

type ChangeListener = (newValue: string) => void;
type FilterChangeListener = (filter: Filter.IFilterType) => void;
type CollapseChangeListener = (collapse: boolean) => void;

export interface AppContext {
    ontologyId: string;
    initFilterValue?: string;
    initFilter?: Filter.IFilterType;
    subscribeFilterValue: (listener: ChangeListener) => void;
    unsubscribeFilterValue: (listener:ChangeListener) => void;
    onFilterChange: (filterValue: string) => void;
    subscribeFilterTypeChanged: (listener: FilterChangeListener) => void;
    unsubscribeFilterTypeChanged: (listener: FilterChangeListener) => void;
    loadListener: () => void;
    subscribeCollapse: (listener: CollapseChangeListener) => void;
    unsubscribeCollapse: () => void;
    onOpen: () => void;
}

interface Props {
    context: AppContext;
}

export const ConceptFilterView: FC<Props> = memo(props => {
    const {
        initFilterValue,
        initFilter,
        onFilterChange,
        ontologyId,
        subscribeFilterValue,
        unsubscribeFilterValue,
        subscribeFilterTypeChanged,
        unsubscribeFilterTypeChanged,
        loadListener,
        subscribeCollapse,
        unsubscribeCollapse,
        onOpen,
    } = props.context;
    const [filterValue, setFilterValue] = useState(initFilterValue);
    const [filter, setFilter] = useState(initFilter);
    const [collapsed, setCollapsed] = useState<boolean>(true);

    const clickHandler = useCallback(() => {
        setCollapsed(!collapsed);
        onOpen();
    },[collapsed, setCollapsed]);

    useEffect(() => {
        const handleValueChange = (newValue: string) => {
            setFilterValue(newValue);
        };
        subscribeFilterValue(handleValueChange);
        return () => unsubscribeFilterValue(handleValueChange);

    },[setFilterValue, subscribeFilterValue, unsubscribeFilterValue]);

    useEffect(() => {
        const handleFilterChange = (newValue: Filter.IFilterType): void => {
            setFilter(newValue);
        };
        subscribeFilterTypeChanged(handleFilterChange);
        return () => unsubscribeFilterTypeChanged(handleFilterChange);

    },[setFilter, subscribeFilterTypeChanged, unsubscribeFilterTypeChanged]);

    useEffect(() => {
        const handleCollapse = (collapse: boolean): void => {
            setCollapsed(true);
        }
        subscribeCollapse(handleCollapse);
        return () => unsubscribeCollapse();
    });

    useEffect(() => {
        loadListener();
    },[]);

    //No need to show the filter tree if a value can't be set.
    if (!filter?.isDataValueRequired())
        return null;

    return (
        <div className={classNames('concept-filter-view', { collapsed, })}>
            <a className='show-toggle'
               onClick={clickHandler}>{collapsed ? 'Find Concepts By Tree' : 'Close Browser'}</a>
            {!collapsed &&
            <OntologyBrowserFilterPanel ontologyId={ontologyId} filterValue={filterValue} filterType={filter}
                                        onFilterChange={onFilterChange}/>}
        </div>
    );
});
