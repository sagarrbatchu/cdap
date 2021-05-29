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
package io.cdap.cdap.proto.id;

import io.cdap.cdap.proto.element.EntityType;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 * Uniquely identifies a draft.
 */
public class ApplicationDraftId extends NamespacedEntityId implements ParentedId<NamespaceId> {
  private final String id;
  private transient Integer hashCode;

  public ApplicationDraftId(String namespace, String id) {
    super(namespace, EntityType.APPLICATION_DRAFT);
    if (id == null) {
      throw new NullPointerException("ID cannot be null.");
    }
    ensureValidId("application draft", id);
    this.id = id;
  }

  public String getId() {
    return id;
  }

  @Override
  public String getEntityName() {
    return getId();
  }

  @Override
  public NamespaceId getParent() {
    return new NamespaceId(namespace);
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ApplicationDraftId that = (ApplicationDraftId) o;
    return Objects.equals(namespace, that.namespace) &&
      Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    Integer hashCode = this.hashCode;
    if (hashCode == null) {
      this.hashCode = hashCode = Objects.hash(super.hashCode(), namespace, id);
    }
    return hashCode;
  }

  @SuppressWarnings("unused")
  public static ApplicationDraftId fromIdParts(Iterable<String> idString) {
    Iterator<String> iterator = idString.iterator();
    return new ApplicationDraftId(next(iterator, "namespace"), nextAndEnd(iterator, "id"));
  }

  @Override
  public Iterable<String> toIdParts() {
    return Collections.unmodifiableList(Arrays.asList(namespace, id));
  }

  public static ApplicationDraftId fromString(String string) {
    return EntityId.fromString(string, ApplicationDraftId.class);
  }
}
