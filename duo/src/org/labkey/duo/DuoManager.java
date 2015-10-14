/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */

package org.labkey.duo;

public class DuoManager
{
    private static final DuoManager _instance = new DuoManager();

    private DuoManager()
    {
        // prevent external construction with a private default constructor
    }

    public static DuoManager get()
    {
        return _instance;
    }
}