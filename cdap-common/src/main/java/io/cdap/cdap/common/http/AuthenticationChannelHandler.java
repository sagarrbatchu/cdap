/*
 * Copyright © 2014-2021 Cask Data, Inc.
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

import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.security.spi.authentication.SecurityRequestContext;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An UpstreamHandler that verifies the userId in a request header and updates the {@code SecurityRequestContext}.
 */
public class AuthenticationChannelHandler extends ChannelInboundHandlerAdapter {
  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationChannelHandler.class);

  private String currentUserId;
  private String currentUserCredential;
  private String currentUserIP;

  /**
   * Decode the AccessTokenIdentifier passed as a header and set it in a ThreadLocal.
   * Returns a 401 if the identifier is malformed.
   */
  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {
      // TODO: authenticate the user using user id - CDAP-688
      HttpRequest request = (HttpRequest) msg;
      currentUserId = request.headers().get(Constants.Security.Headers.USER_ID);
      currentUserIP = request.headers().get(Constants.Security.Headers.USER_IP);
      String authHeader = request.headers().get(HttpHeaderNames.AUTHORIZATION);
      LOG.trace("Got user ID '{}', user IP '{}', and authorization header length '{}'", currentUserId, currentUserIP,
                authHeader == null ? "NULL" : String.valueOf(authHeader.length()));
      if (authHeader != null) {
        int idx = authHeader.trim().indexOf(' ');
        if (idx < 0) {
          LOG.warn("Invalid Authorization header format for {}@{}", currentUserId, currentUserIP);
        } else {
          currentUserCredential = authHeader.substring(idx + 1).trim();
          SecurityRequestContext.setUserCredential(currentUserCredential);
        }
      }

      SecurityRequestContext.setUserId(currentUserId);
      SecurityRequestContext.setUserIP(currentUserIP);
    } else if (msg instanceof HttpContent) {
      SecurityRequestContext.setUserId(currentUserId);
      SecurityRequestContext.setUserCredential(currentUserCredential);
      SecurityRequestContext.setUserIP(currentUserIP);
    }

    ctx.fireChannelRead(msg);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    LOG.error("Got exception: {}", cause.getMessage(), cause);
    // TODO: add WWW-Authenticate header for 401 response -  REACTOR-900
    HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
    HttpUtil.setContentLength(response, 0);
    HttpUtil.setKeepAlive(response, false);
    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
  }
}
