package com.example.springboot_websocket.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 15:56
 */
@Component
public class NettyWebSocketServer {

    @Autowired
    private NettyConfig nettyConfig;

    public void start() throws InterruptedException {

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            bootstrap.group(group,bossGroup) // 绑定线程池
                    .localAddress(new InetSocketAddress(nettyConfig.getIp(), nettyConfig.getPort()))// 绑定监听端口
                    .channel(NioServerSocketChannel.class) // 指定使用的channel
                    .childHandler(new ChannelInitializer() { // 绑定客户端连接时候触发操作
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    //websocket协议本身是基于http协议的，所以这边也要使用http解编码器
                                    .addLast(new HttpServerCodec())
                                    .addLast(new HttpObjectAggregator(65536))//聚合器，使用websocket会用到
                                    .addLast(new ChunkedWriteHandler())//用于大数据的分区传输
                                    .addLast(new WebSocketServerHandler()); //自定义消息处理类
//                                    .addLast(new WebSocketServerProtocolHandler(nettyConfig.getPath(), nettyConfig.getSubprotocols(), nettyConfig.getAllowExtensions(), nettyConfig.getMaxFrameSize()));


                        }
                    });
            Channel cf = bootstrap.bind().sync().channel();// 服务器异步创建绑定
            System.out.println(NettyWebSocketServer.class + "已启动，正在监听： " + cf.localAddress());
            cf.closeFuture().sync(); // 关闭服务器通道
        }finally {
            group.shutdownGracefully().sync(); // 释放线程池资源
            bossGroup.shutdownGracefully().sync();
        }
    }
}
