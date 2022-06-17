package com.example.netty;

import com.example.netty.netty.NettyWebSocketServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class NettySocketApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext ac = SpringApplication.run(NettySocketApplication.class, args);
        //在SpringBoot启动类中加入以下内容
        try {
            //启动netty服务器
           ac.getBean(NettyWebSocketServer.class).start();
        } catch (Exception e) {
            System.out.println("NettyWebSocketServerError:" + e.getMessage());
        }
    }

}
