package com.graphinsight.indicator.service.gpt4;
import lombok.Data;

import java.util.List;

@Data
public class Gpt4oRequest {

    private List<Gpt4oMessage> messages;

}
