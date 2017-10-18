/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = (ctx) => ({
    parser: ctx.parser ? 'sugarss' : false,
    map: ctx.env === 'development' ? ctx.map : false
});