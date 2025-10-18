import React, { FC, useCallback, useEffect, useMemo, useState } from 'react';

// Rely on the global LABKEY object to avoid introducing new dependencies here.
// Types are intentionally loose to minimize coupling to specific API shapes.
declare const LABKEY: any;

interface SchemaItem {
    name: string;
    displayName?: string;
}

interface QueryItem {
    name: string;
    schemaName: string;
    isUserDefined?: boolean;
}

const buildExecuteUrl = (schemaName: string, queryName: string) => {
    try {
        return LABKEY.ActionURL.buildURL('query', 'executeQuery', undefined, { schemaName, queryName });
    } catch (e) {
        return '#';
    }
};

const buildNewQueryUrl = (schemaName: string, baseTableName?: string) => {
    try {
        const params: any = { schemaName };
        if (baseTableName) params.ff_baseTableName = baseTableName;
        return LABKEY.ActionURL.buildURL('query', 'newQuery', undefined, params);
    } catch (e) {
        return '#';
    }
};

const buildEditQueryUrl = (schemaName: string, queryName: string) => {
    try {
        return LABKEY.ActionURL.buildURL('query', 'sourceQuery', undefined, { schemaName, queryName });
    } catch (e) {
        return '#';
    }
};

const getContainerPath = () => {
    try {
        return LABKEY.ActionURL.getContainer();
    } catch {
        return undefined;
    }
};

const useHashRoute = () => {
    const [hash, setHash] = useState<string>(() => window.location.hash || '');

    useEffect(() => {
        const onHashChange = () => setHash(window.location.hash || '');
        window.addEventListener('hashchange', onHashChange);
        return () => window.removeEventListener('hashchange', onHashChange);
    }, []);

    const route = useMemo(() => {
        // Supported formats:
        //  #/schema/SchemaName
        //  #/schema/SchemaName/query/QueryName
        const trimmed = hash.startsWith('#') ? hash.substring(1) : hash;
        const parts = trimmed.split('/').filter(Boolean);
        if (parts.length >= 2 && parts[0] === 'schema') {
            const schemaName = decodeURIComponent(parts[1]);
            if (parts.length >= 4 && parts[2] === 'query') {
                const queryName = decodeURIComponent(parts[3]);
                return { schemaName, queryName };
            }
            return { schemaName };
        }
        return {} as { schemaName?: string; queryName?: string };
    }, [hash]);

    const setRoute = useCallback((schemaName?: string, queryName?: string) => {
        if (!schemaName) {
            window.location.hash = '';
        } else if (!queryName) {
            window.location.hash = `#/schema/${encodeURIComponent(schemaName)}`;
        } else {
            window.location.hash = `#/schema/${encodeURIComponent(schemaName)}/query/${encodeURIComponent(queryName)}`;
        }
    }, []);

    return { route, setRoute } as const;
};

const Toolbar: FC<{
    schemaName?: string;
    queryName?: string;
}> = ({ schemaName, queryName }) => {
    const mc = LABKEY?.moduleContext || {};
    const currentUser = LABKEY?.Security?.currentUser || {};

    const canCreate = mc?.query?.hasEditQueriesPermission && currentUser?.canUpdate && !!schemaName;

    return (
        <div style={{ display: 'flex', gap: 8, padding: '8px 12px', borderBottom: '1px solid #e0e0e0', alignItems: 'center' }}>
            <strong style={{ marginRight: 12 }}>Schema Browser</strong>
            <button onClick={() => (window.location.href = LABKEY.ActionURL.buildURL('query', 'validateQueries'))}>
                Validate Queries
            </button>
            {currentUser?.isSystemAdmin && mc?.query?.hasQueryAnalysisService && (
                <button onClick={() => (window.location.href = LABKEY.ActionURL.buildURL('query', 'crossFolderDependencies'))}>
                    Cross Folder Dependencies
                </button>
            )}
            {currentUser?.isAdmin && (
                <button onClick={() => (window.location.href = LABKEY.ActionURL.buildURL('query', 'admin'))}>
                    Schema Administration
                </button>
            )}
            {canCreate && (
                <button onClick={() => (window.location.href = buildNewQueryUrl(schemaName!, queryName))}>Create New Query</button>
            )}
            {currentUser?.isAdmin && mc?.dataintegration && (
                <button onClick={() => (window.location.href = LABKEY.ActionURL.buildURL('query', 'manageRemoteConnections'))}>
                    Manage Remote Connections
                </button>
            )}
        </div>
    );
};

interface LookupInfo {
    schemaName?: string;
    queryName?: string;
    containerPath?: string;
}

interface ColumnInfo {
    name: string;
    caption?: string;
    type?: string;
    nullable?: boolean;
    lookup?: LookupInfo;
}

const displayType = (col: ColumnInfo | any): string => {
    const t = col?.type || col?.jsonType || col?.jdbcType || '';
    if (t) return String(t);
    const rangeURI: string | undefined = col?.rangeURI || col?.rangeUri;
    if (rangeURI) {
        const hash = rangeURI.lastIndexOf('#');
        if (hash > -1) return rangeURI.substring(hash + 1);
        const slash = rangeURI.lastIndexOf('/');
        if (slash > -1) return rangeURI.substring(slash + 1);
        return rangeURI;
    }
    return '';
};

const toBool = (v: any): boolean | undefined => (v === undefined ? undefined : !!v);

const normalizeLookup = (c: any): LookupInfo | undefined => {
    const lk = c?.lookup || c?.lookupJSON || c?.fk || c?.foreignKey || c?.displayFieldFK;
    if (!lk) return undefined;
    const schema = lk.schemaName || lk.schema || lk.schemaNameFull || lk.schemaPath || lk.schemaDisplay || lk.schemaQueryName;
    const query = lk.queryName || lk.table || lk.query || lk.tableName;
    const containerPath = lk.containerPath || lk.container || lk.publicContainer;
    if (!schema || !query) return undefined;
    return { schemaName: schema, queryName: query, containerPath };
};

const normalizeColumns = (input: any[]): ColumnInfo[] => {
    const cols: ColumnInfo[] = [];
    input?.forEach((c: any) => {
        const name = c?.name || c?.fieldKey || c?.columnName;
        if (!name) return;
        const caption = c?.caption || c?.label || c?.displayName || name;
        const type = displayType(c);
        const required = toBool(c?.required);
        const allowNull = c?.allowNull ?? c?.nullable ?? c?.allowMissingValue;
        cols.push({
            name,
            caption,
            type,
            nullable: allowNull !== undefined ? !!allowNull : required !== undefined ? !required : undefined,
            lookup: normalizeLookup(c),
        });
    });
    return cols.sort((a, b) => a.name.localeCompare(b.name));
};

const extractColumnsFromGetQueryDetails = (result: any): ColumnInfo[] => {
    const cols = result?.columns || result?.queryDetail?.columns || result?.metaData?.columns || [];
    return normalizeColumns(cols);
};

const extractColumnsFromSelectRows = (result: any): ColumnInfo[] => {
    const cols = result?.metaData?.fields || result?.metaData?.columns || result?.columnModel || result?.columns || [];
    return normalizeColumns(cols);
};

const QueryDetails: FC<{
    schemaName: string;
    queryName: string;
    isUserDefined?: boolean;
    onLookupClick: (schemaName: string, queryName: string, containerPath?: string) => void;
}> = ({ schemaName, queryName, isUserDefined, onLookupClick }) => {
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<string | undefined>();
    const [columns, setColumns] = useState<ColumnInfo[] | undefined>();

    // Fetch details when schema/query changes
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(undefined);
        setColumns(undefined);

        const done = (cols: ColumnInfo[]) => {
            if (cancelled) return;
            setColumns(cols);
            setLoading(false);
        };
        const fail = (msg?: string) => {
            if (cancelled) return;
            setError(msg || 'Unable to load query details.');
            setColumns([]);
            setLoading(false);
        };

        const trySelectRowsFallback = () => {
            if (!LABKEY?.Query?.selectRows) {
                fail('Query APIs not available.');
                return;
            }
            LABKEY.Query.selectRows({
                schemaName,
                queryName,
                maxRows: 0,
                containerPath: getContainerPath(),
                success: (res: any) => done(extractColumnsFromSelectRows(res)),
                failure: (err: any) => fail(err?.exception || err?.message),
            });
        };

        if (LABKEY?.Query?.getQueryDetails) {
            LABKEY.Query.getQueryDetails({
                schemaName,
                queryName,
                containerPath: getContainerPath(),
                success: (res: any) => {
                    const cols = extractColumnsFromGetQueryDetails(res);
                    if (cols && cols.length) done(cols);
                    else trySelectRowsFallback();
                },
                failure: () => trySelectRowsFallback(),
            });
        } else {
            trySelectRowsFallback();
        }

        return () => {
            cancelled = true;
        };
    }, [schemaName, queryName]);

    return (
        <div style={{ padding: 16 }}>
            <h2>
                {schemaName}.<span style={{ color: '#555' }}>{queryName}</span>
            </h2>
            <div style={{ display: 'flex', gap: 8, marginBottom: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <a className="labkey-text-link" href={buildExecuteUrl(schemaName, queryName)}>
                    View Data Grid
                </a>
                <a className="labkey-text-link" href={buildNewQueryUrl(schemaName, queryName)}>
                    Derive New Query
                </a>
                {(() => {
                    const mc = LABKEY?.moduleContext || {};
                    const currentUser = LABKEY?.Security?.currentUser || {};
                    const hasEditPerm = mc?.query?.hasEditQueriesPermission && currentUser?.canUpdate;
                    // Show edit if we have permission and either know it's user-defined or we don't know
                    const showEdit = !!hasEditPerm && isUserDefined !== false;
                    return (
                        showEdit && (
                            <a className="labkey-text-link" href={buildEditQueryUrl(schemaName, queryName)}>
                                Edit Query
                            </a>
                        )
                    );
                })()}
            </div>
            {loading && <div>Loading query details…</div>}
            {error && <div style={{ color: 'crimson' }}>{error}</div>}
            {!loading && columns && (
                <div>
                    <div style={{ fontWeight: 600, margin: '8px 0' }}>Columns</div>
                    {columns.length === 0 ? (
                        <div style={{ color: '#666' }}>No columns available.</div>
                    ) : (
                        <div style={{ overflowX: 'auto' }}>
                            <table style={{ borderCollapse: 'collapse', width: '100%' }}>
                                <thead>
                                    <tr>
                                        <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: '6px 8px' }}>Column</th>
                                        <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: '6px 8px' }}>Caption</th>
                                        <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: '6px 8px' }}>Type</th>
                                        <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: '6px 8px' }}>Nullable</th>
                                        <th style={{ textAlign: 'left', borderBottom: '1px solid #ddd', padding: '6px 8px' }}>Lookup</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {columns.map(col => (
                                        <tr key={col.name}>
                                            <td style={{ borderBottom: '1px solid #eee', padding: '6px 8px', whiteSpace: 'nowrap' }}>{col.name}</td>
                                            <td style={{ borderBottom: '1px solid #eee', padding: '6px 8px' }}>{col.caption || ''}</td>
                                            <td style={{ borderBottom: '1px solid #eee', padding: '6px 8px' }}>{col.type || ''}</td>
                                            <td style={{ borderBottom: '1px solid #eee', padding: '6px 8px' }}>
                                                {col.nullable === undefined ? '' : col.nullable ? 'Yes' : 'No'}
                                            </td>
                                            <td style={{ borderBottom: '1px solid #eee', padding: '6px 8px' }}>
                                                {col.lookup?.schemaName && col.lookup?.queryName ? (
                                                    <button
                                                        style={{ background: 'transparent', color: '#0366d6', border: 'none', cursor: 'pointer', padding: 0 }}
                                                        onClick={() =>
                                                            onLookupClick(
                                                                col.lookup!.schemaName!,
                                                                col.lookup!.queryName!,
                                                                col.lookup!.containerPath
                                                            )
                                                        }
                                                        title={
                                                            col.lookup!.containerPath && col.lookup!.containerPath !== getContainerPath()
                                                                ? `Open ${col.lookup!.schemaName}.${col.lookup!.queryName} in ${col.lookup!.containerPath}`
                                                                : `Open ${col.lookup!.schemaName}.${col.lookup!.queryName}`
                                                        }
                                                    >
                                                        {col.lookup!.schemaName}.{col.lookup!.queryName}
                                                    </button>
                                                ) : (
                                                    <span style={{ color: '#888' }}>—</span>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}

                    {/* Dependencies Section */}
                    <div style={{ fontWeight: 600, margin: '16px 0 8px' }}>Dependencies</div>
                    {(() => {
                        const depMap: { [key: string]: { schemaName: string; queryName: string; containerPath?: string } } = {};
                        columns.forEach(c => {
                            const lk = c.lookup;
                            if (lk?.schemaName && lk?.queryName) {
                                const key = `${lk.containerPath || ''}|${lk.schemaName}|${lk.queryName}`;
                                if (!depMap[key]) depMap[key] = { schemaName: lk.schemaName, queryName: lk.queryName, containerPath: lk.containerPath };
                            }
                        });
                        const deps = Object.values(depMap);
                        if (deps.length === 0) {
                            return <div style={{ color: '#666' }}>No dependencies found.</div>;
                        }
                        return (
                            <ul style={{ paddingLeft: 16, margin: 0 }}>
                                {deps.map(d => (
                                    <li key={`${d.containerPath || 'this'}:${d.schemaName}.${d.queryName}`} style={{ margin: '4px 0' }}>
                                        <button
                                            style={{ background: 'transparent', color: '#0366d6', border: 'none', cursor: 'pointer', padding: 0 }}
                                            onClick={() => onLookupClick(d.schemaName, d.queryName, d.containerPath)}
                                            title={
                                                d.containerPath && d.containerPath !== getContainerPath()
                                                    ? `Open ${d.schemaName}.${d.queryName} in ${d.containerPath}`
                                                    : `Open ${d.schemaName}.${d.queryName}`
                                            }
                                        >
                                            {d.schemaName}.{d.queryName}
                                        </button>
                                        {d.containerPath && d.containerPath !== getContainerPath() && (
                                            <span style={{ color: '#888', marginLeft: 6 }}>({d.containerPath})</span>
                                        )}
                                    </li>
                                ))}
                            </ul>
                        );
                    })()}
                </div>
            )}
        </div>
    );
};

export const BrowserApp: FC = () => {
    const { route, setRoute } = useHashRoute();
    const [schemas, setSchemas] = useState<SchemaItem[]>([]);
    const [schemasLoading, setSchemasLoading] = useState<boolean>(false);
    const [queries, setQueries] = useState<QueryItem[]>([]);
    const [queriesLoading, setQueriesLoading] = useState<boolean>(false);

    const selectedSchema = route.schemaName;
    const selectedQuery = route.queryName;

    // Load schemas on mount
    useEffect(() => {
        let cancelled = false;
        setSchemasLoading(true);
        const onSuccess = (result: any) => {
            if (cancelled) return;
            // result.schemas may be an object keyed by name
            const items: SchemaItem[] = [];
            if (result?.schemas) {
                if (Array.isArray(result.schemas)) {
                    result.schemas.forEach((s: any) => items.push({ name: s.name || s, displayName: s.displayName }));
                } else {
                    Object.keys(result.schemas).forEach(name => items.push({ name, displayName: result.schemas[name]?.name || name }));
                }
            }
            items.sort((a, b) => a.name.localeCompare(b.name));
            setSchemas(items);
            setSchemasLoading(false);
        };
        const onFailure = () => {
            if (cancelled) return;
            setSchemas([]);
            setSchemasLoading(false);
        };
        if (LABKEY?.Query?.getSchemas) {
            LABKEY.Query.getSchemas({
                containerPath: getContainerPath(),
                success: onSuccess,
                failure: onFailure,
            });
        } else {
            onFailure();
        }
        return () => {
            cancelled = true;
        };
    }, []);

    // Load queries when schema changes
    useEffect(() => {
        if (!selectedSchema) {
            setQueries([]);
            return;
        }
        let cancelled = false;
        setQueriesLoading(true);
        const onSuccess = (result: any) => {
            if (cancelled) return;
            const list: QueryItem[] = [];
            const queries = result?.queries || result?.QuerySet || result?.querySet || [];
            const toIsUserDefined = (q: any): boolean | undefined => {
                return (
                    q?.isUserDefined ??
                    q?.userDefined ??
                    q?.isUserDefinedQuery ??
                    q?.isUserQuery ??
                    undefined
                );
            };
            if (Array.isArray(queries)) {
                queries.forEach((q: any) => {
                    const name = q?.name || q?.queryName || q;
                    if (name) list.push({ name, schemaName: selectedSchema, isUserDefined: toIsUserDefined(q) });
                });
            } else if (queries?.queries) {
                // sometimes nested
                queries.queries.forEach((q: any) => list.push({ name: q.name, schemaName: selectedSchema, isUserDefined: toIsUserDefined(q) }));
            }
            list.sort((a, b) => a.name.localeCompare(b.name));
            setQueries(list);
            setQueriesLoading(false);
        };
        const onFailure = () => {
            if (cancelled) return;
            setQueries([]);
            setQueriesLoading(false);
        };
        if (LABKEY?.Query?.getQueries) {
            LABKEY.Query.getQueries({
                containerPath: getContainerPath(),
                schemaName: selectedSchema,
                includeUserQueries: true,
                includeSystemQueries: true,
                success: onSuccess,
                failure: onFailure,
            });
        } else {
            onFailure();
        }
        return () => {
            cancelled = true;
        };
    }, [selectedSchema]);

    const onSchemaClick = useCallback((schemaName: string) => setRoute(schemaName), [setRoute]);
    const onQueryClick = useCallback((schemaName: string, queryName: string) => setRoute(schemaName, queryName), [setRoute]);

    return (
        <div className="schemabrowser" style={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%' }}>
            <Toolbar schemaName={selectedSchema} queryName={selectedQuery} />
            <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
                {/* Left: Schema/Query tree */}
                <div style={{ width: 320, minWidth: 240, maxWidth: 480, borderRight: '1px solid #e0e0e0', overflow: 'auto' }}>
                    <div style={{ padding: 8, fontWeight: 600 }}>Schemas</div>
                    {schemasLoading ? (
                        <div style={{ padding: 8 }}>Loading schemas…</div>
                    ) : (
                        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
                            {schemas.map(s => (
                                <li key={s.name}>
                                    <button
                                        style={{
                                            display: 'block',
                                            width: '100%',
                                            textAlign: 'left',
                                            padding: '6px 10px',
                                            background: selectedSchema === s.name ? '#f5f5f5' : 'transparent',
                                            border: 'none',
                                            cursor: 'pointer',
                                        }}
                                        onClick={() => onSchemaClick(s.name)}
                                        title={s.displayName || s.name}
                                    >
                                        {s.displayName || s.name}
                                    </button>
                                    {selectedSchema === s.name && (
                                        <div style={{ paddingLeft: 10 }}>
                                            {queriesLoading ? (
                                                <div style={{ padding: '4px 10px' }}>Loading queries…</div>
                                            ) : (
                                                <ul style={{ listStyle: 'none', paddingLeft: 0, margin: 0 }}>
                                                    {queries.map(q => (
                                                        <li key={q.name}>
                                                            <button
                                                                style={{
                                                                    display: 'block',
                                                                    width: '100%',
                                                                    textAlign: 'left',
                                                                    padding: '4px 10px',
                                                                    background: selectedQuery === q.name ? '#eaf3ff' : 'transparent',
                                                                    border: 'none',
                                                                    cursor: 'pointer',
                                                                    color: '#0366d6',
                                                                }}
                                                                onClick={() => onQueryClick(q.schemaName, q.name)}
                                                            >
                                                                {q.name}
                                                            </button>
                                                        </li>
                                                    ))}
                                                    {queries.length === 0 && !queriesLoading && (
                                                        <li style={{ padding: '4px 10px', color: '#666' }}>No queries</li>
                                                    )}
                                                </ul>
                                            )}
                                        </div>
                                    )}
                                </li>
                            ))}
                        </ul>
                    )}
                </div>

                {/* Right: Details */}
                <div style={{ flex: 1, overflow: 'auto' }}>
                    {!selectedSchema && (
                        <div style={{ padding: 16 }}>
                            <h2>Welcome</h2>
                            <p>Select a schema on the left to view its queries.</p>
                        </div>
                    )}
                    {selectedSchema && !selectedQuery && (
                        <div style={{ padding: 16 }}>
                            <h2>{selectedSchema}</h2>
                            <p>Select a query to view details.</p>
                        </div>
                    )}
                    {selectedSchema && selectedQuery && (
                        <QueryDetails
                            schemaName={selectedSchema}
                            queryName={selectedQuery}
                            isUserDefined={queries.find(q => q.schemaName === selectedSchema && q.name === selectedQuery)?.isUserDefined}
                            onLookupClick={(schema: string, query: string, containerPath?: string) => {
                                if (containerPath && containerPath !== getContainerPath()) {
                                    const url = LABKEY.ActionURL.buildURL('query', 'begin', containerPath, {
                                        schemaName: schema,
                                        queryName: query,
                                    });
                                    window.open(url);
                                } else {
                                    onQueryClick(schema, query);
                                }
                            }}
                        />
                    )}
                </div>
            </div>
        </div>
    );
};
