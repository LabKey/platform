/*
 * Copyright (c) 2017 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
module.exports = (ctx) => ({
    parser: ctx.parser ? 'sugarss' : false,
    map: ctx.env === 'development' ? ctx.map : false
});