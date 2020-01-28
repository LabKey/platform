
// to reviewer: do we have a helper function for this somewhere? I started writing this
// using slice instead of splice for immutability, but since I'm leaning on splice's additional
// parameters, the slice version gets pretty gross.
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
    console.log("configObj", configObj);
    console.log("prevStrae", prevState);
    const configId = configObj.configuration.configuration;

    const staleAuthIndex = prevState.findIndex(element => element.configuration == configId);

    let newState = prevState.slice(0); // To reviewer: This avoids mutation of prevState, but is it overzealous?
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