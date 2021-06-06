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

package io.cdap.cdap.security.authorization;

import com.google.inject.Inject;
import io.cdap.cdap.api.security.AccessException;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.io.Codec;
import io.cdap.cdap.proto.element.EntityType;
import io.cdap.cdap.proto.id.EntityId;
import io.cdap.cdap.proto.security.Permission;
import io.cdap.cdap.proto.security.Principal;
import io.cdap.cdap.security.auth.AccessToken;
import io.cdap.cdap.security.auth.InvalidTokenException;
import io.cdap.cdap.security.auth.TokenManager;
import io.cdap.cdap.security.auth.UserIdentity;
import io.cdap.cdap.security.auth.context.SystemAuthenticationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

public class InternalAccessEnforcer extends AbstractAccessEnforcer {
  private static final Logger LOG = LoggerFactory.getLogger(InternalAccessEnforcer.class);

  private final TokenManager tokenManager;
  private final Codec<AccessToken> accessTokenCodec;

  @Inject
  InternalAccessEnforcer(CConfiguration cConf, TokenManager tokenManager, Codec<AccessToken> accessTokenCodec) {
    super(cConf);
    this.tokenManager = tokenManager;
    this.accessTokenCodec = accessTokenCodec;
  }

  @Override
  public void enforce(EntityId entity, Principal principal, Permission permission) throws AccessException {
    doEnforce(entity, principal, Collections.singleton(permission));
  }

  @Override
  public void enforce(EntityId entity, Principal principal, Set<? extends Permission> permissions)
    throws AccessException {
    doEnforce(entity, principal, permissions);
  }

  @Override
  public void enforceOnParent(EntityType entityType, EntityId parentId, Principal principal, Permission permission)
    throws AccessException {
    doEnforce(entityType, parentId, principal, permission);
  }

  @Override
  public Set<? extends EntityId> isVisible(Set<? extends EntityId> entityIds, Principal principal)
    throws AccessException {
    LOG.debug("Internal isVisible check for entities {} for principal {}", entityIds, principal);
    if (!principal.getType().equals(Principal.PrincipalType.INTERNAL)) {
      throw new IllegalStateException("Attempted to internally enforce access on non-internal principal type");
    }
    validateAccessTokenAndIdentity(principal.getCredential());
    return entityIds;
  }

  private void doEnforce(EntityId entity, Principal principal, Set<? extends Permission> permissions)
    throws AccessException {
    LOG.debug("Internal enforce check for entity {} for principal {} on permissions {}", entity, principal,
              permissions);
    if (!principal.getType().equals(Principal.PrincipalType.INTERNAL)) {
      throw new IllegalStateException("Attempted to internally enforce access on non-internal principal type");
    }
    validateAccessTokenAndIdentity(principal.getCredential());
  }

  private void doEnforce(EntityType entityType, EntityId parentId, Principal principal, Permission permission)
    throws AccessException {
    LOG.debug("Internal parent enforce check for entityType {} on parent entity {} for principal {} on permission {}",
              entityType, parentId, principal, permission);
    if (!principal.getType().equals(Principal.PrincipalType.INTERNAL)) {
      throw new IllegalStateException("Attempted to internally enforce access on non-internal principal type");
    }
    validateAccessTokenAndIdentity(principal.getCredential());
  }

  private void validateAccessTokenAndIdentity(String credential) throws AccessException {
    AccessToken accessToken;
    try {
      accessToken = accessTokenCodec
        .decode(Base64.getDecoder().decode(credential.getBytes()));
    } catch (IOException e) {
      LOG.debug("Access token deserialization failure: {}", e);
      throw new AccessException("Failed to deserialize access token", e);
    }
    try {
      tokenManager.validateSecret(accessToken);
    } catch (InvalidTokenException e) {
      LOG.debug("Access token secret validation failure: {}", e);
      throw new AccessException("Failed to validate access token", e);
    }
    UserIdentity userIdentity = accessToken.getIdentifier();
    if (!userIdentity.getUsername().equals(SystemAuthenticationContext.SYSTEM_IDENTITY)) {
      LOG.debug("Unexpected internal access token username: {}", userIdentity.getUsername());
      throw new AccessException("Unexpected username for internal access token");
    }
  }
}
