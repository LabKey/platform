import { reorder, isEquivalent, addOrUpdateAnAuthConfig } from './utils'
import {ActionURL} from "@labkey/api";

describe('Utils', () => {
    test('reorder', () => {
        let l = [1, 2, 3, 4];

        // reorder outside of bounds
        expect(reorder(l, 3, 7)).toStrictEqual([1, 2, 3, 4]);
        // reorder within bounds
        expect(reorder(l, 1, 3)).toStrictEqual([1, 3, 4, 2]);
        // no reorder
        expect(reorder(l, 3, 3)).toStrictEqual([1, 2, 3, 4]);

    });

    test('isEquivalent', () => {
        let obj1 = {"a": 1, "b": 2, "c":3};
        let obj2 = {"a": 1, "b": 2, "c":3};
        let obj3 = {"a": 1, "b": 2, "c":100};

        expect(isEquivalent(obj1, obj2)).toBe(true);
        expect(isEquivalent(obj1, obj3)).toBe(false);
    });

    test('addOrUpdateAnAuthConfig', () => {
        let prevState = [{
            "provider" : "CAS",
            "configuration" : 106,
            "description" : "CAS Configuration 1",
        }, {
            "provider" : "CAS",
            "configuration" : 108,
            "description" : "CAS Configuration 2",
        }, {
            "provider" : "CAS",
            "configuration" : 109,
            "description" : "CAS Configuration 3",
        }];

        // vars for 'update a config' test
        let updatedConfig = JSON.stringify({
            "configuration": {
            "provider" : "CAS",
            "configuration" : 108,
            "description" : "NEW DESCRIPTION",
            },
        "success:": true
        });
        let newState1 = [{
            "provider" : "CAS",
            "configuration" : 106,
            "description" : "CAS Configuration 1",
        }, {
            "provider" : "CAS",
            "configuration" : 108,
            "description" : "NEW DESCRIPTION",
        }, {
            "provider" : "CAS",
            "configuration" : 109,
            "description" : "CAS Configuration 3",
        }];

        // vars for 'add a config' test
        let addedConfig = JSON.stringify({
            "configuration": {
                "provider" : "CAS",
                "configuration" : 110,
                "description" : "CAS Configuration X",
            },
            "success:": true
        });
        let newState2 = [{
            "provider" : "CAS",
            "configuration" : 106,
            "description" : "CAS Configuration 1",
        }, {
            "provider" : "CAS",
            "configuration" : 108,
            "description" : "CAS Configuration 2",
        }, {
            "provider" : "CAS",
            "configuration" : 109,
            "description" : "CAS Configuration 3",
        }, {
            "provider" : "CAS",
            "configuration" : 110,
            "description" : "CAS Configuration X",
        }];

        // update a config
        expect(addOrUpdateAnAuthConfig(updatedConfig, prevState, "ssoConfigurations")).toStrictEqual(newState1);
        // add a config
        expect(addOrUpdateAnAuthConfig(addedConfig, prevState, "ssoConfigurations")).toStrictEqual(newState2);
    });
});