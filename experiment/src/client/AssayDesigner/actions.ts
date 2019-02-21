/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import {Ajax, Utils} from "@labkey/api";
import {SchemaQuery} from "@glass/models";
import {buildURL} from "@glass/utils";

import {AssayProtocolModel} from "./models";

export function fetchProtocol(protocolId: number): Promise<AssayProtocolModel> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('assay', 'protocol.api', { protocolId }),
            success: Utils.getCallbackWrapper((data) => {
                if (data.data) {
                    resolve(new AssayProtocolModel(data.data));
                }
                else {
                    reject('Unable to find assay protocol for id: ' + protocolId + '.');
                }
            }),
            failure: Utils.getCallbackWrapper((error) => {
                reject(error.exception);
            })
        })
    });
}