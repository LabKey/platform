/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
window.__Mothership = (function () {
    return {
        throwAsyncException: function() {
            new Promise((resolve) => {
                setTimeout(() => {
                    resolve({});
                }, 500);
            }).then((result) => {
                const x = result.does.not.exist;  // Fail to dereference "does"
            });
        },
        throwNestedException: function() {
            const x = undefined;
            const a = x.y.z; // Fail to dereference "y"
        },
    }
})();