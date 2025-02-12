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

package io.cdap.cdap.internal.app.worker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;
import io.cdap.cdap.api.service.worker.RunnableTaskContext;
import io.cdap.cdap.api.service.worker.RunnableTaskRequest;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.http.AbstractBodyConsumer;
import io.cdap.cdap.proto.BasicThrowable;
import io.cdap.cdap.proto.codec.BasicThrowableCodec;
import io.cdap.common.http.HttpRequest;
import io.cdap.common.http.HttpRequests;
import io.cdap.common.http.HttpResponse;
import io.cdap.http.AbstractHttpHandler;
import io.cdap.http.BodyConsumer;
import io.cdap.http.BodyProducer;
import io.cdap.http.HttpHandler;
import io.cdap.http.HttpResponder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

/**
 * Internal {@link HttpHandler} for Task worker.
 */
@Singleton
@Path(Constants.Gateway.INTERNAL_API_VERSION_3 + "/worker")
public class TaskWorkerHttpHandlerInternal extends AbstractHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(TaskWorkerHttpHandlerInternal.class);
  private static final Gson GSON = new GsonBuilder().registerTypeAdapter(BasicThrowable.class,
                                                                         new BasicThrowableCodec()).create();
  /**
   * Prefix of temporary file.
   */
  private static final String PREFIX = "task_worker";
  /**
   * Extension of temporary file.
   */
  private static final String EXT = ".tmp";

  private final RunnableTaskLauncher runnableTaskLauncher;
  private final Consumer<String> stopper;
  private final AtomicInteger inflightRequests = new AtomicInteger(0);
  private final String metadataServiceEndpoint;

  public TaskWorkerHttpHandlerInternal(CConfiguration cConf, Consumer<String> stopper) {
    this.runnableTaskLauncher = new RunnableTaskLauncher(cConf);
    this.metadataServiceEndpoint = cConf.get(Constants.TaskWorker.METADATA_SERVICE_END_POINT);
    this.stopper = s -> {
      stopper.accept(s);
      inflightRequests.decrementAndGet();
    };
  }

  @POST
  @Path("/run")
  public void run(FullHttpRequest request, HttpResponder responder) {
    if (inflightRequests.incrementAndGet() > 1) {
      responder.sendStatus(HttpResponseStatus.TOO_MANY_REQUESTS);
      return;
    }

    String className = null;
    try {
      RunnableTaskRequest runnableTaskRequest =
        GSON.fromJson(request.content().toString(StandardCharsets.UTF_8), RunnableTaskRequest.class);
      className = runnableTaskRequest.getClassName();
      byte[] response = runnableTaskLauncher.launchRunnableTask(runnableTaskRequest, null);

      responder.sendContent(HttpResponseStatus.OK,
                            new RunnableTaskBodyProducer(response, stopper, className),
                            new DefaultHttpHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM));
    } catch (ClassNotFoundException | ClassCastException ex) {
      responder.sendString(HttpResponseStatus.BAD_REQUEST, exceptionToJson(ex), EmptyHttpHeaders.INSTANCE);
    } catch (Exception ex) {
      LOG.error("Failed to run task {}", request.content().toString(StandardCharsets.UTF_8), ex);
      responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, exceptionToJson(ex), EmptyHttpHeaders.INSTANCE);
      if (className != null) {
        stopper.accept(className);
      }
    }
  }

  @GET
  @Path("/token")
  public void token(io.netty.handler.codec.http.HttpRequest request, HttpResponder responder) {
    if (metadataServiceEndpoint == null) {
      responder.sendString(HttpResponseStatus.NOT_IMPLEMENTED,
                           String.format("%s has not been set", Constants.TaskWorker.METADATA_SERVICE_END_POINT));
      return;
    }

    try {
      URL url = new URL(metadataServiceEndpoint);
      HttpRequest tokenRequest = HttpRequest.get(url).addHeader("Metadata-Flavor", "Google").build();
      HttpResponse tokenResponse = HttpRequests.execute(tokenRequest);
      responder.sendByteArray(HttpResponseStatus.OK, tokenResponse.getResponseBody(), EmptyHttpHeaders.INSTANCE);
    } catch (Exception ex) {
      LOG.warn("failed to fetch token from metadata service", ex);
      responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, exceptionToJson(ex), EmptyHttpHeaders.INSTANCE);
    }
  }

  /**
   * Run a task specified by query parameters {@code className} and {@code param}.
   * Request body contains the content of a file uploaded by the caller that is available to the task
   * at runtime via {@link RunnableTaskContext}.
   *
   * @param request   {@link HttpRequest} whose body should be a file content.
   * @param responder {@link HttpResponder}
   * @param className classname of the task to run
   * @param param     parameter(s) to the task
   * @return {@link BodyConsumer}
   */
  @POST
  @Path("/task")
  public BodyConsumer runTask(io.netty.handler.codec.http.HttpRequest request, HttpResponder responder,
                              @QueryParam("className") String className,
                              @QueryParam("param") String param) {
    if (inflightRequests.incrementAndGet() > 1) {
      responder.sendStatus(HttpResponseStatus.TOO_MANY_REQUESTS);
      return null;
    }

    try {
      // Download file content to local tmp dir.
      File tmpFile = File.createTempFile(PREFIX, EXT);

      return new AbstractBodyConsumer(tmpFile) {
        @Override
        protected void onFinish(HttpResponder responder, File uploadedFile) {
          try {
            RunnableTaskRequest runnableTaskRequest = RunnableTaskRequest.getBuilder(className).withParam(param)
              .build();
            byte[] response = runnableTaskLauncher.launchRunnableTask(runnableTaskRequest, tmpFile.toURI());
            responder.sendByteArray(HttpResponseStatus.OK,
                                    response,
                                    EmptyHttpHeaders.INSTANCE);
          } catch (ClassNotFoundException | ClassCastException ex) {
            responder.sendString(HttpResponseStatus.BAD_REQUEST,
                                 exceptionToJson(ex),
                                 EmptyHttpHeaders.INSTANCE);
          } catch (Exception ex) {
            LOG.error(String.format("Failed to run task %s", ex));
            responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, exceptionToJson(ex),
                                 EmptyHttpHeaders.INSTANCE);
          } finally {
            tmpFile.delete();
            stopper.accept(className);
          }
        }

        @Override
        protected void onError(Throwable cause) {
          try {
            tmpFile.delete();
            LOG.info("Failed to download file to run task {}", className);
            responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                 exceptionToJson(new Exception(cause)),
                                 EmptyHttpHeaders.INSTANCE);
          } finally {
            stopper.accept(className);
          }
        }
      };
    } catch (IOException e) {
      LOG.error("Failed to download file to run task {}", className);
      responder.sendString(HttpResponseStatus.INTERNAL_SERVER_ERROR, exceptionToJson(e), EmptyHttpHeaders.INSTANCE);
      stopper.accept(className);
    }
    return null;
  }

  /**
   * Return json representation of an exception.
   * Used to propagate exception across network for better surfacing errors and debuggability.
   */
  private String exceptionToJson(Exception ex) {
    BasicThrowable basicThrowable = new BasicThrowable(ex);
    return GSON.toJson(basicThrowable);
  }

  /**
   * By using BodyProducer instead of simply sending out response bytes,
   * the handler can get notified (through finished method) when sending the response is done,
   * so it can safely call the stopper to kill the worker pod.
   */
  private static class RunnableTaskBodyProducer extends BodyProducer {
    private final byte[] response;
    private final Consumer<String> stopper;
    private final String className;
    private boolean done = false;

    RunnableTaskBodyProducer(byte[] response, Consumer<String> stopper, String className) {
      this.response = response;
      this.stopper = stopper;
      this.className = className;
    }

    @Override
    public ByteBuf nextChunk() {
      if (done) {
        return Unpooled.EMPTY_BUFFER;
      }

      done = true;
      return Unpooled.wrappedBuffer(response);
    }

    @Override
    public void finished() {
      stopper.accept(className);
    }

    @Override
    public void handleError(@Nullable Throwable cause) {
      LOG.error("Error when sending chunks", cause);
      stopper.accept(className);
    }
  }
}
