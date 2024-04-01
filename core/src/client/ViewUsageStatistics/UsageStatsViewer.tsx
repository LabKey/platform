import React, { FC, memo, useCallback, useEffect, useMemo, useState } from 'react';
import { ActionURL, Ajax, Utils } from '@labkey/api';
import { Alert, LoadingSpinner, naturalSort } from '@labkey/components';

function fetchUsageStatistics(): Promise<Record<string, any>> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: ActionURL.buildURL('admin', 'testMothershipReport', null, { type: 'CheckForUpdates', level: 'ON' }),
            success: Utils.getCallbackWrapper(response => resolve(response)),
            failure: Utils.getCallbackWrapper((error, response) => reject(response.exception)),
        });
    });
}

interface SearchResult {
    invalidSegment: string;
    keys: string[];
    node: string; // Stringify'ed node value
    validSegments: string[];
}

function searchStats(stats: Record<string, any>, path: string): SearchResult {
    if (stats === undefined) return undefined;
    if (path === '')
        return {
            invalidSegment: undefined,
            keys: Object.keys(stats).sort(naturalSort),
            node: JSON.stringify(stats, undefined, 2),
            validSegments: undefined,
        };
    const parts = path.split('.');
    let node = stats;
    const validSegments = [];

    for (const segment of parts) {
        if (node[segment] !== undefined) {
            validSegments.push(segment);
            node = node[segment];
        } else {
            return {
                invalidSegment: segment,
                keys: Object.keys(node)
                    .filter(k => k.startsWith(segment))
                    .sort(naturalSort),
                node: JSON.stringify(node, undefined, 2),
                validSegments,
            };
        }
    }

    return {
        invalidSegment: undefined,
        keys: node === null ? [] : Object.keys(node).sort(naturalSort),
        node: JSON.stringify(node, undefined, 2),
        validSegments: undefined,
    };
}

interface StatsKeyProps {
    selectKey: (key: string) => void;
    value: string;
}

const StatsKey: FC<StatsKeyProps> = memo(({ selectKey, value }) => {
    const onClick = useCallback(() => selectKey(value), [value, selectKey]);
    return (
        <li className="clickable-text" onClick={onClick}>
            {value}
        </li>
    );
});

interface StatsDisplayProps {
    searchResult: SearchResult;
    selectKey: (key: string) => void;
}

export const StatsDisplay: FC<StatsDisplayProps> = memo(({ searchResult, selectKey }) => {
    const { invalidSegment, keys, node, validSegments } = searchResult;
    const invalidSeg = <code className="text-danger">{invalidSegment === '' ? '.' : invalidSegment}</code>;
    return (
        <div className="usage-stats__search-result">
            {invalidSegment !== undefined && (
                <div className="usage-stats__invalid-messsage">
                    {invalidSeg} not found{' '}
                    {validSegments.length > 0 && (
                        <>
                            in <code className="text-success">{validSegments.join('.')}</code>
                        </>
                    )}
                </div>
            )}

            <div className="usage-stats__valid-keys">
                {keys.length > 0 && (
                    <>
                        {invalidSegment && <label>Keys starting with {invalidSeg}:</label>}
                        {!invalidSegment && <label>Keys:</label>}
                        <ul className="key-list">
                            {keys.map(key => (
                                <StatsKey key={key} selectKey={selectKey} value={key} />
                            ))}
                        </ul>
                    </>
                )}
                {keys.length === 0 && <>No valid keys starting with {invalidSeg}</>}
            </div>
            {node !== undefined && <pre>{node}</pre>}
        </div>
    );
});

export const UsageStatsViewer: FC = memo(() => {
    const [loading, setLoading] = useState<boolean>(false);
    const [usageStatistics, setUsageStatistics] = useState(undefined);
    const [jsonPath, setJsonPath] = useState<string>(ActionURL.getParameter('jsonPath') ?? '');
    const [error, setError] = useState(undefined);
    const loadStats = useCallback(async () => {
        setError(undefined);
        setLoading(true);
        try {
            const response = await fetchUsageStatistics();
            setUsageStatistics(response.jsonMetrics);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }, []);
    const onChange = useCallback(event => setJsonPath(event.target.value), []);
    const searchResult = useMemo(() => {
        return searchStats(usageStatistics, jsonPath);
    }, [usageStatistics, jsonPath]);
    const clearPath = useCallback(() => setJsonPath(''), []);
    const selectKey = useCallback(
        (key: string) => {
            setJsonPath(current => {
                if (current === '') {
                    return key;
                } else if (searchResult.invalidSegment !== undefined) {
                    let segments = current.split('.');
                    segments = segments.slice(0, segments.length - 1); // Slice off the invalid segment
                    segments.push(key); // append the key that was clicked
                    return segments.join('.');
                }

                return current + '.' + key;
            });
        },
        [searchResult]
    );

    useEffect(() => {
        loadStats();
    }, [loadStats]);
    return (
        <div>
            <div className="usage-stats panel panel-default">
                <div className="panel-body">
                    <div className="usage-stats__button-bar">
                        <button type="button" className="btn btn-primary" onClick={loadStats}>
                            Reload Usage Statistics
                        </button>
                        {loading && <LoadingSpinner msg="Loading usage statistics..." />}
                    </div>
                    <div className="usage-stats__inputs">
                        <div>
                            <label htmlFor="stats-path">JSON Path:</label>
                        </div>
                        <input
                            id="stats-path"
                            className="form-control"
                            type="text"
                            value={jsonPath}
                            onChange={onChange}
                            placeholder="e.g. modules.Core"
                        />
                        <div>
                            <button className="btn btn-primary" onClick={clearPath} type="button">
                                clear
                            </button>
                        </div>
                    </div>
                    <Alert>{error}</Alert>
                    {searchResult !== undefined && <StatsDisplay selectKey={selectKey} searchResult={searchResult} />}
                </div>
            </div>
        </div>
    );
});
UsageStatsViewer.displayName = 'UsageStatsViewer';
