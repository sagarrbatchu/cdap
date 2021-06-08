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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.common.conf.SConfiguration;
import io.cdap.cdap.common.discovery.ResolvingDiscoverable;
import io.cdap.cdap.common.discovery.URIScheme;
import io.cdap.cdap.common.http.CommonNettyHttpServiceBuilder;
import io.cdap.http.NettyHttpService;
import org.apache.twill.common.Cancellable;
import org.apache.twill.discovery.DiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Launches an HTTP server for receiving and unpacking and caching artifacts.
 */
public class ArtifactLocalizerService extends AbstractIdleService {

  private static final Logger LOG = LoggerFactory.getLogger(ArtifactLocalizerService.class);
  private static final String BIND_ADDRESS = "127.0.0.1";

  private final CConfiguration cConf;
  private final SConfiguration sConf;
  private final DiscoveryService discoveryService;
  private final NettyHttpService httpService;
  private Cancellable cancelDiscovery;
  private InetSocketAddress bindAddress;

  @Inject
  ArtifactLocalizerService(CConfiguration cConf,
                           SConfiguration sConf,
                           DiscoveryService discoveryService) {
    this.cConf = cConf;
    this.sConf = sConf;
    this.discoveryService = discoveryService;

    NettyHttpService.Builder builder = new CommonNettyHttpServiceBuilder(cConf, Constants.Service.TASK_WORKER)
      .setHost(BIND_ADDRESS)
      .setPort(cConf.getInt(Constants.ArtifactLocalizer.PORT))
      .setBossThreadPoolSize(cConf.getInt(Constants.ArtifactLocalizer.BOSS_THREADS))
      .setWorkerThreadPoolSize(cConf.getInt(Constants.ArtifactLocalizer.WORKER_THREADS))
      .setHttpHandlers(new ArtifactLocalizerHttpHandlerInternal(this.cConf));

    httpService = builder.build();
  }

  @Override
  protected void startUp() throws Exception {
    LOG.info("Starting ArtifactLocalizerService");
    httpService.start();
    bindAddress = httpService.getBindAddress();
    cancelDiscovery = discoveryService.register(
      ResolvingDiscoverable.of(URIScheme.createDiscoverable(Constants.Service.TASK_WORKER, httpService)));
    LOG.info("Starting ArtifactLocalizerService has completed");
  }

  @Override
  protected void shutDown() throws Exception {
    LOG.info("Shutting down ArtifactLocalizerService");
    if (cancelDiscovery != null) {
      cancelDiscovery.cancel();
    }
    httpService.stop(5, 5, TimeUnit.SECONDS);
    LOG.debug("Shutting down ArtifactLocalizerService has completed");
  }

  @VisibleForTesting
  public InetSocketAddress getBindAddress() {
    return bindAddress;
  }
}
