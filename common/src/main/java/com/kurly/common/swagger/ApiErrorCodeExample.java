package com.kurly.common.swagger;

import com.kurly.common.exception.ErrorCode;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodeExamples.class)
public @interface ApiErrorCodeExample {
    Class<? extends ErrorCode> status();

    String code();

    String message() default "";
}