package com.example.netty.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 16:07
 */

@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;

    /**
     * @Description: 建立连接
     * @param: [ctx]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-16 17:34
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        log.info("与客户端建立连接，通道开启！");
        //添加到channelGroup通道组
//        MyChannelHandlerPool.addChannel(ctx.channel());
    }

    /**
     * @Description: 断开连接
     * @param: [ctx]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-16 17:34
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("与客户端断开连接，通道关闭！");
        //从channelGroup通道组删除
//        MyChannelHandlerPool.removeChannel(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest){
            //以http请求形式接入，但是走的是websocket
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        }else if (msg instanceof  WebSocketFrame){
            //处理websocket客户端的消息
            handleWebSocketRequest(ctx, (WebSocketFrame) msg);
        }else {
            //不接受文本以外的数据帧类型
            ctx.channel().writeAndFlush(WebSocketCloseStatus.INVALID_MESSAGE_TYPE).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        //要求Upgrade为websocket，过滤掉get/Post
        if (!req.decoderResult().isSuccess()
                || (!"websocket".equals(req.headers().get("Upgrade")))) {
            //若不是websocket方式，则创建BAD_REQUEST的req，返回给客户端
            sendResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        //获取请求参数
        QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
        Map<String, List<String>> map = decoder.parameters();
        if (map.isEmpty() || map.get("clentId") == null) {
            sendResponse(ctx, req, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        //判断是否以/ws/开头,获取path上的参数
//        if (decoder.path().contains("/ws/") && decoder.path().startsWith("/ws/")) {
//            System.out.println(decoder.path().substring(4));
//        }

        //将通道与userId关联起来
        String clentId = map.get("clentId").get(0);
        ChannelHandlerPool.addChannel(ctx.channel(), clentId);

        //参数分别是ws地址，子协议，是否扩展，最大frame长度
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true, 5 * 1024 * 1024);
        handshaker = factory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
            String receiverMsg = "客户端Id：" + clentId + "上线了！";
            log.info(receiverMsg);
            //单发消息
            sendMessage(ChannelHandlerPool.findChannelByClentId(clentId),receiverMsg);
            //群发消息
            sendAllMessage(receiverMsg);
        }
    }

    private void handleWebSocketRequest(ChannelHandlerContext ctx, WebSocketFrame frame) {

        //判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }

        //握手 PING/PONG
        if (frame instanceof PingWebSocketFrame) {
            ctx.write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        //文本接收和发送
        if (frame instanceof TextWebSocketFrame) {

            //收到的消息
            String params = ((TextWebSocketFrame) frame).text();
            log.info("服务端收到：" + params);
            JSONObject jsonParam = JSON.parseObject(params);
            String content = (String)jsonParam.get("content");


            //处理消息
            //群发
            if ("all".equals(jsonParam.get("toUserId"))) {
                //群发消息
                sendAllMessage(content);
            }else {
                //单发消息
                List<String> list = new ArrayList<>();
                list.add("1");
                list.add("2");
                List<Channel> channelList = ChannelHandlerPool.findChannelByClentIds(list);
                sendMessage(channelList,content);
            }
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            ctx.write(frame.retain());
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, FullHttpRequest req, FullHttpResponse resp) {
        HttpResponseStatus status = resp.status();
        if (status != HttpResponseStatus.OK) {
            ByteBufUtil.writeUtf8(resp.content(), status.toString());
            HttpUtil.setContentLength(req, resp.content().readableBytes());
        }
        boolean keepAlive = HttpUtil.isKeepAlive(req) && status == HttpResponseStatus.OK;
        HttpUtil.setKeepAlive(req, keepAlive);
        ChannelFuture future = ctx.write(resp);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * @Description: 单发消息
     * @param: [ctx, message]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-16 17:31
     */
    private void sendMessage(List<Channel> channelList, String message){

        //单发
        if (channelList.size() == 1) {
            channelList.get(0).writeAndFlush(new TextWebSocketFrame(message));
        }else {
            //群发
            ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            channelGroup.addAll(channelList);
            channelGroup.writeAndFlush(new TextWebSocketFrame(message));
        }
    }

    /**
     * @Description: 群发消息
     * @param: [message]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-16 17:31
     */
    private void sendAllMessage(String message){
        ChannelHandlerPool.sendAll(new TextWebSocketFrame(message));
    }

    //SSL支持采用wss://
    private String getWebSocketLocation(FullHttpRequest request) {
        String location = request.headers().get(HttpHeaderNames.HOST) + "/ws";
        return "ws://" + location;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
