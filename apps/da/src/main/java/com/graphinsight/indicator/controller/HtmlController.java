package com.graphinsight.indicator.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HtmlController {

    @RequestMapping("ihtml")
    public String test() {
        return "onlinetest";
    }

    @RequestMapping("pivot")
    public String pivot() {
        return "pivot";
    }

}
