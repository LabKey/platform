
export function isFunction(value: any): boolean {
    // http://stackoverflow.com/questions/5999998/how-can-i-check-if-a-javascript-variable-is-function-type
    const getType = {};
    return value !== null && value !== undefined && getType.toString.call(value) === '[object Function]';
}