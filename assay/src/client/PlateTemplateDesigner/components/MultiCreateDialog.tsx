/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React, { useEffect, useRef, useState } from 'react';

interface MultiCreateDialogProps {
    initialBaseName: string;
    existingNames: Set<string>;
    onClose: () => void;
    onConfirm: (names: string[]) => void;
}

export function MultiCreateDialog({ initialBaseName, existingNames, onClose, onConfirm }: MultiCreateDialogProps): JSX.Element {
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
                if (document.activeElement === first) { e.preventDefault(); last?.focus(); }
            } else {
                if (document.activeElement === last) { e.preventDefault(); first?.focus(); }
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

    const handleCreate = () => {
        const parsedCount = parseInt(count, 10);
        if (isNaN(parsedCount) || parsedCount < 1) {
            setCountError(`"${count}" is not a valid count.`);
            return;
        }
        const trimmedBase = baseName.trim();
        if (!trimmedBase) return;
        const namesToCreate = Array.from({ length: parsedCount }, (_, i) => `${trimmedBase} ${i + 1}`)
            .filter(name => !existingNames.has(name));
        if (namesToCreate.length === 0) {
            setCountError(`All ${parsedCount} generated name${parsedCount === 1 ? '' : 's'} already exist in this type.`);
            return;
        }
        onConfirm(namesToCreate);
    };

    return (
        <dialog
            ref={dialogRef}
            className="multi-create-dialog__overlay"
            aria-labelledby="multi-create-title"
            onClick={onClose}
        >
            <div className="multi-create-dialog" onClick={e => e.stopPropagation()}>
                <div id="multi-create-title" className="multi-create-dialog__title">Create Multiple Groups</div>
                <div className="multi-create-dialog__form">
                    <div className="multi-create-dialog__field">
                        <span id="multi-create-base-name-label" className="multi-create-dialog__label">Base Name</span>
                        <input
                            className="multi-create-dialog__input"
                            type="text"
                            aria-labelledby="multi-create-base-name-label"
                            value={baseName}
                            onChange={e => setBaseName(e.target.value)}
                            onKeyDown={e => { if (e.key === 'Enter') handleCreate(); if (e.key === 'Escape') onClose(); }}
                        />
                    </div>
                    <div className="multi-create-dialog__field">
                        <span id="multi-create-count-label" className="multi-create-dialog__label">Count</span>
                        <div className="multi-create-dialog__count-area">
                            <input
                                className="multi-create-dialog__input multi-create-dialog__input--count"
                                type="number"
                                min="1"
                                aria-labelledby="multi-create-count-label"
                                aria-describedby={countError ? 'multi-create-count-error' : undefined}
                                aria-invalid={!!countError}
                                value={count}
                                onChange={e => { setCount(e.target.value); setCountError(''); }}
                                onKeyDown={e => { if (e.key === 'Enter') handleCreate(); if (e.key === 'Escape') onClose(); }}
                            />
                            {countError && <div id="multi-create-count-error" className="multi-create-dialog__error">{countError}</div>}
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
}
