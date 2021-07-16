import React, { FC, memo, useCallback, useEffect, useState } from 'react';
import classNames from 'classnames';
import { Filter, } from '@labkey/api';
import { OntologyBrowserFilterPanel, } from '@labkey/components';

import './ConceptFilterView.scss';

type ChangeListener = (newValue: string) => void;
type FilterChangeListener = (filter: Filter.IFilterType) => void;
type CollapseChangeListener = (collapse: boolean) => void;

export interface AppContext {
    ontologyId: string;
    conceptSubtree: string;
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
    columnName?: string;
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
        conceptSubtree,
        subscribeFilterValue,
        unsubscribeFilterValue,
        subscribeFilterTypeChanged,
        unsubscribeFilterTypeChanged,
        loadListener,
        subscribeCollapse,
        unsubscribeCollapse,
        onOpen,
        columnName = 'Concepts'
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
    if (!filter?.isDataValueRequired()) return null;

    return (
        <div className={classNames('concept-filter-view', { collapsed, })}>
            <a className='show-toggle'
               onClick={clickHandler}>{collapsed ? `Find ${columnName} By Tree` : 'Close Browser'}</a>
            {!collapsed &&
                <OntologyBrowserFilterPanel
                    ontologyId={ontologyId}
                    conceptSubtree={conceptSubtree}
                    filterValue={filterValue}
                    filterType={filter}
                    onFilterChange={onFilterChange}
                />}
        </div>
    );
});
