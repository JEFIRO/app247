package com.jefiro.app247.infra.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DocController {

    @GetMapping("/")
    public String get() {
        return "redirect:/swagger-ui/index.html";
    }
}