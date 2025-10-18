import React from 'react';
import { createRoot } from 'react-dom/client';
import { Hello } from './hello';

window.addEventListener('DOMContentLoaded', () => {
    const el = document.getElementById('app');
    if (el) {
        createRoot(el).render(<Hello />);
    }
});