package com.example.springboot_websocket.netty;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 17:41
 */

@Component
@ConfigurationProperties("server.netty.websocket")
@Data
public class NettyConfig implements Serializable {

    private String ip;
    private int port;
    private String path;
    private int maxFrameSize;
    private String subprotocols;
    private Boolean allowExtensions;

}
