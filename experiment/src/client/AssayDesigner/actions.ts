/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import {Ajax, Utils} from "@labkey/api";
import {buildURL} from "@glass/base";

import {AssayProtocolModel} from "./models";

// TODO remove as this is now in @glass/base
export function fetchProtocol(protocolId: number): Promise<AssayProtocolModel> {
    return new Promise((resolve, reject) => {
        Ajax.request({
            url: buildURL('assay', 'getProtocol.api', { protocolId }),
            success: Utils.getCallbackWrapper((data) => {
                if (data.data) {
                    resolve(new AssayProtocolModel(data.data));
                }
                else {
                    reject('Unable to find assay protocol for id: ' + protocolId + '.');
                }
            }),
            failure: Utils.getCallbackWrapper((error) => {
                reject(error ? error.exception: 'An unknown error occurred getting the assay protocol data from the server.');
            })
        })
    });
}