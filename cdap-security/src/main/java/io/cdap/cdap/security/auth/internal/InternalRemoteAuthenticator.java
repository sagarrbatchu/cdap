/*
 * Copyright © 2021 Cask Data, Inc.
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

package io.cdap.cdap.security.auth.internal;

import com.google.inject.Inject;
import io.cdap.cdap.common.internal.remote.RemoteAuthenticator;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.security.spi.authentication.AuthenticationContext;

import java.io.IOException;

/**
 * A {@link RemoteAuthenticator} that authenticate internal remote calls using an access token from an existing
 * authentication context.
 */
public class InternalRemoteAuthenticator extends RemoteAuthenticator {
  private static final String BEARER_TYPE = "Bearer";

  private final AuthenticationContext authenticationContext;

  @Inject
  public InternalRemoteAuthenticator(AuthenticationContext authenticationContext) {
    this.authenticationContext = authenticationContext;
  }

  @Override
  public String getType() throws IOException {
    return BEARER_TYPE;
  }

  @Override
  public boolean hasCredential() {
    Principal principal = authenticationContext.getPrincipal();
    return principal != null && principal.getCredential() != null;
  }

  @Override
  public String getCredentials() throws IOException {
    return authenticationContext.getPrincipal().getCredential();
  }
}