/*
 * Copyright (c) 2017-2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import { configure } from 'enzyme';
import Adapter from 'enzyme-adapter-react-16';

// Enzyme expects an adapter to be configured
// http://airbnb.io/enzyme/docs/installation/react-16.html
configure({ adapter: new Adapter() });