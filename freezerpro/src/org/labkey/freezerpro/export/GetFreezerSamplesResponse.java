/*
 * Copyright (c) 2014-2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.freezerpro.export;

import org.labkey.api.pipeline.PipelineJob;
import org.labkey.freezerpro.FreezerProConfig;

/**
 * Created by klum on 10/28/2014.
 */
public class GetFreezerSamplesResponse extends FreezerProCommandResponse
{
    public static final String DATA_NODE_NAME = "Samples";

    public GetFreezerSamplesResponse(FreezerProExport export, String text, int statusCode, PipelineJob job)
    {
        super(export, text, statusCode, DATA_NODE_NAME, job);
    }

    @Override
    public String translateFieldName(String fieldName)
    {
        // special case for this API using obj_id and id as the sample id
        if ("obj_id".equalsIgnoreCase(fieldName))
            return FreezerProConfig.SAMPLE_ID_FIELD_NAME;
        else
            return super.translateFieldName(fieldName);
    }
}
