package com.mz.jarboot.core.ws;

import com.mz.jarboot.core.constant.CoreConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.net.URI;

/**
 * WebSocket 客户端实现
 * @author majianzheng
 */
public final class WebSocketClient {
    private static final Logger logger = LoggerFactory.getLogger(CoreConstant.LOG_NAME);
    private static final String WS_HEADER = "ws";
    private static final String WSS_HEADER = "wss";
    private URI uri;
    private EventLoopGroup group;
    private Channel channel;
    private WebSocketClientHandler handler;
    public WebSocketClient(String url) {
        try {
            this.uri = new URI(url);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public void sendText(String text) {
        if (null != channel) {
            channel.writeAndFlush(new TextWebSocketFrame(text));
        } else {
            logger.info("发送消息失败！channel为null.");
        }
    }

    public boolean connect(MessageHandler messageHandler) {
        String scheme = uri.getScheme() == null ? WS_HEADER : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port = parsePort(scheme);

        if (!WS_HEADER.equalsIgnoreCase(scheme) && !WSS_HEADER.equalsIgnoreCase(scheme)) {
            logger.error("Only WS(S) is supported.");
            return false;
        }

        final boolean ssl = WSS_HEADER.equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            try {
                sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
            } catch (SSLException e) {
                logger.error(e.getMessage(), e);
                return false;
            }
        } else {
            sslCtx = null;
        }

        group = new NioEventLoopGroup();
        try {
            handler = new WebSocketClientHandler(WebSocketClientHandshakerFactory
                    .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders()));
            handler.setMessageHandler(messageHandler);
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    if (sslCtx != null) {
                        p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                    }
                    p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192), handler);
                }
            });

            channel = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (null == channel) {
            logger.error("channel is null");
        }
        return null != channel && channel.isOpen();
    }

    public boolean isOpen() {
        return null != channel && channel.isOpen();
    }

    private int parsePort(String scheme) {
        final int port;
        if (uri.getPort() == -1) {
            if (WS_HEADER.equalsIgnoreCase(scheme)) {
                port = 80;
            } else if (WSS_HEADER.equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }
        return port;
    }

    public void disconnect() {
        try {
            channel.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE));
        } catch (Exception e) {
            //ignore
        }
        try {
            channel.disconnect();
        } catch (Exception e) {
            //ignore
        }
        try {
            this.group.shutdownGracefully();
        } catch (Exception e) {
            //ignore
        }
    }
}
