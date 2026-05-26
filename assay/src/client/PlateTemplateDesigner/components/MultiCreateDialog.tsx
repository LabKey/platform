/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { FC, useCallback, useEffect, useRef, useState } from 'react';

import { useEnterEscape } from '../useEnterEscape';

interface MultiCreateDialogProps {
    existingNames: Set<string>;
    initialBaseName: string;
    onClose: () => void;
    onConfirm: (names: string[]) => void;
}

/** Modal dialog for batch-creating numbered well groups (e.g. "Sample 1" through "Sample 8") from a base name and count, skipping any names that already exist. */
export const MultiCreateDialog: FC<MultiCreateDialogProps> = ({
    initialBaseName,
    existingNames,
    onClose,
    onConfirm,
}) => {
    const [baseName, setBaseName] = useState(initialBaseName);
    const [count, setCount] = useState('2');
    const [countError, setCountError] = useState('');
    const dialogRef = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const dialog = dialogRef.current;
        if (!dialog) return;

        // Open as a modal (top layer + native backdrop; also restores focus on close).
        dialog.showModal();

        // Focus trap: cycle Tab/Shift-Tab within the dialog.
        const focusableSelectors = 'button, input, select, textarea, [tabindex]:not([tabindex="-1"])';
        const getFocusable = () => Array.from(dialog.querySelectorAll<HTMLElement>(focusableSelectors));

        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key !== 'Tab') return;
            const focusable = getFocusable();
            const first = focusable[0];
            const last = focusable[focusable.length - 1];
            if (e.shiftKey) {
                if (document.activeElement === first) {
                    e.preventDefault();
                    last?.focus();
                }
            } else {
                if (document.activeElement === last) {
                    e.preventDefault();
                    first?.focus();
                }
            }
        };

        // The native <dialog> fires a `cancel` event on Escape before closing itself.
        // Prevent the browser's default close so we control the unmounting via onClose.
        const handleCancel = (e: Event) => {
            e.preventDefault();
            onClose();
        };

        dialog.addEventListener('keydown', handleKeyDown);
        dialog.addEventListener('cancel', handleCancel);

        // Move focus to the first focusable element inside the dialog.
        getFocusable()[0]?.focus();

        return () => {
            dialog.removeEventListener('keydown', handleKeyDown);
            dialog.removeEventListener('cancel', handleCancel);
            if (dialog.open) dialog.close();
        };
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const handleCreate = useCallback(() => {
        const parsedCount = parseInt(count, 10);
        if (isNaN(parsedCount) || parsedCount < 1) {
            setCountError(`"${count}" is not a valid count.`);
            return;
        }
        const trimmedBase = baseName.trim();
        if (!trimmedBase) return;
        const namesToCreate = Array.from({ length: parsedCount }, (_, i) => `${trimmedBase} ${i + 1}`).filter(
            name => !existingNames.has(name)
        );
        if (namesToCreate.length === 0) {
            setCountError(
                `All ${parsedCount} generated name${parsedCount === 1 ? '' : 's'} already exist in this type.`
            );
            return;
        }
        onConfirm(namesToCreate);
    }, [count, baseName, existingNames, onConfirm]);

    const handleInputKeyDown = useEnterEscape(handleCreate, onClose);

    const handleBaseNameChange = useCallback(
        (e: React.ChangeEvent<HTMLInputElement>) => setBaseName(e.target.value),
        []
    );
    const handleCountChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        setCount(e.target.value);
        setCountError('');
    }, []);
    const handleContentClick = useCallback((e: React.MouseEvent) => e.stopPropagation(), []);

    return (
        <dialog
            aria-labelledby="multi-create-title"
            className="multi-create-dialog__overlay"
            onClick={onClose}
            ref={dialogRef}
        >
            <div className="multi-create-dialog" onClick={handleContentClick}>
                <div className="multi-create-dialog__title" id="multi-create-title">
                    Create Multiple Groups
                </div>
                <div className="multi-create-dialog__form">
                    <div className="multi-create-dialog__field">
                        <span className="multi-create-dialog__label" id="multi-create-base-name-label">
                            Base Name
                        </span>
                        <input
                            aria-labelledby="multi-create-base-name-label"
                            className="multi-create-dialog__input"
                            onChange={handleBaseNameChange}
                            onKeyDown={handleInputKeyDown}
                            type="text"
                            value={baseName}
                        />
                    </div>
                    <div className="multi-create-dialog__field">
                        <span className="multi-create-dialog__label" id="multi-create-count-label">
                            Count
                        </span>
                        <div className="multi-create-dialog__count-area">
                            <input
                                aria-describedby={countError ? 'multi-create-count-error' : undefined}
                                aria-invalid={!!countError}
                                aria-labelledby="multi-create-count-label"
                                className="multi-create-dialog__input multi-create-dialog__input--count"
                                min="1"
                                onChange={handleCountChange}
                                onKeyDown={handleInputKeyDown}
                                type="number"
                                value={count}
                            />
                            {countError && (
                                <div className="multi-create-dialog__error" id="multi-create-count-error">
                                    {countError}
                                </div>
                            )}
                        </div>
                    </div>
                    <div className="multi-create-dialog__buttons">
                        <button className="group-types-panel__add-btn" onClick={onClose}>
                            Cancel
                        </button>
                        <button
                            className="group-types-panel__add-btn group-types-panel__add-btn--primary"
                            disabled={!baseName.trim()}
                            onClick={handleCreate}
                        >
                            Create
                        </button>
                    </div>
                </div>
            </div>
        </dialog>
    );
};
MultiCreateDialog.displayName = 'MultiCreateDialog';
