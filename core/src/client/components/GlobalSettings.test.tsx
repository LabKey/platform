/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { render } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';

import { GLOBAL_SETTINGS } from '../../../test/data';

import { GlobalSettings } from './GlobalSettings';

describe('GlobalSettings', () => {
    test('Clicking a checkbox toggles the checkbox', async () => {
        const checkGlobalAuthBox = jest.fn();
        render(<GlobalSettings authCount={3} canEdit globalSettings={GLOBAL_SETTINGS} onChange={checkGlobalAuthBox} />);

        // Click self-registration checkbox
        const firstCheckBox = document.querySelector('input[type="checkbox"]');
        await userEvent.click(firstCheckBox);
        expect(checkGlobalAuthBox).toHaveBeenCalled();
    });

    test('An authCount of 1 eliminates the option to auto-create authenticated users', () => {
        const checkGlobalAuthBox = jest.fn();
        const { rerender } = render(
            <GlobalSettings authCount={3} canEdit globalSettings={GLOBAL_SETTINGS} onChange={checkGlobalAuthBox} />
        );

        expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(4);
        rerender(
            <GlobalSettings authCount={1} canEdit globalSettings={GLOBAL_SETTINGS} onChange={checkGlobalAuthBox} />
        );
        expect(document.querySelectorAll('input[type="checkbox"]')).toHaveLength(3);
        expect(document.querySelector('.panel-body').innerHTML).not.toMatch(/Auto-create authenticated users/);
    });

    test('view-only mode', () => {
        render(<GlobalSettings authCount={3} canEdit={false} globalSettings={GLOBAL_SETTINGS} onChange={jest.fn()} />);

        expect(document.querySelectorAll('input[disabled=""]')).toHaveLength(5);
    });
});
