
export const reorder = (list: number[], startIndex: number, endIndex: number): number[] => {
    const result = Array.from(list);
    const [removed] = result.splice(startIndex, 1);
    result.splice(endIndex, 0, removed);

    return result;
};

export const isEquivalent = (a: any, b: any): boolean => {
    const aProps = Object.keys(a);
    const bProps = Object.keys(b);

    if (aProps.length != bProps.length) {
        return false;
    }

    return !aProps.some(key => {
        return a[key] !== b[key];
    });
};

export const addOrUpdateAnAuthConfig = (config, prevState, stateSection) => {
    const configObj = JSON.parse(config);
    const configId = configObj.configuration.configuration;
    const staleAuthIndex = prevState.findIndex(element => element.configuration == configId);

    let newState = prevState.slice(0);
    if (staleAuthIndex == -1) {
        if (stateSection == 'formConfigurations') {
            newState = [...newState.slice(0, -1), configObj.configuration, ...newState.slice(-1)];
        } else {
            newState.push(configObj.configuration);
        }
    } else {
        newState[staleAuthIndex] = configObj.configuration;
    }
    return newState;
};