package com.example.netty.netty;

import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 16:26
 */
public class ChannelHandlerPool {

    //channelGroup通道组
    public static ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    //可以存储clentId与ChannelId的映射表
    public static ConcurrentHashMap<String, ConcurrentHashMap<ChannelId,Integer>> channelMap = new ConcurrentHashMap<>();

    /**
     * @Description: 添加clentId 与 ChannelId关联
     * @param: [channel, clentId]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-17 13:44
     */
    public static void addChannel(Channel channel, String clentId){
        channelGroup.add(channel);
        ConcurrentHashMap<ChannelId,Integer> map = channelMap.get(clentId);
        if (map == null) {
            map = new ConcurrentHashMap<>();
        }
        map.put(channel.id(),0);
        channelMap.put(clentId,map);
    }

    /**
     * @Description: 移除该用户下所有的Channel
     * @param: [clentId]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-17 16:57
     */
    public static void removeChannelByClentId(String clentId) {

        List<ChannelId> chnnelIds = channelMap.get(clentId)
                .entrySet()
                .stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        channelGroup.stream().forEach(entry -> {
            if (chnnelIds.contains(entry.id())){
                channelGroup.remove(entry);
            }
        });
        channelMap.remove(clentId);
    }

    /**
     * @Description: 解除 clentId 与 ChannelId关联
     * @param: [channel]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-17 13:42
     */
    public static void removeChannel(String channelId){
        channelGroup.remove(findChannel(channelId));
        channelMap.entrySet().stream().forEach((entry) -> {
            ConcurrentHashMap<ChannelId,Integer> channelIdMap = entry.getValue();
            if (channelIdMap.get(channelId) != null){
                channelIdMap.remove(channelId);
                if (channelIdMap.size() == 0) {
                    channelMap.remove(entry.getKey());
                }
            }
        });
    }

    /**
     * @Description: 通过channelId找到参应的Channel
     * @param: [id]
     * @return: io.netty.channel.Channel
     * @author: shijinxing
     * @date: 2022-06-17 13:41
     */
    public static Channel findChannel(String channelId){
        return channelGroup
                .stream()
                .filter(channel -> channelId.equals(channel.id().asLongText()))
                .findFirst()
                .orElse(null);
    }

    /**
     * @Description: 通过clentId 查找所有的Channel
     * @param: [clentId]
     * @return: java.util.List<io.netty.channel.Channel>
     * @author: shijinxing
     * @date: 2022-06-17 18:05
     */
    public static List<Channel> findChannelByClentId(String clentId) {

        List<ChannelId> channelIds = channelMap.get(clentId).entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList());
        return channelGroup.stream().filter(channel -> channelIds.contains(channel.id())).collect(Collectors.toList());
    }


    /**
     * @Description: 查找所有用户的Channel
     * @param: [clentIds]
     * @return: java.util.List<io.netty.channel.Channel>
     * @author: shijinxing
     * @date: 2022-06-17 20:10
     */
    public static List<Channel> findChannelByClentIds(List<String> clentIds) {

        List<ChannelId> list = new ArrayList();
        channelMap.entrySet().stream().filter(entry -> clentIds.contains(entry.getKey())).forEach(entry -> {
            ConcurrentHashMap<ChannelId,Integer> map = entry.getValue();
            map.entrySet().forEach(entryMap -> {
                list.add(entryMap.getKey());
            });
        });
        return channelGroup.stream().filter(entry -> list.contains(entry.id())).collect(Collectors.toList());
    }

    /**
     * @Description: 给所有Channel发送消息(群发)
     * @param: [tws]
     * @return: void
     * @author: shijinxing
     * @date: 2022-06-17 13:41
     */
    public static void sendAll(TextWebSocketFrame tws){

        channelGroup.writeAndFlush(tws);
    }

    public static void main(String[] args) {
        HashMap<String,Map<String,Integer>> map = new HashMap<>();
        Map<String,Integer> hashMap = new HashMap<>();
        hashMap.put("a",0);
        hashMap.put("b",0);
        map.put("age",hashMap);
        Map<String,Integer> hashMap1 = new HashMap<>();
        hashMap1.put("c",0);
        hashMap1.put("d",0);
        map.put("name",hashMap1);

        List<String> clentIds = new ArrayList<>();
        clentIds.add("age");
        clentIds.add("name");

        List<String> list = new ArrayList();
        map.entrySet().stream().filter(entry -> clentIds.contains(entry.getKey())).forEach(entry -> {

            Map<String,Integer> params = entry.getValue();
            params.entrySet().forEach(entryMap -> {
                list.add(entryMap.getKey());
            });
        });
        System.out.println(list);
    }

}
