package com.example.netty.netty;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 16:07
 */

@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker handshaker;
    private final AttributeKey<Integer> counterAttr = AttributeKey.valueOf("count_Attr");;
    private final Integer heard_count = 3;

//    private static final EventExecutorGroup group = new DefaultEventExecutorGroup(10);

//    private static final ExecutorService group = Executors.newFixedThreadPool(10);

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (!ctx.channel().isActive()) {
            ctx.channel().close();
            ChannelHandlerPool.removeChannel(ctx.channel().id().asLongText());
            return;
        }
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            switch (idleStateEvent.state()) {
                case READER_IDLE: {
                    log.info("进入读空闲...");
                    ctx.writeAndFlush(new TextWebSocketFrame("heard beat !")).addListener(future -> {
//                        if(future.isSuccess()) {
//                            ctx.channel().attr(counterAttr).set(0);
//                        }else {
                            Integer counter = ctx.channel().attr(counterAttr).get();
                            counter = ++counter;
                            log.info("线程id:"+Thread.currentThread().getId() +"id:" +ctx.channel().id().asShortText() + "，发送心跳: " + counter + "次");
                            if(counter >= heard_count) {
                                ctx.disconnect();
                                ChannelHandlerPool.removeChannel(ctx.channel().id().asLongText());
                            } else {
                                ctx.channel().attr(counterAttr).set(counter);
                            }
//                        }
                    });
                    break;
                }
                case WRITER_IDLE:
                    log.info("进入写空闲...");
                    break;
                case ALL_IDLE:
                    log.info("进入读写空闲..." + ctx.channel().id().asShortText());
                    break;
                default:
                    break;
            }
        }else {
            super.userEventTriggered(ctx,evt);
        }
    }

    /**
     * @Description: 建立连接
     * @param: [ctx]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-16 17:34
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("线程id:"+Thread.currentThread().getId() + "id: " + ctx.channel().id().asShortText() +"与客户端建立连接，通道开启！");
        ctx.channel().attr(counterAttr).set(0);
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
        log.info("线程id:"+Thread.currentThread().getId() + "id:" + ctx.channel().id().asShortText() + "与客户端断开连接，通道关闭！");
        //从channelGroup通道组删除
        ChannelHandlerPool.removeChannel(ctx.channel().id().asLongText());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {


                //如果有消息过来，将当前Channel的心跳记录置为0
                if (ctx.channel().attr(counterAttr).get() != 0) {
                    ctx.channel().attr(counterAttr).set(0);
                }
                if (msg instanceof FullHttpRequest){
                    //以http请求形式接入，但是走的是websocket
                    handleHttpRequest(ctx, (FullHttpRequest) msg);
                }else if (msg instanceof  WebSocketFrame){
//                    group.submit(new Callable<Object>() {
//                        @Override
//                        public Object call() throws Exception {
                    //处理websocket客户端的消息
                    handleWebSocketRequest(ctx, (WebSocketFrame) msg);
//                            return null;
//                        }
//                    });
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
//            sendAllMessage(receiverMsg);
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
            log.info("线程id:"+Thread.currentThread().getId() + "服务端收到：" + params);
            JSONObject jsonParam = JSON.parseObject(params);
            String content = (String)jsonParam.get("content");

            //处理消息
            //群发
            if ("all".equals(jsonParam.get("toUserId"))) {
                //群发消息
                sendAllMessage(content);
            }else {
                //单发消息
                List<Channel> list = new ArrayList<>();
                list.add(ctx.channel());
//                list.add("1");
//                list.add("2");
//                List<Channel> channelList = ChannelHandlerPool.findChannelByClentIds(list);
                sendMessage(list,content);
            }
            return;
        }

        if (frame instanceof BinaryWebSocketFrame) {
            BinaryWebSocketFrame binaryFrame = (BinaryWebSocketFrame) frame;
            String result = binaryFrame.content().toString(CharsetUtil.UTF_8);
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
            System.out.println("线程id:"+Thread.currentThread().getId() + "给id:" + channelList.get(0).id().asShortText() + "发送消息:");
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
