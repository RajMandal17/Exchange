package com.custom.openapi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {

    @GetMapping("/v2/barong/identity/configs")
    public Map<String, Object> getConfigs() {
        return null;
    }

}
