package com.kurly.common.exception.handler;

import com.kurly.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클라이언트 잘못이 500으로 새어 나가지 않는지 확인한다.
 *
 * <p>500은 서버 결함을 뜻한다. 여기 모인 상황들은 모두 요청 쪽 문제이므로 500으로 응답하면
 * 장애 지표가 오염되고 실제 장애가 가려진다.
 */
class GlobalExceptionHandlerUnitExceptionTest {

    MockMvc mockMvc;

    @RestController
    static class ProbeController {

        @GetMapping("/probe/{id}")
        String byId(@PathVariable Long id) {
            return "ok";
        }

        @PostMapping("/probe")
        String create(@RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/probe/missing")
        String missing() {
            throw new EntityNotFoundException("대상을 찾을 수 없습니다.");
        }

        record Payload(String name, int count) {
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("매핑되지 않은 경로")
    class NoHandlerTest {

        @Test
        void 존재하지_않는_경로는_404다() throws Exception {
            // 처리기가 없으면 catch-all로 떨어져 500이 된다. 오타나 스캐너 요청이
            // 장애 지표를 오염시키고 실제 장애를 가린다.
            mockMvc.perform(get("/does-not-exist"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        void 어떤_경로가_실재하는지는_드러내지_않는다() throws Exception {
            mockMvc.perform(get("/probe/deep/unknown/path"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
        }
    }

    @Nested
    @DisplayName("요청 형식 오류")
    class BadRequestTest {

        // 경로 변수 타입 불일치(MethodArgumentTypeMismatchException)는 여기서 검증하지 않는다.
        // standalone MockMvc는 ConversionService 없이 변환을 시도해 NumberFormatException을
        // 그대로 올려보내므로, 실제 앱과 예외 종류가 달라 의미 있는 검증이 되지 않는다.
        // 실제 매핑을 갖춘 user-service의 UserControllerUnitExceptionTest가 이 경로를 덮는다.

        @Test
        void 본문이_JSON이_아니면_400이다() throws Exception {
            mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_INPUT_VALUE"));
        }

        @Test
        void 지원하지_않는_메서드는_405다() throws Exception {
            mockMvc.perform(post("/probe/1"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"));
        }
    }

    @Nested
    @DisplayName("업무 예외")
    class BusinessTest {

        @Test
        void BusinessException은_에러코드의_상태로_응답한다() throws Exception {
            mockMvc.perform(get("/probe/missing"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("대상을 찾을 수 없습니다."));
        }
    }
}
