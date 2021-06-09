/*
 * Copyright © 2014-2019 Cask Data, Inc.
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
package io.cdap.cdap.common.http;

import io.cdap.cdap.common.HttpExceptionHandler;
import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.http.ChannelPipelineModifier;
import io.cdap.http.NettyHttpService;
import io.cdap.http.internal.HttpDispatcher;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ImmediateEventExecutor;

import javax.annotation.Nullable;

/**
 * Provides a {@link io.cdap.http.NettyHttpService.Builder} that has common settings built-in.
 */
public class CommonNettyHttpServiceBuilder extends NettyHttpService.Builder {

  public static final String DISPATCHER_PIPELINE_STEP = "dispatcher";
  public static final String AUTHENTICATOR_PIPELINE_STEP = "authenticator";
  private ChannelPipelineModifier pipelineModifier;
  private ChannelPipelineModifier additionalModifier;

  public CommonNettyHttpServiceBuilder(CConfiguration cConf, String serviceName) {
    super(serviceName);

    if (cConf.getBoolean(Constants.Security.ENABLED)) {
      pipelineModifier = new ChannelPipelineModifier() {
        @Override
        public void modify(ChannelPipeline pipeline) {
          // Adds the AuthenticationChannelHandler before the dispatcher.
          // The dispatcher itself is readded with the immediate executor.
          // This is needed before we use a InheritableThreadLocal in SecurityRequestContext
          // to remember the user id.
          ChannelHandlerContext dispatcher = pipeline.context(DISPATCHER_PIPELINE_STEP);
          EventExecutor executor = dispatcher.executor();
          pipeline.addBefore(executor, DISPATCHER_PIPELINE_STEP, AUTHENTICATOR_PIPELINE_STEP,
                             new AuthenticationChannelHandler());
          ChannelHandler dispatcherHandler = dispatcher.handler();
          pipeline.remove(dispatcherHandler);
          pipeline.addAfter(ImmediateEventExecutor.INSTANCE, AUTHENTICATOR_PIPELINE_STEP, DISPATCHER_PIPELINE_STEP,
                            new HttpDispatcher());
        }
      };
    }
    this.setExceptionHandler(new HttpExceptionHandler());
  }

  @Override
  public NettyHttpService.Builder setChannelPipelineModifier(ChannelPipelineModifier channelPipelineModifier) {
    pipelineModifier = channelPipelineModifier;
    return this;
  }

  public NettyHttpService.Builder addChannelPipelineModifier(ChannelPipelineModifier additionalPipelineModifier) {
    additionalModifier = combine(additionalModifier, additionalPipelineModifier);
    return this;
  }

  @Override
  public NettyHttpService build() {
    ChannelPipelineModifier modifier = combine(pipelineModifier, additionalModifier);
    if (modifier != null) {
      super.setChannelPipelineModifier(modifier);
    }
    return super.build();
  }

  private ChannelPipelineModifier combine(@Nullable ChannelPipelineModifier existing,
                                          @Nullable ChannelPipelineModifier additional) {
    if (existing == null) {
      return additional;
    }
    if (additional == null) {
      return existing;
    }
    return new ChannelPipelineModifier() {
      @Override
      public void modify(ChannelPipeline pipeline) {
        existing.modify(pipeline);
        additional.modify(pipeline);
      }
    };
  }
}
