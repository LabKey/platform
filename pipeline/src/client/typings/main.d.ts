/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * @deprecated Use getServerContext() from @labkey/api instead
 */
declare const LABKEY: import('@labkey/api').LabKey;

/**
 * Needed so we can use process.env.NODE_ENV, which is injected by webpack, but not included in the types declared in
 * the browser environments.
 */
declare const process: {
    env: {
        NODE_ENV: string;
    };
};
