package com.example.springboot_websocket.websocket;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
