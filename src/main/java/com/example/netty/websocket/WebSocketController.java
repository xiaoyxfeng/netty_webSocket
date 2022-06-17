package com.example.netty.websocket;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @Description:
 * @author: shijinxing
 * @date: 2022-06-16 15:23
 */

@Controller
public class WebSocketController {

    @GetMapping("/webSocket")
    public String getWebSocket() {
        return  "webSocket";
    }
}
