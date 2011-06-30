package org.radeox.test.filter.mock;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.context.FilterContext;

public class MockReplacesFilter implements Filter {
  public String filter(String input, FilterContext context) {
    return input;
  }

  public String[] replaces() {
    return new String[] { "org.radeox.test.filter.mock.MockReplacedFilter" };
  }

  public void setInitialContext(InitialRenderContext context) {
  }

  public String[] before() {
    return new String[0];
  }

  public String getDescription() {
    return "";
  }
}
