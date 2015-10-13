/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.oauth;

public class OAuthManager
{
    private static final OAuthManager _instance = new OAuthManager();

    private OAuthManager()
    {
        // prevent external construction with a private default constructor
    }

    public static OAuthManager get()
    {
        return _instance;
    }
}