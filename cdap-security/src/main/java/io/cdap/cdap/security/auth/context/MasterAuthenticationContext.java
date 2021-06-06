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

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.security.spi.authentication.AuthenticationContext;
import io.cdap.cdap.security.spi.authentication.SecurityRequestContext;
import org.apache.hadoop.security.UserGroupInformation;

import java.io.IOException;

/**
 * An {@link AuthenticationContext} for HTTP requests in the Master. The authentication details in this context are
 * derived from:
 * <ol>
 *   <li>{@link SecurityRequestContext}, when the request; or</li>
 *   <li>{@link UserGroupInformation}, when the master itself is asynchronously updating privileges in the
 *   authorization policy cache.</li>
 * </ol>
 *
 * @see SecurityRequestContext
 * @see UserGroupInformation
 */
public class MasterAuthenticationContext implements AuthenticationContext {

  private final AuthenticationContext internalAuthDelegateContext;
  private final boolean enforceInternalAuth;

  @Inject
  public MasterAuthenticationContext(CConfiguration cConf, SystemAuthenticationContext internalAuthDelegateContext) {
    this.enforceInternalAuth = cConf.getBoolean(Constants.Security.Internal.ENFORCE_INTERNAL_AUTH);
    this.internalAuthDelegateContext = internalAuthDelegateContext;
  }

  @Override
  public Principal getPrincipal() {
    // When requests come in via rest endpoints, the userId is updated inside SecurityRequestContext, so give that
    // precedence.
    String userId = SecurityRequestContext.getUserId();
    String userCredential = SecurityRequestContext.getUserCredential();
    // This userId can be null, when the master itself is asynchoronously updating the policy cache, since
    // during that process the router will not set the SecurityRequestContext.
    //
    // The userId can no longer be set to null by an external request if internal communication authentication is
    // enforced.
    if (userId == null) {
      if (enforceInternalAuth) {
        return internalAuthDelegateContext.getPrincipal();
      } else {
        // If internal authenticated communication enforcement is disabled, obtain the userId from
        // the UserGroupInformation, which will be the user that the master is running as.
        try {
          userId = UserGroupInformation.getCurrentUser().getShortUserName();
        } catch (IOException e) {
          throw Throwables.propagate(e);
        }
      }
    }
    return new Principal(userId, Principal.PrincipalType.USER, userCredential);
  }
}
