package com.schooldesk.docqa.conversation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "docqa.conversation")
public record ConversationProperties(

        @DefaultValue("6") @Min(1) @Max(50) int maxTurns,

        @DefaultValue("1500") @Min(100) @Max(20000) int tokenBudget) {
}
