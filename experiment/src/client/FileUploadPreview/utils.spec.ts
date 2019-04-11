
import { List } from 'immutable'
import { isFunction } from "./utils";

// describe("isFunction", () => {
//     test("falseCases", () => {
//         expect(isFunction("hello world")).toBe(false);
//         expect(isFunction(12)).toBe(false);
//         expect(isFunction([])).toBe(false);
//         expect(isFunction({})).toBe(false);
//     });
//     test("trueCases", () => {
//         expect(isFunction(Array.isArray)).toBe(true);
//         var fn = () => {return;};
//         expect(isFunction(fn)).toBe(true);
//     });
// })