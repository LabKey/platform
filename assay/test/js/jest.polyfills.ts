/*
 * Copyright (c) 2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import { TextDecoder, TextEncoder } from 'util';

// JSDom treats TextEncoder/TextDecoder as Node globals and omits them, but react-router constructs an encoder at module
// scope, so anything importing it throws before a test can run. Browsers provide these natively.
globalThis.TextEncoder = TextEncoder;
globalThis.TextDecoder = TextDecoder as typeof globalThis.TextDecoder;
