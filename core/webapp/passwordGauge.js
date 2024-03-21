/*
 * Copyright (c) 2015-2023 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.PasswordGauge = new function() {

    let _passwordId;            // the element ID for the password text box to listen on
    let _emailId;               // the element ID for the email text box
    let _emailAddress;          // optional email address (for the AJAX request)
    let _renderBarFunction;

    // private methods
    function _drawOutline(canvas, ctx, ratio) {
        ctx.lineWidth = 3 * ratio;
        ctx.strokeRect(0, 0, canvas.width, canvas.height);
        return ctx.lineWidth / 2; // IDK why the border seems to be drawn at half of lineWidth
    }

    // Modifies a canvas element's resolution to match the native monitor resolution. This results in much clearer text.
    // See https://stackoverflow.com/questions/15661339/how-do-i-fix-blurry-text-in-my-html5-canvas
    function _increaseResolution(canvas, ctx, originalWidth, originalHeight) {
        const dpr = window.devicePixelRatio || 1;
        const bsr = ctx.webkitBackingStorePixelRatio ||
                ctx.mozBackingStorePixelRatio ||
                ctx.msBackingStorePixelRatio ||
                ctx.oBackingStorePixelRatio ||
                ctx.backingStorePixelRatio || 1;

        const ratio = dpr / bsr;

        canvas.width = originalWidth * ratio;
        canvas.height = originalHeight * ratio;
        canvas.style.width = originalWidth + "px";
        canvas.style.height = originalHeight + "px";

        // Uncomment if you want to draw using the original width + height coordinates instead new high resolution coordinates
        //ctx.setTransform(ratio, 0, 0, ratio, 0, 0);

        return ratio;
    }

    function _render(canvas, ctx, originalWidth, originalHeight) {
        const ratio = _increaseResolution(canvas, ctx, originalWidth, originalHeight);
        const borderWidth = _drawOutline(canvas, ctx, ratio) + ratio; // leave one css pixel between border and bar
        const maxBarWidth = canvas.width - 2 * borderWidth;
        const barHeight = canvas.height - 2 * borderWidth;
        const centerX = canvas.width / 2;
        const centerY = canvas.height / 2;

        ctx.lineWidth = ratio;
        ctx.font = 12 * ratio + "pt Sans-Serif"
        ctx.textAlign = "center";
        ctx.textBaseLine = "middle";

        // textBaseLine = middle sets the text too high, IMO. Adjust by half the size of the vertical bound.
        const metrics = ctx.measureText("H");
        const textHeightFix = (metrics.fontBoundingBoxAscent - metrics.fontBoundingBoxDescent) / 2 - 1;

        const password = document.getElementById(_passwordId);
        const email = _emailAddress;
        const emailField = document.getElementById(_emailId);

        // Remove existing event, if present (resize case)
        if (_renderBarFunction)
            password.removeEventListener('input', _renderBarFunction);

        _renderBarFunction = function() {
            const showPlaceholderText = !password.value;
            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("login", "getPasswordScore.api"),
                method: 'POST',
                params: {
                    password: password.value,
                    email: email || emailField?.value
                },
                success: LABKEY.Utils.getCallbackWrapper(function(responseText) {
                    // Clear everything inside the border. You might think I could clear using the same coordinates as
                    // I fill below (that's what I thought, anyway), but this tended to leave single-pixel trails of
                    // color behind. I clear a larger rectangle than what I filled to erase these trails.
                    ctx.clearRect(borderWidth - ratio, borderWidth - ratio, maxBarWidth + 2 * ratio, barHeight + 2 * ratio);

                    // Render bar
                    const percent = Math.min(responseText.score / 90, 0.99999);
                    const colorIndex = Math.floor(percent * 3);
                    ctx.fillStyle = ["red", "yellow", "green"][colorIndex];
                    const barWidth = percent * maxBarWidth;
                    ctx.fillRect(borderWidth, borderWidth, barWidth, barHeight);

                    // Render text
                    ctx.fillStyle = 2 === colorIndex ? "white" : showPlaceholderText ? "gray" : "black";
                    const textIndex = Math.floor(percent * 6);
                    const text = showPlaceholderText ?  "Password Strength Gauge" : ["Very Weak", "Very Weak", "Weak", "Weak", "Strong", "Very Strong"][textIndex];
                    ctx.fillText(text, centerX, centerY + textHeightFix);
                    canvas.innerText = text;
                })
            });
        };

        password.addEventListener('input', _renderBarFunction);
        _renderBarFunction(); // Proactive render otherwise box will be empty after resizing until next change
    }

    // public methods
    return {
        /**
         * Construct the password strength gauge and render it to the specified div.
         *
         * @param renderTo The div to render this component to
         * @param passwordElementId The element ID for the password text box to validate
         * @param emailElementId The element ID for the email address (optional)
         * @param emailAddress The email address to be sent when validating password strength (optional)
         */
        createComponent : function(renderTo, passwordElementId, emailElementId, emailAddress) {
            const canvas = document.getElementById(renderTo);
            if (canvas) {
                _passwordId = passwordElementId;
                _emailId = emailElementId;
                _emailAddress = emailAddress;
                const ctx = canvas.getContext("2d");

                if (ctx) {
                    // Stashing original width & height helps on resize
                    const canvasWidth = canvas.width;
                    const canvasHeight = canvas.height;
                    _render(canvas, ctx, canvasWidth, canvasHeight);
                    window.addEventListener("resize", function () {
                        _render(canvas, ctx, canvasWidth, canvasHeight);
                    });
                }
            }
        }
    }
};