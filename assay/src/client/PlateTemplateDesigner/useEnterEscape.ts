/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import { useCallback, KeyboardEvent } from 'react';

/**
 * Returns an onKeyDown handler that calls onEnter when Enter is pressed and
 * onEscape when Escape is pressed (both with no modifier keys held).
 * Inlined from @labkey/components to avoid that package's module-level side
 * effects, which require a live server context and break unit tests.
 */
export const useEnterEscape = (onEnter?: () => void, onEscape?: () => void): ((evt: KeyboardEvent) => void) => {
    return useCallback(
        (evt: KeyboardEvent) => {
            if (evt.shiftKey || evt.metaKey) return;
            switch (evt.key) {
                case 'Enter':
                    evt.stopPropagation();
                    evt.preventDefault();
                    onEnter?.();
                    break;
                case 'Escape':
                    evt.stopPropagation();
                    evt.preventDefault();
                    onEscape?.();
                    break;
            }
        },
        [onEnter, onEscape]
    );
};
