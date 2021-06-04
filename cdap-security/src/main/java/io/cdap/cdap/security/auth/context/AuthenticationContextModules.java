/*
 * Copyright © 2016 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.cdap.cdap.security.auth.context;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import io.cdap.cdap.security.spi.authentication.AuthenticationContext;
import io.cdap.cdap.security.spi.authentication.SecurityRequestContext;

/**
 * Exposes the right {@link AuthenticationContext} via an {@link AbstractModule} based on the context in which
 * it is being invoked.
 */
public class AuthenticationContextModules {
  /**
   * An {@link AuthenticationContext} for HTTP requests in Master. The authentication details in this context are
   * derived from {@link SecurityRequestContext}.
   *
   * @see SecurityRequestContext
   */
  public Module getMasterModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(AuthenticationContext.class).to(MasterAuthenticationContext.class);
      }
    };
  }

  /**
   * An {@link AuthenticationContext} for use in program containers. The authentication details in this context are
   * self-contained and are generated based on the locally-mounted secrets.
   */
  public Module getProgramContainerModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(AuthenticationContext.class).to(ProgramContainerAuthenticationContext.class);
      }
    };
  }

  /**
   * An {@link AuthenticationContext} for use in tests that do not need authentication/authorization. The
   * authentication details in this context are determined based on the {@link System#props user.name} system property.
   */
  public Module getNoOpModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(AuthenticationContext.class).to(AuthenticationTestContext.class);
      }
    };
  }
}
