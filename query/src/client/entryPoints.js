/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'queryMetadataEditor',
        title: 'Metadata Editor',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see QueryController.MetadataQueryAction for main usage
        path: './src/client/QueryMetadataEditor'
    }]
};