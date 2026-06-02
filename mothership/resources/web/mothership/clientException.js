/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function resourceScriptError() {
    const x = undefined;
    const a = x.y.z; // Fail to dereference y
}

function asyncScriptError() {
    window.__Mothership.throwAsyncException();
}

function nestedResourceScriptError() {
    window.__Mothership.throwNestedException();
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('resource-script').addEventListener('click', resourceScriptError);
    document.getElementById('nested-script').addEventListener('click', nestedResourceScriptError);
    document.getElementById('async-script').addEventListener('click', asyncScriptError);
});
