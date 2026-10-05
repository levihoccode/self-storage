package vn.lemar.selfstorage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.lemar.selfstorage.identity.repository.AccountRepository;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApiExceptionHandlerTest.ProbeController.class)
@Import(ApiExceptionHandlerTest.ProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountRepository accountRepository;

    @Test
    void unexpectedExceptionReturnsGenericInternalError() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(content().string(not(containsString("chi tiết nội bộ"))));
    }

    @Test
    void frameworkErrorKeepsStatusAndReturnsMessage() throws Exception {
        mockMvc.perform(post("/probe/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("Phương thức không được hỗ trợ"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void badRequestErrorResponseKeepsInvalidDataMessage() throws Exception {
        mockMvc.perform(get("/probe/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu không hợp lệ"));
    }

    @Test
    void notFoundErrorResponseKeepsNotFoundMessage() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tài nguyên"));
    }

    @Test
    void unsupportedMediaTypeErrorResponseKeepsMessage() throws Exception {
        mockMvc.perform(get("/probe/unsupported"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("Định dạng nội dung không được hỗ trợ"));
    }

    @Test
    void otherErrorResponseFallsBackToGenericClientMessage() throws Exception {
        mockMvc.perform(get("/probe/teapot"))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.message").value("Yêu cầu không hợp lệ"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("chi tiết nội bộ không được lộ");
        }

        @GetMapping("/probe/bad-request")
        String badRequest() {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        @GetMapping("/probe/not-found")
        String notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/probe/unsupported")
        String unsupported() {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }

        @GetMapping("/probe/teapot")
        String teapot() {
            throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT);
        }
    }
}
