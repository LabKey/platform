package org.radeox.test.filter.mock;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.context.FilterContext;

public class MockReplacedFilter implements Filter {
  public String filter(String input, FilterContext context) {
    return input;
  }

  public void setInitialContext(InitialRenderContext context) {
  }

  public String[] replaces() {
    return new String[0];
  }

  public String[] before() {
    return new String[0];
  }

  public String getDescription() {
    return "";
  }
}
