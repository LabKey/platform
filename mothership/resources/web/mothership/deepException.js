window.__Mothership = (function () {
    return {
        throwAsyncException: function() {
            new Promise((resolve) => {
                setTimeout(() => {
                    resolve({});
                }, 500);
            }).then((result) => {
                const x = result.does.not.exist;  // Fail to dereference "does"
            });
        },
        throwNestedException: function() {
            const x = undefined;
            const a = x.y.z; // Fail to dereference "y"
        },
    }
})();