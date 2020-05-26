export interface AppContext {
    lsid: string
    rowId: number
    target: string
}

export interface InitAppEventDetail<T> {
    appName: string
    appContext: T
    appTarget: string
}

interface AppRegistryItem {
    appName: string
    contexts: any[]
    hot: boolean
    onInit: Function
    targets: string[]
}

let appRegistry: {[appName:string]: AppRegistryItem} = {};
let isDOMContentLoaded = false;

// Global listener for initializing apps
window.addEventListener('initApp', (event: CustomEvent<InitAppEventDetail<any>>) => {
    const { appContext, appName, appTarget } = event.detail;

    if (appRegistry.hasOwnProperty(appName)) {
        if (appRegistry[appName].hot) {
            appRegistry[appName].contexts.push(appContext);
            appRegistry[appName].targets.push(appTarget);
        }

        if (isDOMContentLoaded) {
            appRegistry[appName].onInit(appTarget, appContext);
        } else {
            window.addEventListener('DOMContentLoaded', () => {
                isDOMContentLoaded = true;
                appRegistry[appName].onInit(appTarget, appContext);
            }, { once: true });
        }
    } else {
        throw Error(`Application "${appName}" is not a registered application. Unable to initialize.`);
    }
});

export function registerApp<T>(appName: string, onInit: (target: string, ctx: T) => any, hot?: boolean): void {
    if (!appRegistry.hasOwnProperty(appName)) {
        appRegistry[appName] = {
            appName,
            contexts: [],
            hot: hot === true,
            onInit,
            targets: [],
        };
    } else if (appRegistry[appName].hot) {
        runHot(appRegistry[appName]);
    }
}

function runHot(item: AppRegistryItem): void {
    if (!item.hot) {
        throw Error(`Attempting to run application ${item.appName} hot when hot is not enabled.`);
    }

    if (item.targets.length !== item.contexts.length) {
        throw Error(`Application registry for "${item.appName}" is in an invalid state. Expected targets and contexts to align.`);
    }

    for (let i=0; i < item.targets.length; i++) {
        item.onInit(item.targets[i], item.contexts[i]);
    }
}