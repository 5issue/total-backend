package com.kurly.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 컨트롤러 파라미터에 검증된 {@link AuthenticatedPrincipal}을 주입받는다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthPrincipal {
}
