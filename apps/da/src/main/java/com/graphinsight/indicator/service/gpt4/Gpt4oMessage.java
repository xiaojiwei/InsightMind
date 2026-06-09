package com.graphinsight.indicator.service.gpt4;
import lombok.Data;

import java.util.List;

@Data
public class Gpt4oMessage {

    private String role;

    private List<Gpt4oContent> contents;
}
