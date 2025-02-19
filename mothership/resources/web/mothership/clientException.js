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
