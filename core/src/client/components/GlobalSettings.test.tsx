import React from 'react';
import { render } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { GLOBAL_SETTINGS } from '../../../test/data';

import { GlobalSettings } from './GlobalSettings';

describe('<GlobalSettings/>', () => {
    test('Clicking a checkbox toggles the checkbox', async () => {
        const checkGlobalAuthBox = jest.fn();
        render(
            <GlobalSettings
                globalSettings={GLOBAL_SETTINGS}
                canEdit={true}
                authCount={3}
                onChange={checkGlobalAuthBox}
            />
        );

        // Click self registration checkbox
        const firstCheckBox = document.querySelector('input[type="checkbox"]');
        await userEvent.click(firstCheckBox);
        expect(checkGlobalAuthBox).toHaveBeenCalled();
    });

    test('An authCount of 1 eliminates the option to auto-create authenticated users', () => {
        const checkGlobalAuthBox = jest.fn();
        const { rerender } = render(
            <GlobalSettings
                authCount={3}
                canEdit={true}
                globalSettings={GLOBAL_SETTINGS}
                onChange={checkGlobalAuthBox}
            />
        );

        expect(document.querySelectorAll('input[type="checkbox"]').length).toBe(3);
        rerender(
            <GlobalSettings
                authCount={1}
                canEdit={true}
                globalSettings={GLOBAL_SETTINGS}
                onChange={checkGlobalAuthBox}
            />
        );
        expect(document.querySelectorAll('input[type="checkbox"]').length).toBe(2);
        expect(document.querySelector('.panel-body').innerHTML).not.toMatch(/Auto-create authenticated users/);
    });

    test('view-only mode', () => {
        render(
            <GlobalSettings globalSettings={GLOBAL_SETTINGS} canEdit={false} authCount={3} onChange={jest.fn()} />
        );

        expect(document.querySelectorAll('input[disabled=""]')).toHaveLength(4);
    });
});
