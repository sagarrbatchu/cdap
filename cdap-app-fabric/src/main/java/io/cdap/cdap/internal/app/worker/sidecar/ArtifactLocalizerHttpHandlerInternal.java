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

package io.cdap.cdap.internal.app.worker.sidecar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.logging.gateway.handlers.AbstractLogHttpHandler;
import io.cdap.cdap.proto.BasicThrowable;
import io.cdap.cdap.proto.codec.BasicThrowableCodec;
import io.cdap.cdap.proto.id.ArtifactId;
import io.cdap.http.HttpHandler;
import io.cdap.http.HttpResponder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.twill.filesystem.Location;
import org.apache.twill.filesystem.LocationFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

/**
 * Internal {@link HttpHandler} for File Localizer.
 */
@Singleton
@Path(Constants.Gateway.INTERNAL_API_VERSION_3 + "/worker")
public class ArtifactLocalizerHttpHandlerInternal extends AbstractLogHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ArtifactLocalizerHttpHandlerInternal.class);
  private static final Gson GSON = new GsonBuilder()
    .registerTypeAdapter(BasicThrowable.class, new BasicThrowableCodec()).create();
  private final ArtifactLocalizer artifactLocalizer;
  private final LocationFactory locationFactory;

  public ArtifactLocalizerHttpHandlerInternal(CConfiguration cConf, ArtifactLocalizer artifactLocalizer,
                                              LocationFactory locationFactory) {
    super(cConf);
    this.artifactLocalizer = artifactLocalizer;
    this.locationFactory = locationFactory;
  }

  @GET
  @Path("/artifact/namespaces/{namespace-id}/artifacts/{artifact-name}/versions/{artifact-version}")
  public void artifact(HttpRequest request, HttpResponder responder,
                       @PathParam("namespace-id") String namespaceId,
                       @PathParam("artifact-name") String artifactName,
                       @PathParam("artifact-version") String artifactVersion,
                       @QueryParam("unpack") @DefaultValue("false") String unpack) throws Exception {
    Location artifact;
    if (Boolean.parseBoolean(unpack)) {
      artifact = artifactLocalizer.getAndUnpackArtifact(new ArtifactId(namespaceId, artifactName, artifactVersion));
    } else {
      artifact = artifactLocalizer.getArtifact(new ArtifactId(namespaceId, artifactName, artifactVersion));
    }
    responder.sendString(HttpResponseStatus.OK, artifact.toString());
  }

  /**
   * Return json representation of an exception. Used to propagate exception across network for better surfacing errors
   * and debuggability.
   */
  private String exceptionToJson(Exception ex) {
    BasicThrowable basicThrowable = new BasicThrowable(ex);
    return GSON.toJson(basicThrowable);
  }
}
